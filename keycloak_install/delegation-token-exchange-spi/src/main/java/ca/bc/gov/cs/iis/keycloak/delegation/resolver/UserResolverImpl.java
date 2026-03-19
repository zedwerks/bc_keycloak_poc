package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import ca.bc.gov.cs.iis.keycloak.delegation.DelegationContext;
import ca.bc.gov.cs.iis.keycloak.ppid.PairwiseUtil;

import org.keycloak.protocol.oidc.mappers.PairwiseSubMapperHelper;
import org.keycloak.models.ClientModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class UserResolverImpl implements UserResolver {

    private static final String SHA_256 = "SHA-256";
    private static final String SALT_ATTR = "pairwiseSubSalt";
    private static final String SECTOR_IDENTIFIER_URI_ATTR = "sector.identifier.uri";

    private final KeycloakSession session;
    private static final org.jboss.logging.Logger logger = org.jboss.logging.Logger.getLogger(UserResolverImpl.class);

    public UserResolverImpl(KeycloakSession session) {
        this.session = session;
    }

    public String resolveZoneName(ClientModel client) {
        // Example only: you can derive this from client attributes, client tags, or
        // client scopes
        String zone = PairwiseUtil.getSectorIdentifier(client);
        return zone;
    }

    public UserModel resolveUserFromToken(RealmModel realm, AccessToken subjectAccessToken) {

        String sessionId = subjectAccessToken.getSessionId();

        if (sessionId == null || sessionId.isBlank()) {
            logger.warn("Session ID not found in token; unable to resolve user");
            return null; // no session, can't resolve user
        }
        UserSessionModel userSession = session.sessions().getUserSession(realm, sessionId);
        if (userSession == null) {
            return null; // no active session, can't resolve user
        }

        return userSession.getUser();
    }

    public String computePairwiseSub(RealmModel realm, ClientModel client, UserModel user) {
        try {
            String sector = resolveSectorIdentifier(client);
            String userId = user.getId();
            String salt = realm.getAttribute(SALT_ATTR);

            if (salt == null) {
                throw new IllegalStateException("Missing pairwiseSubSalt in realm");
            }

            String input = sector + userId + salt;

            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute pairwise subject", e);
        }
    }

    private String resolveSectorIdentifier(ClientModel client) {
        return PairwiseUtil.getSectorIdentifier(client);
    }
}
