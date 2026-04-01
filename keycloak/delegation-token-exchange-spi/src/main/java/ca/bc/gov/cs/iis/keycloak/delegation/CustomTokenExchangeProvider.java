package ca.bc.gov.cs.iis.keycloak.delegation;

import ca.bc.gov.cs.iis.keycloak.auth.AuthTokenHelper;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolver;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolverImpl;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.OAuthErrorException;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.protocol.oidc.tokenexchange.AbstractTokenExchangeProvider;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ca.bc.gov.cs.iis.keycloak.delegation.client.DelegationServiceClient;

public final class CustomTokenExchangeProvider extends AbstractTokenExchangeProvider {

    public static final String PROVIDER_ID = "custom-token-exchange";
    public static final String PARAM_DELEGATION_CONTRACT_ID = "delegation_contract_id";
    public static final String PARAM_SUBJECT_TOKEN = "subject_token";

    /**
     * Use the standard parameter names so Keycloak internals and protocol mappers
     * can behave more like the built-in token exchange flow.
     */
    private static final String PARAM_AUDIENCE = "audience";
    private static final String PARAM_SCOPE = "scope";

    private static final String REQUESTED_TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    private static final String REQUESTED_TOKEN_TYPE_REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";
    private static final String RESPONSE_TOKEN_TYPE_BEARER = "Bearer";
    private static final String AUTH_METHOD = "custom-token-exchange";
    private static final String REALM_ATTR_DELEGATION_SERVICE_URL = "delegation_service_base_url"; // set with Terraform
                                                                                                   // realm.tf
                                                                                                   // attributes
    private static final String REALM_ATTR_DELEGATION_SERVICE_AUD_STRING = "delegation_service_audience"; // set with
                                                                                                          // Terraform
                                                                                                          // realm.tf
                                                                                                          // attributes

    /**
     * User session note keys for delegation context.
     * These are written at token-exchange time and survive across the full session
     * lifecycle — including refresh token grants — because UserSessionModel notes
     * are persisted with the session, not with a single request.
     *
     * The protocol mapper (DelegationActMapper) reads these notes on every token
     * issuance so the act claim is present on both the initial access token and
     * any tokens minted from a subsequent refresh_token grant.
     */
    public static final String SESSION_NOTE_ACT_JSON = "delegation.act";
    public static final String SESSION_NOTE_ACT_SUB = "delegation.act.sub";
    public static final String SESSION_NOTE_ACT_CLIENT_ID = "delegation.act.client_id";
    public static final String SESSION_NOTE_CONTRACT_ID = "delegation.contract.id";

    private static final Logger logger = Logger.getLogger(CustomTokenExchangeProvider.class);

    KeycloakSession session;
    private String actClaimJson;
    static String delegationServiceBaseUrl = null;
    static String delegationServiceAudience = null;

    public CustomTokenExchangeProvider(KeycloakSession session) {
        this.session = session;
        delegationServiceBaseUrl = session.getContext().getRealm().getAttribute(REALM_ATTR_DELEGATION_SERVICE_URL);
        delegationServiceAudience = session.getContext().getRealm()
                .getAttribute(REALM_ATTR_DELEGATION_SERVICE_AUD_STRING);
    }

    public CustomTokenExchangeProvider() {
        // Default constructor for SPI instantiation; session will be set in factory.
    }

    @Override
    public boolean supports(org.keycloak.protocol.oidc.TokenExchangeContext context) {
        return context.getFormParams() != null
                && context.getFormParams().getFirst(PARAM_DELEGATION_CONTRACT_ID) != null;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    protected Response tokenExchange() {

        logger.infof("Custom token exchange for delegation");
        this.actClaimJson = null;

        final String actorTokenEncoded = formParams.getFirst(PARAM_SUBJECT_TOKEN);
        final String delegationContractId = formParams.getFirst(PARAM_DELEGATION_CONTRACT_ID);

        if (actorTokenEncoded == null || actorTokenEncoded.isBlank()) {
            logger.warn("Missing subject_token");
            throw new BadRequestException("Missing subject_token");
        }
        if (delegationContractId == null || delegationContractId.isBlank()) {
            logger.warn("Missing delegation_contract_id");
            throw new BadRequestException("Missing delegation_contract_id");
        }

        final AccessToken actorToken;
        try {
            actorToken = AuthTokenHelper.verifyAccessToken(session, actorTokenEncoded);
        } catch (OAuthErrorException e) {
            logger.warn("Failed to verify subject_token", e);
            throw new NotAuthorizedException("subject_token invalid", e);
        }

        final DelegationContract delegation;
        try {
            delegation = this.getDelegationContract(delegationContractId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid delegation_contract"));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delegation_contract", e);
            throw new BadRequestException("Invalid delegation_contract", e);
        }

        if (delegation.getExpiresAt() != 0
                && delegation.getExpiresAt() < (System.currentTimeMillis() / 1000L)) {
            logger.warn("delegation_contract expired");
            throw new BadRequestException("delegation_contract expired");
        }

        if (delegation.getDelegate() == null) {
            logger.warn("Missing delegate entry in delegation_contract");
            throw new BadRequestException("Missing delegate in delegation_contract");
        }

        boolean delegateIsUser = delegation.getDelegate() != null && delegation.getDelegate().getUserId() != null
                && !delegation.getDelegate().getUserId().isBlank();
        boolean delegateIsServiceAccount = delegation.getDelegate() != null
                && delegation.getDelegate().getServiceAccountClientId() != null
                && !delegation.getDelegate().getServiceAccountClientId().isBlank();

        if (!delegateIsUser && !delegateIsServiceAccount) {
            logger.warn(
                    "Delegate entry in delegation_contract must specify either user_id or service_account_client_id");
            throw new BadRequestException("Invalid delegate in delegation_contract");
        }
        if (delegateIsUser && delegateIsServiceAccount) {
            logger.warn(
                    "Delegate entry in delegation_contract cannot specify both user_id and service_account_client_id");
            throw new BadRequestException("Invalid delegate in delegation_contract");
        }

        if (delegation.getDelegator() == null
                || delegation.getDelegator().getUserId() == null
                || delegation.getDelegator().getUserId().isBlank()) {
            logger.warn("Missing delegator.userId in delegation_contract");
            throw new BadRequestException("Missing delegator.userId in delegation_contract");
        }

        // Resolve delegate user or service account based on delegation contract
        // configuration
        UserModel delegateUser = null;
        if (delegateIsServiceAccount) {
            ClientModel client = session.clients().getClientByClientId(realm,
                    delegation.getDelegate().getServiceAccountClientId());
            if (client != null && client.isServiceAccountsEnabled()) {
                delegateUser = session.users().getServiceAccount(client);
            }
        } else if (delegateIsUser) {

            delegateUser = session.users().getUserById(realm, delegation.getDelegate().getUserId());
        }

        if (delegateUser == null) {
            logger.warn("Could not resolve delegate user in delegation_contract");
            throw new BadRequestException("Could not resolve delegate user in delegation_contract");
        }

        final UserModel delegatorUser = session.users().getUserById(realm, delegation.getDelegator().getUserId());
        if (delegatorUser == null) {
            logger.warn("Could not resolve delegator user in delegation_contract");
            throw new BadRequestException("Could not resolve delegator user in delegation_contract");
        }

        // subject_token represents the delegate in this model
        final String actorSub = actorToken.getSubject();
        if (actorSub == null) {
            logger.warn("subject_token missing sub");
            throw new NotAuthorizedException("subject_token missing sub");
        }

        final ClientModel actorClient = session.clients().getClientByClientId(realm, actorToken.getIssuedFor());
        if (actorClient == null) {
            logger.warnf("Unable to resolve client for issuedFor: %s", actorToken.getIssuedFor());
            throw new NotAuthorizedException("Invalid subject_token client");
        }

        final UserResolver userResolver = new UserResolverImpl(session);
        final String expectedDelegateSub;
        try {
            expectedDelegateSub = userResolver.computePairwiseSub(realm, actorClient, delegateUser);
        } catch (RuntimeException e) {
            logger.warn("Failed to compute delegate pairwise subject for comparison", e);
            throw new BadRequestException("Failed to resolve delegate identity", e);
        }

        if (!actorSub.equals(expectedDelegateSub)) {
            logger.warnf("subject_token sub mismatch. expected=%s actual=%s", expectedDelegateSub, actorSub);
            throw new NotAuthorizedException("subject_token does not match delegate in delegation_contract");
        }

        final List<ClientModel> targetAudienceClients = getTargetAudienceClients();
        if (targetAudienceClients == null || targetAudienceClients.isEmpty()) {
            logger.warn("At least one audience is required");
            throw new BadRequestException("At least one audience is required");
        }

        final String requestedTokenType = getRequestedTokenType();
        final String scope = getRequestedScope(actorToken, targetAudienceClients);

        final ClientModel actAudienceClient = targetAudienceClients.get(0);

        final String delegateSubjectId;
        try {
            delegateSubjectId = userResolver.computePairwiseSub(realm, actAudienceClient, delegateUser);
        } catch (RuntimeException e) {
            logger.warn("Failed to compute delegate pairwise subject", e);
            throw new BadRequestException("Failed to compute delegate pairwise subject", e);
        }

        final ActClaim actClaim = new ActClaim(delegateSubjectId);
        actClaim.setClientId(actorToken.getIssuedFor());
        this.actClaimJson = ActClaim.toJsonString(actClaim);

        final String ipAddress = clientConnection != null ? clientConnection.getRemoteAddr() : null;
        final String loginUsername = delegatorUser.getUsername() != null
                ? delegatorUser.getUsername()
                : delegatorUser.getId();

        final UserSessionModel targetUserSession = session.sessions().createUserSession(
                null,
                realm,
                delegatorUser,
                loginUsername,
                ipAddress,
                AUTH_METHOD,
                false,
                null,
                null,
                UserSessionModel.SessionPersistenceState.PERSISTENT);

        // Write delegation context into user session notes immediately after session
        // creation. These notes are persisted with the UserSessionModel and survive
        // for the full lifetime of the session — including subsequent refresh_token
        // grants — where the original request parameters (delegation_contract_id,
        // subject_token, etc.) are no longer present.
        //
        // The DelegationActMapper reads these notes on every token issuance so the
        // act claim is consistently present on the initial access token AND on any
        // access token minted from a refresh. When Joe switches back to his own
        // session (a separate UserSessionModel with no delegation notes), the mapper
        // finds nothing and emits no act claim — exactly the desired behaviour.
        targetUserSession.setNote(SESSION_NOTE_ACT_JSON, this.actClaimJson);
        targetUserSession.setNote(SESSION_NOTE_ACT_SUB, delegateSubjectId);
        targetUserSession.setNote(SESSION_NOTE_ACT_CLIENT_ID, actorToken.getIssuedFor());
        targetUserSession.setNote(SESSION_NOTE_CONTRACT_ID, delegationContractId);
        logger.debugf("Persisted delegation context into user session notes: contractId=%s, act.sub=%s",
                delegationContractId, delegateSubjectId);

        return exchangeClientToOIDCClient(
                delegatorUser,
                targetUserSession,
                requestedTokenType,
                targetAudienceClients,
                scope,
                actorToken);
    }

    @Override
    protected Response exchangeClientToOIDCClient(
            UserModel targetUser,
            UserSessionModel targetUserSession,
            String requestedTokenType,
            List<ClientModel> targetAudienceClients,
            String scope,
            AccessToken subjectToken) {

        // `client` (from AbstractTokenExchangeProvider) is the client_id making
        // the exchange request. Named explicitly to avoid confusion with audience.
        final ClientModel requestingClient = client;

        // The first audience entry is the primary target client — named clearly
        // to distinguish it from the requesting client.
        final ClientModel targetClient = targetAudienceClients.get(0);

        logger.debugf("Token exchange: requestingClient=%s, targetClient(audience)=%s",
                requestingClient.getClientId(), targetClient.getClientId());

        final AuthenticationSessionManager asm = new AuthenticationSessionManager(session);
        final RootAuthenticationSessionModel rootAuthSession = asm.createAuthenticationSession(realm, false);

        final AuthenticationSessionModel authSession = createSessionModel(
                targetUserSession,
                rootAuthSession,
                targetUser,
                requestingClient,
                scope);

        /**
         * Push audience and scope into the auth/user session notes using the
         * standard parameter names so Keycloak's token generation path can
         * resolve audiences, scopes, and mapper filtering more like built-in
         * token exchange.
         */
        final String requestedAudienceValue = targetAudienceClients.stream()
                .map(ClientModel::getClientId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));

        if (!requestedAudienceValue.isBlank()) {
            authSession.setClientNote(PARAM_AUDIENCE, requestedAudienceValue);
            targetUserSession.setNote(PARAM_AUDIENCE, requestedAudienceValue);
        }

        if (scope != null && !scope.isBlank()) {
            authSession.setClientNote(PARAM_SCOPE, scope);
            targetUserSession.setNote(PARAM_SCOPE, scope);
        }

        // attachAuthenticationSession creates the AuthenticatedClientSessionModel
        // for requestingClient on targetUserSession. Notes MUST be set after this
        // call — the client session does not exist on targetUserSession before it.
        final ClientSessionContext clientSessionCtx = TokenManager.attachAuthenticationSession(
                session,
                targetUserSession,
                authSession);

        // Safe to write notes now — AuthenticatedClientSessionModel exists.
        // Also inject the delegation scope so DelegationActMapper fires without
        // requiring the caller to pass scope=delegation explicitly.
        if (this.actClaimJson != null) {
            persistDelegationNote(requestingClient, targetUserSession, this.actClaimJson);
            injectDelegationScope(requestingClient, targetUserSession);
        }

        final boolean includeRefreshToken = REQUESTED_TOKEN_TYPE_REFRESH_TOKEN.equals(requestedTokenType);

        logger.infof("Including refresh token: %b", includeRefreshToken);

        if (includeRefreshToken && !requestingClient.isStandardFlowEnabled()) {
            // Refresh tokens require standard flow; warn and fall back to access-token
            // only.
            logger.warnf(
                    "Client '%s' does not have standard flow enabled; refresh token cannot be issued. " +
                            "Falling back to access token only.",
                    requestingClient.getClientId());
        }

        final TokenManager.AccessTokenResponseBuilder builder = tokenManager
                .responseBuilder(realm, requestingClient, event, session, targetUserSession, clientSessionCtx)
                .generateAccessToken()
                .responseTokenType(RESPONSE_TOKEN_TYPE_BEARER);

        // When the caller requests a refresh token, and the requesting client supports
        // it, instruct the builder to include one alongside the access token.
        if (includeRefreshToken && requestingClient.isStandardFlowEnabled()) {
            logger.debugf("Generating refresh token for delegation exchange on client '%s'",
                    requestingClient.getClientId());
            builder.generateRefreshToken();
        }

        final AccessToken issuedAccessToken = builder.getAccessToken();

        // Set act claim directly on the token before signing.
        // This is the only reliable approach in KC26 — the scope parameter in
        // token exchange is ignored since KC24, so mapper-based approaches that
        // depend on scope resolution do not work. The session note persists the
        // value for refresh tokens (handled by DelegationAwareTokenManager).
        if (this.actClaimJson != null) {
            try {
                Map<String, Object> actMap = org.keycloak.util.JsonSerialization
                        .readValue(this.actClaimJson, new TypeReference<Map<String, Object>>() {
                        });
                issuedAccessToken.setOtherClaims("act", actMap);
                logger.debugf("Set act claim directly on issued access token");
            } catch (IOException e) {
                logger.warnf("Failed to parse actClaimJson for direct token injection: %s", e.getMessage());
            }
        }

        // Keep azp / issued-for aligned to the RP performing the exchange.
        issuedAccessToken.issuedFor(requestingClient.getClientId());

        // Force aud to contain only the explicitly requested audience values.
        final List<String> audienceValues = targetAudienceClients.stream()
                .filter(audienceClient -> audienceClient != null && audienceClient.getClientId() != null)
                .map(ClientModel::getClientId)
                .filter(clientId -> !clientId.isBlank())
                .distinct()
                .toList();

        if (audienceValues.isEmpty()) {
            throw new BadRequestException("At least one audience is required");
        }

        // overwrite any audience already injected by Keycloak/mappers
        issuedAccessToken.audience(audienceValues.get(0));
        for (int i = 1; i < audienceValues.size(); i++) {
            issuedAccessToken.addAudience(audienceValues.get(i));
        }

        final AccessTokenResponse tokenResponse = builder.build();
        return cors.add(Response.ok(tokenResponse));
    }

    @Override
    protected Response exchangeClientToSAML2Client(
            UserModel targetUser,
            UserSessionModel targetUserSession,
            String requestedTokenType,
            List<ClientModel> targetAudienceClients) {
        throw new BadRequestException("SAML token exchange is not supported by CustomTokenExchangeProvider");
    }

    @Override
    protected String getRequestedTokenType() {
        final String requested = formParams.getFirst("requested_token_type");
        return (requested == null || requested.isBlank())
                ? REQUESTED_TOKEN_TYPE_ACCESS_TOKEN
                : requested;
    }

    @Override
    protected String getRequestedScope(AccessToken token, List<ClientModel> targetAudienceClients) {
        final String requested = formParams.getFirst(PARAM_SCOPE);
        return (requested == null || requested.isBlank()) ? null : requested.trim();
    }

    @Override
    protected List<String> getSupportedOAuthResponseTokenTypes() {
        return List.of(RESPONSE_TOKEN_TYPE_BEARER);
    }

    @Override
    protected void validateAudience(
            AccessToken token,
            boolean disallowOnHolderOfTokenMismatch,
            List<ClientModel> targetAudienceClients) {
        // Intentionally relaxed for the custom delegation-contract model.
    }

    /**
     * Get the delegation contract from the Delegation Service. This method extracts
     * the
     * 
     * @param contractId The ID of the delegation contract to retrieve from the
     *                   Delegation Service.
     * @return An Optional containing the delegation contract JSON if retrieval was
     *         successful, or an empty Optional if not.
     */
    private Optional<DelegationContract> getDelegationContract(String contractId) {
        if (delegationServiceBaseUrl == null || delegationServiceBaseUrl.isEmpty()) {
            throw new IllegalStateException("Delegation Service Base URL is not configured");
        }
        if (contractId == null) {
            throw new IllegalArgumentException("Contract ID cannot be null");
        }

        DelegationServiceClient delegationServiceClient = new DelegationServiceClient(delegationServiceBaseUrl,
                delegationServiceAudience, session);
        final String response = delegationServiceClient.getDelegationContract(contractId);
        logger.debugf("Delegation Service Response: %s", response);

        try {
            DelegationContract contract = DelegationContract.fromJsonString(response);
            return Optional.of(contract);
        } catch (IllegalArgumentException e) {
            logger.errorf("Failed to parse delegation contract: %s", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Persists the act claim JSON onto the AuthenticatedClientSessionModel for
     * the requestingClient. Must be called AFTER
     * TokenManager.attachAuthenticationSession()
     * so the client session exists on the user session.
     *
     * Note: the note goes on the requestingClient's session (not the
     * audience/targetClient)
     * because that is the session that TokenManager.attachAuthenticationSession()
     * creates
     * and that clientSessionCtx wraps. The audience clients only appear in the
     * token's
     * aud claim — they do not have their own AuthenticatedClientSessionModel here.
     */
    private static void persistDelegationNote(
            ClientModel requestingClient,
            UserSessionModel userSession,
            String delegationActJsonString) {

        final AuthenticatedClientSessionModel clientSession = userSession
                .getAuthenticatedClientSessionByClient(requestingClient.getId());

        if (clientSession == null) {
            throw new IllegalStateException(
                    "AuthenticatedClientSessionModel not found for client '" +
                            requestingClient.getClientId() +
                            "' — call persistDelegationNote after attachAuthenticationSession");
        }

        clientSession.setNote("delegation.act", delegationActJsonString);
        logger.debugf("Persisted delegation.act note on client session for client '%s'",
                requestingClient.getClientId());
    }

    /**
     * Injects the "delegation" client scope onto the requestingClient's
     * AuthenticatedClientSessionModel so the DelegationActMapper fires
     * without the caller needing to pass scope=delegation in the request.
     *
     * Keycloak's DefaultClientSessionContext resolves the active scope list
     * by reading notes from the AuthenticatedClientSessionModel keyed by
     * scope ID. Writing the note here is the correct internal mechanism for
     * adding a scope programmatically after the session is created.
     *
     * Must be called AFTER TokenManager.attachAuthenticationSession().
     */
    private void injectDelegationScope(ClientModel requestingClient,
            UserSessionModel targetUserSession) {
        final ClientScopeModel delegationScope = KeycloakModelUtils.getClientScopeByName(realm, "delegation");

        if (delegationScope == null) {
            logger.warnf("'delegation' client scope not found in realm '%s' — " +
                    "DelegationActMapper will not fire. Create the optional scope in the admin console.",
                    realm.getName());
            return;
        }

        final AuthenticatedClientSessionModel clientSession = targetUserSession
                .getAuthenticatedClientSessionByClient(requestingClient.getId());

        if (clientSession == null) {
            logger.warnf("AuthenticatedClientSessionModel not found for client '%s' — " +
                    "cannot inject delegation scope.", requestingClient.getClientId());
            return;
        }

        // Key = scope ID, value = scope name. This is the contract
        // DefaultClientSessionContext uses when building the scope list.
        clientSession.setNote(delegationScope.getId(), delegationScope.getName());
        logger.debugf("Injected 'delegation' scope (id=%s) onto client session for client '%s'",
                delegationScope.getId(), requestingClient.getClientId());
    }
}