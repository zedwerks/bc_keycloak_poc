package ca.bc.gov.cs.iis.keycloak.delegation;

import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.representations.IDToken;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.util.JsonSerialization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActClaimProtocolMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, ProtocolMapper {

    private static final Logger logger = Logger.getLogger(ActClaimProtocolMapper.class);

    public static final String PROVIDER_ID = "oidc-act-claim-protocol-mapper";
    public static final String ACT_SESSION_NOTE_CLAIM = "act";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, ActClaimProtocolMapper.class);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Act Claim Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds the RFC 8693 act claim from a user session note into the access token.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(
            IDToken token,
            ProtocolMapperModel mappingModel,
            UserSessionModel userSession,
            KeycloakSession session,
            ClientSessionContext clientSessionCtx) {

        if (userSession == null) {
            return;
        }

        String actJson = userSession.getNote(ACT_SESSION_NOTE_CLAIM);
        if (actJson == null || actJson.isBlank()) {
            return;
        }

        try {
            Map<String, Object> actMap =
                    JsonSerialization.readValue(actJson, new TypeReference<Map<String, Object>>() {});

            String claimName = mappingModel.getConfig() != null
                    ? mappingModel.getConfig().getOrDefault("claim.name", "act")
                    : "act";

            token.getOtherClaims().put(claimName, actMap);

        } catch (Exception e) {
            logger.warnf(e, "Failed to parse act claim JSON from user session note: %s", actJson);
        }
    }
}