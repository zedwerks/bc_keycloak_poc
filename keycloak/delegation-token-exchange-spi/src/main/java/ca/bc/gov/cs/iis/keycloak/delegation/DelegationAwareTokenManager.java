package ca.bc.gov.cs.iis.keycloak.delegation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.Map;

/**
 * Keycloak 26 - correct approach.
 *
 * In KC26, OIDCLoginProtocol has NO tokenManager field — it does
 * `new TokenManager()` inline at every call site. There is therefore
 * no protocol/factory subclassing hook.
 *
 * The correct override point is createClientAccessToken(), which is the
 * single method all token-issuing paths funnel through:
 *   - initial token grant
 *   - refresh (via AccessTokenResponseBuilder.generateAccessToken())
 *   - introspection (via AccessTokenIntrospectionProvider.transformAccessToken())
 *
 * We override createClientAccessToken() to call super, then stamp act
 * from the client session note before returning the token for signing.
 *
 * HOW TO USE THIS:
 *   Wherever in your existing code you currently do `new TokenManager()`,
 *   replace it with `new DelegationAwareTokenManager()`.
 *
 *   In your CustomTokenExchangeProvider this is already under your control.
 *   For introspection, extend AccessTokenIntrospectionProvider and override
 *   its tokenManager field (see DelegationIntrospectionProvider).
 */
public class DelegationAwareTokenManager extends TokenManager {

    public static final String DELEGATION_ACT_NOTE = "delegation.act";

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    /**
     * All token paths in KC26 ultimately call createClientAccessToken().
     * We let super build the full token (running all mappers etc.),
     * then apply the act claim from the client session note if present.
     */
    @Override
    public AccessToken createClientAccessToken(
            KeycloakSession session,
            RealmModel realm,
            ClientModel client,
            UserModel user,
            UserSessionModel userSession,
            ClientSessionContext clientSessionCtx) {

        AccessToken token = super.createClientAccessToken(
                session, realm, client, user, userSession, clientSessionCtx);

        applyActIfDelegated(token, clientSessionCtx);
        return token;
    }

    // -------------------------------------------------------------------------

    private void applyActIfDelegated(AccessToken token,
                                     ClientSessionContext clientSessionCtx) {
        if (clientSessionCtx == null) return;

        AuthenticatedClientSessionModel clientSession =
                clientSessionCtx.getClientSession();
        if (clientSession == null) return;

        String actJson = clientSession.getNote(DELEGATION_ACT_NOTE);
        if (actJson == null || actJson.isBlank()) return;

        try {
            Map<String, Object> actClaim =
                    JsonSerialization.readValue(actJson, MAP_TYPE);
            token.setOtherClaims("act", actClaim);
        } catch (IOException e) {
            System.err.printf(
                    "[DelegationAwareTokenManager] Failed to parse %s note: %s%n",
                    DELEGATION_ACT_NOTE, e.getMessage());
        }
    }
}
