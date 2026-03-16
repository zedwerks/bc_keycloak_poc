package ca.bc.gov.cs.iis.keycloak.auth;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;

import java.util.Objects;

public final class AuthTokenHelper {

    private AuthTokenHelper() {
    }

    /**
     * Base verification for any realm-issued access token.
     */
    public static AccessToken verifyAccessToken(
            RealmModel realm,
            KeycloakSession session,
            String token
    ) throws VerificationException {

        if (token == null || token.isBlank()) {
            throw new VerificationException("Missing token");
        }

        AccessToken accessToken = TokenVerifier.create(token, AccessToken.class)
                .withChecks(
                        TokenVerifier.IS_ACTIVE,
                        TokenVerifier.SUBJECT_EXISTS_CHECK
                )
                .verify()
                .getToken();

        String expectedIssuer = session.getContext().getUri().getBaseUriBuilder()
                .path("realms")
                .path(realm.getName())
                .build()
                .toString();

        if (!Objects.equals(expectedIssuer, accessToken.getIssuer())) {
            throw new VerificationException("Invalid token issuer: " + accessToken.getIssuer());
        }

        return accessToken;
    }

    /**
     * Verifies the Authorization header bearer token.
     * This token represents the delegation service itself.
     */
    public static AccessToken verifyCallerBearerToken(
            RealmModel realm,
            KeycloakSession session,
            String bearerToken,
            String requiredScope
    ) throws VerificationException {

        AccessToken token = verifyAccessToken(realm, session, bearerToken);

        if (requiredScope != null && !requiredScope.isBlank()) {
            requireScope(token, requiredScope);
        }

        return token;
    }

    /**
     * Verifies the subject_token from the POST body.
     * This token represents the subject user whose KC user id you want to resolve.
     */
    public static AccessToken verifySubjectToken(
            RealmModel realm,
            KeycloakSession session,
            String subjectToken
    ) throws VerificationException {

        return verifyAccessToken(realm, session, subjectToken);
    }

    public static void requireScope(AccessToken token, String requiredScope) {
        String scope = token.getScope();
        if (scope == null || scope.isBlank()) {
            throw new ForbiddenException("Missing required scope: " + requiredScope);
        }

        boolean found = false;
        for (String s : scope.split("\\s+")) {
            if (requiredScope.equals(s)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ForbiddenException("Missing required scope: " + requiredScope);
        }
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new NotAuthorizedException("Missing Authorization header");
        }

        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new NotAuthorizedException("Authorization header must use Bearer scheme");
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new NotAuthorizedException("Missing bearer token");
        }

        return token;
    }
}