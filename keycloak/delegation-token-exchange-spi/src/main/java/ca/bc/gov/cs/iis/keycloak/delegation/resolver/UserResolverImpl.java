package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.protocol.oidc.mappers.SHA256PairwiseSubMapper;
import org.keycloak.protocol.oidc.mappers.PairwiseSubMapperHelper;
import org.keycloak.protocol.oidc.utils.PairwiseSubMapperUtils;

import java.util.List;

public class UserResolverImpl implements UserResolver {

    private static final Logger logger = Logger.getLogger(UserResolverImpl.class);

    private final KeycloakSession session;

    public UserResolverImpl(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public UserModel resolveUserFromToken(RealmModel realm, AccessToken subjectAccessToken) {

        String sessionId = subjectAccessToken.getSessionId();

        // --- 1. Try normal user session resolution ---
        if (sessionId != null && !sessionId.isBlank()) {
            UserSessionModel userSession = session.sessions().getUserSession(realm, sessionId);
            if (userSession != null) {
                return userSession.getUser();
            }
        }

        // --- 2. Fallback: resolve directly from token subject ---
        String subject = subjectAccessToken.getSubject();
        if (subject != null) {
            UserModel user = session.users().getUserById(realm, subject);
            if (user != null) {
                return user;
            }
        }

        // --- 3. Fallback: resolve as service account via client ---
        String clientId = subjectAccessToken.getIssuedFor(); // azp
        if (clientId != null) {
            ClientModel client = realm.getClientByClientId(clientId);
            if (client != null && client.isServiceAccountsEnabled()) {
                UserModel serviceAccount = session.users().getServiceAccount(client);
                if (serviceAccount != null) {
                    return serviceAccount;
                }
            }
        }

        logger.warn("Unable to resolve user from token (no session, subject, or service account match)");
        return null;
    }

    @Override
    public String computePairwiseSub(RealmModel realm, ClientModel client, UserModel user) {

        ProtocolMapperModel mapper = findPairwiseMapper(client);
        if (mapper == null) {
            throw new IllegalStateException(
                    "No pairwise mapper found for client: " + client.getClientId());
        }

        // 🔑 Extract sector + salt
        String sectorUri = PairwiseSubMapperHelper.getSectorIdentifierUri(mapper);
        String salt = PairwiseSubMapperHelper.getSalt(mapper);

        logger.infof("Pairwise mapper found: sectorUri=%s salt=%s", sectorUri, salt);

        // 🔑 Resolve sector identifier EXACTLY like Keycloak
        String sectorIdentifier;

        if (sectorUri != null && !sectorUri.isBlank()) {
            sectorIdentifier = PairwiseSubMapperUtils.resolveValidSectorIdentifier(sectorUri);
        } else {
            sectorIdentifier = PairwiseSubMapperUtils.resolveValidSectorIdentifier(
                    client.getRootUrl(),
                    client.getRedirectUris());
        }

        if (sectorIdentifier == null || sectorIdentifier.isBlank()) {
            throw new IllegalStateException(
                    "Unable to resolve sector identifier for client: " + client.getClientId());
        }

        logger.infof("Resolved sectorIdentifier=%s", sectorIdentifier);

        // 🔥 THIS is the key fix
        SHA256PairwiseSubMapper generator = new SHA256PairwiseSubMapper();

        String result = generator.generateSub(mapper, sectorIdentifier, user.getId());

        logger.infof("Generated pairwise sub for user=%s → %s", user.getId(), result);

        return result;
    }

    private ProtocolMapperModel findPairwiseMapper(ClientModel client) {

        // 1. Check client mappers
        List<ProtocolMapperModel> mappers = client.getProtocolMappersStream().toList();

        for (ProtocolMapperModel mapper : mappers) {
            if (isPairwiseMapper(mapper)) {
                return mapper;
            }
        }

        // 2. Check client scopes
        for (ClientScopeModel scope : client.getClientScopes(true).values()) {
            for (ProtocolMapperModel mapper : scope.getProtocolMappersStream().toList()) {
                if (isPairwiseMapper(mapper)) {
                    return mapper;
                }
            }
        }

        return null;
    }

    private boolean isPairwiseMapper(ProtocolMapperModel mapper) {
        return "oidc-sha256-pairwise-sub-mapper".equals(mapper.getProtocolMapper());
    }
}