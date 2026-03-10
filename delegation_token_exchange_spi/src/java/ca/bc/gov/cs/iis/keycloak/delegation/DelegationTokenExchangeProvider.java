package ca.bc.gov.cs.iis.keycloak.delegation;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.AuthenticationSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RootAuthenticationSessionModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenExchangeContext;
import org.keycloak.protocol.oidc.tokenexchange.StandardTokenExchangeProvider;

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
    public static final String NOTE_DELEGATION_JSON = "delegation_json";

    private final KeycloakSession session;
    private final DelegationServiceClient delegationClient;

    // per-request cached value
    private String delegationJson;

    public DelegationTokenExchangeProvider(KeycloakSession session, DelegationServiceClient delegationClient) {
        super();
        this.session = Objects.requireNonNull(session);
        this.delegationClient = Objects.requireNonNull(delegationClient);
    }

    @Override
    public boolean supports(TokenExchangeContext context) {
        // Only handle requests that explicitly include delegation_context.
        // Otherwise let standard provider handle the request.
        MultivaluedMap<String, String> form = context.getFormParams();
        return form != null && form.getFirst(PARAM_DELEGATION_CONTEXT) != null;
    }

    @Override
    public Response exchange(TokenExchangeContext context) {
        // 1) read request param
        String delegationContext = context.getFormParams().getFirst(PARAM_DELEGATION_CONTEXT);
        if (delegationContext == null || delegationContext.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing delegation_context")
                    .build();
        }

        // 2) call your delegation service
        // You can authenticate this call with client credentials, mTLS, etc.
        // Strongly recommend returning an expiry and validating it.
        DelegationResult res = delegationClient.resolveDelegation(delegationContext, context);

        if (!res.isAllowed()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Delegation not allowed")
                    .build();
        }

        if (res.getExpiresAt() != null && res.getExpiresAt().isBefore(Instant.now())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Delegation expired")
                    .build();
        }

        this.delegationJson = res.getDelegationJson();

        // 3) delegate to the standard token exchange implementation
        return super.exchange(context);
    }

    /**
     * Hook called by AbstractTokenExchangeProvider during token exchange.
     * We add the note to the *target* user session so UserSessionNoteMapper can map it into tokens.
     */
    @Override
    protected AuthenticationSessionModel createSessionModel(
            UserSessionModel targetUserSession,
            RootAuthenticationSessionModel rootAuthSession,
            UserModel targetUser,
            ClientModel client,
            String scope
    ) {
        // add note before standard code mints tokens
        if (targetUserSession != null && delegationJson != null && !delegationJson.isBlank()) {
            targetUserSession.setNote(NOTE_DELEGATION_JSON, delegationJson);
        }
        return super.createSessionModel(targetUserSession, rootAuthSession, targetUser, client, scope);
    }
}