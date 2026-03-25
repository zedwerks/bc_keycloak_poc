package ca.bc.gov.cs.iis.keycloak.delegation;

import ca.bc.gov.cs.iis.keycloak.auth.AuthTokenHelper;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolver;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolverImpl;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.OAuthErrorException;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.protocol.oidc.tokenexchange.AbstractTokenExchangeProvider;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CustomTokenExchangeProvider extends AbstractTokenExchangeProvider {

    public static final String PROVIDER_ID = "custom-token-exchange";
    public static final String PARAM_DELEGATION_CONTRACT = "delegation_contract";
    public static final String PARAM_SUBJECT_TOKEN = "subject_token";
    public static final String ACT_SESSION_NOTE_CLAIM = "delegation-act-claim";

    /**
     * Use the standard parameter names so Keycloak internals and protocol mappers
     * can behave more like the built-in token exchange flow.
     */
    private static final String PARAM_AUDIENCE = "audience";
    private static final String PARAM_SCOPE = "scope";

    private static final String REQUESTED_TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    private static final String RESPONSE_TOKEN_TYPE_BEARER = "Bearer";
    private static final String AUTH_METHOD = "custom-token-exchange";

    private static final Logger logger = Logger.getLogger(CustomTokenExchangeProvider.class);

    private String actClaimJson;

    @Override
    public boolean supports(org.keycloak.protocol.oidc.TokenExchangeContext context) {
        return context.getFormParams() != null
                && context.getFormParams().getFirst(PARAM_DELEGATION_CONTRACT) != null;
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
        final String delegationContextEncoded = formParams.getFirst(PARAM_DELEGATION_CONTRACT);

        if (actorTokenEncoded == null || actorTokenEncoded.isBlank()) {
            logger.warn("Missing subject_token");
            throw new BadRequestException("Missing subject_token");
        }
        if (delegationContextEncoded == null || delegationContextEncoded.isBlank()) {
            logger.warn("Missing delegation_contract");
            throw new BadRequestException("Missing delegation_contract");
        }

        final AccessToken actorToken;
        try {
            actorToken = AuthTokenHelper.verifyAccessToken(session, actorTokenEncoded);
        } catch (OAuthErrorException e) {
            logger.warn("Failed to verify subject_token", e);
            throw new NotAuthorizedException("subject_token invalid", e);
        }

        final DelegationContext delegation;
        try {
            delegation = DelegationContext.fromBase64Url(delegationContextEncoded);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delegation_contract", e);
            throw new BadRequestException("Invalid delegation_contract", e);
        }

        if (delegation.getExpiresAt() != 0
                && delegation.getExpiresAt() < (System.currentTimeMillis() / 1000L)) {
            logger.warn("delegation_contract expired");
            throw new BadRequestException("delegation_contract expired");
        }

        if (delegation.getDelegate() == null
                || delegation.getDelegate().getUserId() == null
                || delegation.getDelegate().getUserId().isBlank()) {
            logger.warn("Missing delegate.userId in delegation_contract");
            throw new BadRequestException("Missing delegate.userId in delegation_contract");
        }

        if (delegation.getDelegator() == null
                || delegation.getDelegator().getUserId() == null
                || delegation.getDelegator().getUserId().isBlank()) {
            logger.warn("Missing delegator.userId in delegation_contract");
            throw new BadRequestException("Missing delegator.userId in delegation_contract");
        }

        final UserModel delegateUser = session.users().getUserById(realm, delegation.getDelegate().getUserId());
        if (delegateUser == null) {
            logger.warn("Unknown delegate user in delegation_contract");
            throw new BadRequestException("Unknown delegate user in delegation_contract");
        }

        final UserModel delegatorUser = session.users().getUserById(realm, delegation.getDelegator().getUserId());
        if (delegatorUser == null) {
            logger.warn("Unknown delegator user in delegation_contract");
            throw new BadRequestException("Unknown delegator user in delegation_contract");
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
        actClaim.setClientId(client.getClientId());
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

        final ClientModel requestingClient = client;

        final AuthenticationSessionManager asm = new AuthenticationSessionManager(session);
        final RootAuthenticationSessionModel rootAuthSession = asm.createAuthenticationSession(realm, false);

        final AuthenticationSessionModel authSession = createSessionModel(
                targetUserSession,
                rootAuthSession,
                targetUser,
                requestingClient,
                scope);

        if (this.actClaimJson != null) {
            authSession.setUserSessionNote(ACT_SESSION_NOTE_CLAIM, this.actClaimJson);
            targetUserSession.setNote(ACT_SESSION_NOTE_CLAIM, this.actClaimJson);
        }

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

        final ClientSessionContext clientSessionCtx = TokenManager.attachAuthenticationSession(
                session,
                targetUserSession,
                authSession);

        final TokenManager.AccessTokenResponseBuilder builder = tokenManager
                .responseBuilder(realm, requestingClient, event, session, targetUserSession, clientSessionCtx)
                .generateAccessToken()
                .responseTokenType(RESPONSE_TOKEN_TYPE_BEARER);

        final AccessToken issuedAccessToken = builder.getAccessToken();

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
}