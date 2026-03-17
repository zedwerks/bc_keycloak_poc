package ca.bc.gov.cs.iis.keycloak.delegation;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.BadRequestException;

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
import org.keycloak.services.ErrorResponseException;

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

    private static final org.jboss.logging.Logger logger = org.jboss.logging.Logger
            .getLogger(DelegationTokenExchangeProvider.class);

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

        return delegationParamPresent;
    }

    @Override
    public Response exchange(TokenExchangeContext context) {

        MultivaluedMap<String, String> form = context.getFormParams();

        // ------------------------------------------------------------------
        // 1. Extract parameters
        // ------------------------------------------------------------------

        String subjectToken = form.getFirst("subject_token");
        String delegationContextEncoded = form.getFirst("delegation_context");

        if (subjectToken == null || subjectToken.isBlank()) {
            throw new BadRequestException("Missing subject_token");
        }

        if (delegationContextEncoded == null || delegationContextEncoded.isBlank()) {
            throw new BadRequestException("Missing delegation_context");
        }

        // ------------------------------------------------------------------
        // 2. Validate subject_token (LET KEYCLOAK DO THE HEAVY LIFTING)
        // ------------------------------------------------------------------

        AccessToken subject;
        try {
            subject = AuthTokenHelper.verifySubjectToken(context.getSession(), subjectToken);

        } catch (OAuthErrorException e) {
            throw new NotAuthorizedException("subject_token invalid", e);
        }

        // ------------------------------------------------------------------
        // 3. Parse delegation_context
        // ------------------------------------------------------------------

        DelegationContext delegation;
        try {
            delegation = DelegationContext.fromBase64Url(delegationContextEncoded);

        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid delegation_context", e);
        }

        // ------------------------------------------------------------------
        // 4. Your delegation rules (THIS IS YOUR DOMAIN)
        // ------------------------------------------------------------------

        // Example: subject must match delegate_user_id
        if (!subject.getSubject().equals(delegation.getDelegateUserId())) {
            throw new ForbiddenException("Delegation subject mismatch");
        }

        // Example: expiry
        if (delegation.getExpiresAt() != 0 &&
                delegation.getExpiresAt() < (System.currentTimeMillis() / 1000)) {
            throw new BadRequestException("delegation_context expired");
        }

        // ------------------------------------------------------------------
        // 5. (Optional) Scope enforcement
        // ------------------------------------------------------------------

        if (delegation.getScope() != null && !delegation.getScope().isEmpty()) {

            // You can enforce intersection with subject token scopes here
            // (optional but recommended)

        }

        // ------------------------------------------------------------------
        // 6. Continue with normal token exchange
        // ------------------------------------------------------------------

        // 🔥 IMPORTANT:
        // At this point you should call into Keycloak’s normal token exchange
        // OR build your own token
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