
package ca.bc.gov.cs.iis.keycloak.ppid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ClientInfoStatus;
import java.util.Base64;

import java.security.NoSuchAlgorithmException;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.utils.PairwiseSubMapperUtils;

public class PairwiseUtil {

    public static String SHA_256 = "SHA-256";
    private static final String SALT_ATTR = "pairwiseSubSalt";
    private static final String SECTOR_IDENTIFIER_URI_ATTR = "sector.identifier.uri";

    public static String getSectorIdentifier(ClientModel client) {

        String sectorIdentifier = client.getAttribute(SECTOR_IDENTIFIER_URI_ATTR);

        if (sectorIdentifier == null || sectorIdentifier.isEmpty()) {
            sectorIdentifier = client.getClientId();
        }

        return sectorIdentifier;
    }

    private static String pairwiseSub(ClientModel client, UserModel user, String salt) {

        String sectorIdentifier = getSectorIdentifier(client);
        String input = sectorIdentifier + user.getId() + salt;

        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a pairwise identifier for the given user and client.
     *
     * @param user   the user for whom to generate the identifier
     * @param realm  the Keycloak realm
     * @param client the client for which to generate the identifier
     * @return a pairwise identifier string
     */
    public static String getSubjectIdentifier(UserModel user, RealmModel realm, ClientModel client) {
        // Generate pairwise identifier for the user/client combination

        String pairwiseSub = PairwiseUtil.pairwiseSub(client, user, realm.getAttribute(SALT_ATTR));
        return pairwiseSub;
    }
}