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

import java.util.List;
import java.util.Objects;

public final class DelegationTokenExchangeProvider extends StandardTokenExchangeProvider {

    public static final String PARAM_DELEGATION_CONTEXT = "delegation_context";
    public static final String SUBJECT_TOKEN_PARAM = "subject_token";
    public static final String ACT_SESSION_NOTE_CLAIM = "delegation-act-claim";
    private static final String DELEGATE_TYPE_SERVICE_ACCOUNT = "service_account";

    private final KeycloakSession session;

    /**
     * Request-scoped transient state used only during one exchange() call
     * and then consumed by createSessionModel().
     */
    private String actClaimJson;

    private static final org.jboss.logging.Logger logger =
            org.jboss.logging.Logger.getLogger(DelegationTokenExchangeProvider.class);

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
            subject = AuthTokenHelper.verifyAccessToken(context.getSession(), subjectToken);
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

        if (delegation.getDelegate() == null) {
            throw new BadRequestException("Missing delegate in delegation_context");
        }

        if (delegation.getDelegator() == null || delegation.getDelegator().getUserId() == null
                || delegation.getDelegator().getUserId().isBlank()) {
            throw new BadRequestException("Missing delegator.userId in delegation_context");
        }

        boolean delegateIsServiceAccount =
                DELEGATE_TYPE_SERVICE_ACCOUNT.equalsIgnoreCase(delegation.getDelegate().getType());

        List<String> delegateClientIds = delegation.getDelegate().getClientIds();
        boolean allowAnyServiceAccount =
                delegateIsServiceAccount && (delegateClientIds == null || delegateClientIds.isEmpty());

        // Resolve whether the subject token represents a service account.
        String subjectClientId = subject.getIssuedFor();
        ClientModel subjectClient = null;
        boolean subjectIsServiceAccount = false;

        if (subjectClientId != null && !subjectClientId.isBlank()) {
            subjectClient = realm.getClientByClientId(subjectClientId);
            subjectIsServiceAccount = subjectClient != null && subjectClient.isServiceAccountsEnabled();
        }

        UserModel delegateUser;

        if (delegateIsServiceAccount) {
            // Policy:
            // 1. delegate.type=service_account and no/empty clientIds => any service account caller allowed
            // 2. delegate.type=service_account and clientIds provided => caller must be one of those service account clients
            //
            // In both cases, the effective delegate user is the service account user represented by subject_token.

            if (!subjectIsServiceAccount) {
                throw new NotAuthorizedException(
                        "delegation_context requires subject_token to represent a service account");
            }

            if (!allowAnyServiceAccount) {
                if (delegateClientIds == null || !delegateClientIds.contains(subjectClientId)) {
                    throw new NotAuthorizedException(
                            "Service account not permitted for delegation: " + subjectClientId);
                }
            }

            delegateUser = session.users().getServiceAccount(subjectClient);
            if (delegateUser == null) {
                throw new BadRequestException(
                        "Unable to resolve service account user for client: " + subjectClientId);
            }

            logger.debugf(
                    "Service account subject %s allowed by delegation policy (allowAny=%s)",
                    subjectClientId,
                    allowAnyServiceAccount);

        } else {
            // Non-service-account delegate resolution:
            // Try as userId first.
            delegateUser = null;

            if (delegation.getDelegate().getUserId() != null
                    && !delegation.getDelegate().getUserId().isBlank()) {
                delegateUser = session.users().getUserById(realm, delegation.getDelegate().getUserId());
            }

            // If not found, optionally try resolving from the first configured clientId as a service account.
            // This preserves your previous fallback behavior for non-service-account typed delegates.
            if (delegateUser == null
                    && delegateClientIds != null
                    && !delegateClientIds.isEmpty()) {
                ClientModel client = realm.getClientByClientId(delegateClientIds.get(0));
                if (client != null && client.isServiceAccountsEnabled()) {
                    delegateUser = session.users().getServiceAccount(client);
                }
            }

            if (delegateUser == null) {
                throw new BadRequestException("Unknown delegate (user or service account)");
            }
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

        AuthenticationSessionModel authSession = super.createSessionModel(
                targetUserSession, rootAuthSession, targetUser, client, scope);

        if (this.actClaimJson != null) {
            authSession.setUserSessionNote(ACT_SESSION_NOTE_CLAIM, this.actClaimJson);

            if (targetUserSession != null) {
                targetUserSession.setNote(ACT_SESSION_NOTE_CLAIM, this.actClaimJson);
            }
        }

        return authSession;
    }
}