package ca.bc.gov.cs.iis.keycloak.delegation.client;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.io.IOException;
import java.lang.InterruptedException;

import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;

public class DelegationServiceClient {

    private final HttpClient http = HttpClient.newHttpClient();

    protected static final Logger logger = Logger.getLogger(DelegationServiceClient.class);

    /**
     * How long (in seconds) the self-signed assertion JWT remains valid.
     * Keep this short — it is a single-use outbound credential.
     */
    private static final int TOKEN_TTL_SECONDS = 30;

    private final String baseUrl;
    private final String delegationServiceAudience;
    private final KeycloakSession session;

    /**
     * @param baseUrl                   Base URL of the delegation service REST API.
     * @param delegationServiceAudience The audience URI the delegation service will
     *                                  validate in the incoming JWT — typically the
     *                                  delegation service's own base URL or a
     *                                  logical
     *                                  identifier agreed upon at deployment time.
     * @param session                   The active KeycloakSession (injected by the
     *                                  SPI).
     */
    public DelegationServiceClient(String baseUrl, String delegationServiceAudience, KeycloakSession session) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.delegationServiceAudience = delegationServiceAudience;
        this.session = session;
    }

    // -------------------------------------------------------------------------
    // Self-signed JWT assertion (Option 3)
    //
    // We use Keycloak's own realm signing key — the same RS256 key that signs
    // normal access tokens — to mint a short-lived JWT assertion identifying
    // Keycloak as the caller. No outbound token request is made; the key
    // material is accessed directly through the KeycloakSession.
    //
    // The delegation service validates the JWT by:
    // 1. Fetching the realm's JWKS:
    // {keycloak-base}/realms/{realm}/protocol/openid-connect/certs
    // 2. Matching on the "kid" header claim.
    // 3. Verifying the RS256 signature.
    // 4. Checking iss, aud, exp in the standard way.
    // -------------------------------------------------------------------------

    /**
     * Mints a short-lived, self-signed JWT assertion using the realm's active
     * RS256 signing key. The token is suitable for a single outbound call to
     * the delegation service and should never be cached.
     *
     * Claims produced:
     * jti — random UUID (prevents replay within the TTL window)
     * iss — realm issuer URL (e.g. https://auth.example.com/realms/myrealm)
     * sub — realm issuer URL (identifies Keycloak itself as the subject)
     * aud — delegationServiceAudience
     * iat — now (epoch seconds)
     * exp — now + TOKEN_TTL_SECONDS
     * nbf — now (epoch seconds)
     *
     * @return signed compact JWT string
     * @throws DelegationServiceClientException if the realm key is unavailable
     *                                          or signing fails
     */
    private String accessToken() {
        RealmModel realm = session.getContext().getRealm();

        // Resolve the issuer URL the same way Keycloak does internally.
        String issuerUrl = session.getContext().getUri().getBaseUri().toString()
                + "realms/" + realm.getName();

        // Retrieve the realm's current active RS256 signing key.
        KeyWrapper key = session.keys().getActiveKey(realm, KeyUse.SIG, Algorithm.RS256);

        if (key == null) {
            throw new DelegationServiceClientException(
                    "No active RS256 signing key found for realm: " + realm.getName());
        }

        long now = Time.currentTime();

        // Build the JWT claims payload.
        JsonWebToken jwt = new JsonWebToken();
        jwt.id(UUID.randomUUID().toString()); // jti — unique per call
        jwt.issuer(issuerUrl); // iss
        jwt.subject(issuerUrl); // sub — Keycloak itself
        jwt.audience(delegationServiceAudience);// aud
        jwt.issuedNow(); // iat
        jwt.exp(now + TOKEN_TTL_SECONDS);// exp
        jwt.nbf(now); // nbf
        jwt.type("Bearer"); // typ (optional, but can help with debugging and token
                            // introspection)

        // Sign with the realm key via Keycloak's standard signing pipeline.
        // AsymmetricSignatureSignerContext handles RSA signature mechanics;
        // JWSBuilder encodes the JOSE header (alg + kid) and produces the
        // compact serialization.
        try {
            AsymmetricSignatureSignerContext signer = new AsymmetricSignatureSignerContext(key);
            String signedJwt = new JWSBuilder()
                    .kid(key.getKid())
                    .jsonContent(jwt)
                    .sign(signer);

            logger.debugf("Minted delegation service assertion JWT (kid=%s, exp=%d)",
                    key.getKid(), now + TOKEN_TTL_SECONDS);

            return signedJwt;

        } catch (Exception e) {
            throw new DelegationServiceClientException(
                    "Failed to sign delegation service assertion JWT", e);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP plumbing
    // -------------------------------------------------------------------------

    /**
     * Executes a GET request against the delegation service, attaching a
     * freshly-minted self-signed JWT as the Bearer token.
     */
    private String doGet(String url) {
        // Mint a fresh assertion for every call — tokens are single-use and
        // expire in TOKEN_TTL_SECONDS so caching is not worthwhile.
        String token = accessToken();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            logger.infof("HTTP GET → %s", url);

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new DelegationServiceClientException(
                        "HTTP error " + res.statusCode() + ": " + res.body());
            }

            return res.body();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DelegationServiceClientException(
                    "Delegation service GET interrupted", ie);
        } catch (IOException ioe) {
            throw new DelegationServiceClientException(
                    "Network IO error GETting " + url, ioe);
        } 
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getDelegationContract(String delegationContractId)
            throws DelegationServiceClientException {
        String url = baseUrl + "delegations/" + delegationContractId;
        return doGet(url);
    }
}