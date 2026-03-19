package ca.bc.gov.cs.iis.keycloak.delegation;

import ca.bc.gov.cs.iis.keycloak.auth.AuthTokenHelper;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolver;
import ca.bc.gov.cs.iis.keycloak.delegation.resolver.UserResolverImpl;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuthErrorException;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenExchangeContext;
import org.keycloak.protocol.oidc.tokenexchange.StandardTokenExchangeProvider;
import org.keycloak.representations.AccessToken;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.Objects;

public final class DelegationTokenExchangeProvider extends StandardTokenExchangeProvider {

    public static final String PARAM_DELEGATION_CONTEXT = "delegation_context";
    public static final String SUBJECT_TOKEN_PARAM = "subject_token";
    private static final String ACT_SESSION_NOTE_CLAIM = "act";

    private final KeycloakSession session;

    /**
     * Request-scoped transient state used only during one exchange() call
     * and then consumed by createSessionModel().
     */
    private String actClaimJson;

    private static final org.jboss.logging.Logger logger = org.jboss.logging.Logger
            .getLogger(DelegationTokenExchangeProvider.class);

    public DelegationTokenExchangeProvider(KeycloakSession session) {
        super();
        this.session = Objects.requireNonNull(session);
    }

    @Override
    public boolean supports(TokenExchangeContext context) {
        MultivaluedMap<String, String> form = context.getFormParams();
        return form != null && form.getFirst(PARAM_DELEGATION_CONTEXT) != null;
    }

    @Override
    public Response exchange(TokenExchangeContext context) {
        // clear request-scoped transient state defensively
        this.actClaimJson = null;

        MultivaluedMap<String, String> form = context.getFormParams();

        String subjectToken = form.getFirst(SUBJECT_TOKEN_PARAM);
        String delegationContextEncoded = form.getFirst(PARAM_DELEGATION_CONTEXT);
        String audience = form.getFirst("audience");

        if (subjectToken == null || subjectToken.isBlank()) {
            throw new BadRequestException("Missing subject_token");
        }

        if (delegationContextEncoded == null || delegationContextEncoded.isBlank()) {
            throw new BadRequestException("Missing delegation_context");
        }

        if (audience == null || audience.isBlank()) {
            throw new BadRequestException("Missing audience");
        }

        AccessToken subject;
        try {
            subject = AuthTokenHelper.verifySubjectToken(context.getSession(), subjectToken);
        } catch (OAuthErrorException e) {
            throw new NotAuthorizedException("subject_token invalid", e);
        }

        DelegationContext delegation;
        try {
            delegation = DelegationContext.fromBase64Url(delegationContextEncoded);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid delegation_context", e);
        }

        if (delegation.getExpiresAt() != 0
                && delegation.getExpiresAt() < (System.currentTimeMillis() / 1000L)) {
            throw new BadRequestException("delegation_context expired");
        }

        RealmModel realm = context.getRealm();
        UserResolver userResolver = new UserResolverImpl(context.getSession());

        UserModel delegateUser = session.users().getUserById(realm, delegation.getDelegate().getUserId());
        if (delegateUser == null) {
            throw new BadRequestException("Unknown delegate user");
        }

        UserModel delegatorUser = session.users().getUserById(realm, delegation.getDelegator().getUserId());
        if (delegatorUser == null) {
            throw new BadRequestException("Unknown delegator user");
        }

        ClientModel targetClient = realm.getClientByClientId(audience);
        if (targetClient == null) {
            throw new BadRequestException("Unknown audience client: " + audience);
        }

        try {
            String delegateSubjectId = userResolver.computePairwiseSub(realm, targetClient, delegateUser);
            String delegatorSubjectId = userResolver.computePairwiseSub(realm, targetClient, delegatorUser);
            logger.infof(
                    "Delegation exchange: delegateSub=%s delegatorSub=%s audience=%s",
                    delegateSubjectId, delegatorSubjectId, audience);

            ActClaim actClaim = new ActClaim(delegatorSubjectId);
            actClaim.setClientId(subject.getIssuedFor());
            this.actClaimJson = ActClaim.toJsonString(actClaim);

        } catch (RuntimeException e) {
            logger.error("Failed to compute pairwise subject", e);
            throw new BadRequestException("Failed to compute pairwise subject", e);
        }
        // serialize once here; createSessionModel() will attach it to the target user
        // session

        try {
            return super.exchange(context);
        } finally {
            // defensive cleanup
            this.actClaimJson = null;
        }
    }

    /**
     * Hook called during token exchange before tokens are minted.
     * Put the serialized act claim on the target user session so a
     * UserSessionNoteMapper (or custom mapper) can emit it into the token.
     */
    @Override
    protected AuthenticationSessionModel createSessionModel(
            UserSessionModel targetUserSession,
            RootAuthenticationSessionModel rootAuthSession,
            UserModel targetUser,
            ClientModel client,
            String scope) {

        if (targetUserSession != null && this.actClaimJson != null) {
            targetUserSession.setNote(ACT_SESSION_NOTE_CLAIM, this.actClaimJson);
        }

        return super.createSessionModel(targetUserSession, rootAuthSession, targetUser, client, scope);
    }
}