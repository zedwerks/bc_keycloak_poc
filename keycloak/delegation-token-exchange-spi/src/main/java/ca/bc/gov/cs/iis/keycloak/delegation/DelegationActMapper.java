package ca.bc.gov.cs.iis.keycloak.delegation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Protocol mapper that injects the RFC 8693 {@code act} (actor) claim into
 * access tokens issued for delegated sessions.
 *
 * <h2>Why this mapper exists</h2>
 * During a token exchange, {@link CustomTokenExchangeProvider} writes the
 * delegation context (act.sub, act.client_id, contract ID) into the
 * {@link UserSessionModel} notes keyed by the constants in
 * {@link CustomTokenExchangeProvider#SESSION_NOTE_ACT_JSON} et al.
 *
 * Because {@code UserSessionModel} notes are persisted with the session,
 * they survive the original exchange request. When the RP later submits a
 * {@code refresh_token} grant, Keycloak rebuilds a new access token by
 * re-running all protocol mappers against the same session — at which point
 * this mapper reads the notes and re-injects the {@code act} claim. No
 * special handling of the refresh path is required.
 *
 * <h2>Non-delegation sessions</h2>
 * If the {@code delegation.act} note is absent (Joe's normal session, or any
 * session that did not go through the delegation exchange), this mapper is a
 * no-op and no {@code act} claim is emitted — which is exactly the desired
 * behaviour when Joe authenticates as himself.
 *
 * <h2>Registration</h2>
 * Register this mapper as a provider in your Keycloak SPI module and attach
 * it to the "delegation" optional client scope. The
 * {@link CustomTokenExchangeProvider} programmatically injects that scope
 * onto the client session (via {@code injectDelegationScope}) so the mapper
 * fires even when the caller does not explicitly request {@code scope=delegation}.
 */
public class DelegationActMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "delegation-act-mapper";

    private static final Logger logger = Logger.getLogger(DelegationActMapper.class);

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of();

    // -------------------------------------------------------------------------
    // ProviderConfigProperty / display metadata
    // -------------------------------------------------------------------------

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Delegation Act Claim";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Injects the RFC 8693 'act' claim from delegation session notes. "
                + "Present on both initial access tokens and tokens minted from a refresh grant.";
    }

    // -------------------------------------------------------------------------
    // Core mapping — fires on access token, ID token, and userinfo.
    //
    // The delegation.act session note is the guard: if it is absent (Joe's
    // normal session, or any session that did not go through the delegation
    // exchange) this mapper is a no-op and no act claim is emitted.
    // We do this inline rather than via a shouldBeIncluded() override because
    // that method does not exist on AbstractOIDCProtocolMapper in KC 24+.
    // -------------------------------------------------------------------------

    @Override
    protected void setClaim(IDToken token,
                            ProtocolMapperModel mappingModel,
                            UserSessionModel userSession,
                            KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx) {

        final String actJson = userSession.getNote(CustomTokenExchangeProvider.SESSION_NOTE_ACT_JSON);

        if (actJson == null || actJson.isBlank()) {
            // Not a delegated session — no act claim should be emitted.
            logger.tracef("No delegation.act note on session '%s'; skipping act claim injection.",
                    userSession.getId());
            return;
        }

        try {
            final Map<String, Object> actMap = JsonSerialization.readValue(
                    actJson, new TypeReference<Map<String, Object>>() {});

            token.setOtherClaims("act", actMap);

            logger.debugf("Injected act claim on token for session '%s': act.sub=%s",
                    userSession.getId(),
                    actMap.getOrDefault("sub", "<missing>"));

        } catch (IOException e) {
            // Log and skip — a malformed note should not break token issuance.
            logger.warnf("Failed to deserialize delegation.act note on session '%s': %s",
                    userSession.getId(), e.getMessage());
        }
    }

}
