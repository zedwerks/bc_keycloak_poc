package ca.bc.gov.cs.iis.keycloak.delegation;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.keycloak.OAuthErrorException;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.TokenExchangeContext;
import org.keycloak.protocol.oidc.tokenexchange.StandardTokenExchangeProvider;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.protocol.oidc.utils.PairwiseSubMapperUtils;

import ca.bc.gov.cs.iis.keycloak.ppid.PairwiseUtil;
import ca.bc.gov.cs.iis.keycloak.auth.AuthTokenHelper;

import java.time.Instant;
import java.util.Objects;

/**
 * Wraps StandardTokenExchangeProvider:
 * - when delegation_context is provided, calls delegation service
 * - stores resulting JSON as a UserSession note
 * - relies on UserSessionNoteMapper to project it into the access token claim
 */
public final class DelegationTokenExchangeProvider extends StandardTokenExchangeProvider {

    public static final String PARAM_DELEGATION_CONTEXT = "delegation_context";
    public static final String SESSION_NOTE_DELEGATION_JSON = "delegation_json";
    public static final String SUBJECT_TOKEN_PARAM = "subject_token";
    private final KeycloakSession session;

    // per-request cached value
    private String delegationJson;

    public DelegationTokenExchangeProvider(KeycloakSession session) {
        super();
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public boolean supports(TokenExchangeContext context) {

        MultivaluedMap<String, String> form = context.getFormParams();

        boolean delegationParamPresent = form != null && form.getFirst(PARAM_DELEGATION_CONTEXT) != null;

        ClientModel client = context.getClient();

        UserModel serviceAccount = context.getSession()
                .users()
                .getServiceAccount(client);


        return  delegationParamPresent;
    }

    @Override
    public Response exchange(TokenExchangeContext context) {

        String delegationContextEncoded = context.getFormParams().getFirst(PARAM_DELEGATION_CONTEXT);

        if (delegationContextEncoded == null || delegationContextEncoded.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing delegation_context")
                    .build();
        }

        DelegationContext d = DelegationContext.fromBase64Url(delegationContextEncoded);

        if (d.getExpiresAt() != 0 &&
                d.getExpiresAt() < Instant.now().getEpochSecond()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Delegation expired")
                    .build();
        }

        RealmModel realm = session.getContext().getRealm();
        ClientModel targetClient = context.getClient();

        // Resolve delegation-zone users

        UserModel delegatorUserId = session.users().getUserById(realm, d.getDelegatorUserId());
        UserModel delegateUserId = session.users().getUserById(realm, d.getDelegateUserId());

        // Verify delegate == subject token user

        String subjectTokenString = context.getFormParams().getFirst(SUBJECT_TOKEN_PARAM);

        try {

            if (subjectTokenString == null || subjectTokenString.isBlank()) {
                throw new OAuthErrorException(
                        OAuthErrorException.INVALID_REQUEST,
                        "Missing subject_token");
            }

            AccessToken subjectAccessToken = AuthTokenHelper.verifySubjectToken(
                    realm,
                    session,
                    subjectTokenString);
                    
            String userIdString = subjectAccessToken.getSubject();
            if (!delegateUserId.getId().equals(userIdString)) {
                throw new OAuthErrorException(
                        OAuthErrorException.INVALID_REQUEST,
                        "subject_token user does not match delegate user");
            }

        } catch (VerificationException | OAuthErrorException e) {
            throw new IllegalArgumentException("Invalid subject_token", e);
        }

        // Get pairwise subs for target client (audience)

        String delegatorTargetSub = PairwiseUtil.getSubjectIdentifier(delegatorUserId, realm, targetClient);
        String delegateTargetSub = PairwiseUtil.getSubjectIdentifier(delegateUserId, realm, targetClient);

        DelegationContext translated = new DelegationContext(
                d.getId(),
                delegatorTargetSub,
                delegateTargetSub,
                d.getScope(),
                d.getExpiresAt());

        this.delegationJson = DelegationContext.toJsonString(translated);

        return super.exchange(context);
    }

    /**
     * Hook called by AbstractTokenExchangeProvider during token exchange.
     * We add the note to the *target* user session so UserSessionNoteMapper can map
     * it into tokens.
     */
    @Override
    protected AuthenticationSessionModel createSessionModel(
            UserSessionModel targetUserSession,
            RootAuthenticationSessionModel rootAuthSession,
            UserModel targetUser,
            ClientModel client,
            String scope) {
        // add note before standard code mints tokens
        if (targetUserSession != null && delegationJson != null && !delegationJson.isBlank()) {
            targetUserSession.setNote(SESSION_NOTE_DELEGATION_JSON, delegationJson);
        }
        return super.createSessionModel(targetUserSession, rootAuthSession, targetUser, client, scope);
    }

}