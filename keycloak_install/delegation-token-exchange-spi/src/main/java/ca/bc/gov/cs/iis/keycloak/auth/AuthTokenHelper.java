package ca.bc.gov.cs.iis.keycloak.auth;

import jakarta.ws.rs.core.Response;

import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.exceptions.TokenNotActiveException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.OAuthErrorException;

public class AuthTokenHelper {

    /**
     * Extract Bearer token from Authorization header
     */
    public static String extractBearerToken(String authorizationHeader) throws OAuthErrorException {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new OAuthErrorException(
                    OAuthErrorException.INVALID_REQUEST,
                    "Missing or invalid Authorization header");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    /**
     * Verify caller bearer token (delegation service)
     */
    public static AccessToken verifyCallerBearerToken(
            RealmModel realm,
            KeycloakSession session,
            String tokenString,
            String requiredRole) throws OAuthErrorException {

        AccessToken token = verifyAccessToken(session, tokenString);

        if (requiredRole != null &&
                (token.getRealmAccess() == null ||
                        !token.getRealmAccess().isUserInRole(requiredRole))) {

            throw new OAuthErrorException(
                    OAuthErrorException.ACCESS_DENIED,
                    "Caller is missing required role.");
        }

        return token;
    }

    /**
     * Verify subject token (preferred signature)
     */
    public static AccessToken verifySubjectToken(
            KeycloakSession session,
            String tokenString) throws OAuthErrorException {

        if (tokenString == null || tokenString.isBlank()) {
            throw new OAuthErrorException(
                    OAuthErrorException.INVALID_REQUEST,
                    "subject_token is missing");
        }

        try {
            return verifyAccessToken(session, tokenString);

        } catch (Exception e) {
            throw new OAuthErrorException(
                    OAuthErrorException.SERVER_ERROR,
                    "Failed to process subject_token");
        }
    }

    /**
     * Backwards-compatible method (keeps your existing resolver working)
     */
    public static AccessToken verifySubjectToken(
            RealmModel realm,
            KeycloakSession session,
            String tokenString) throws OAuthErrorException {

        return verifySubjectToken(session, tokenString);
    }

    /**
     * Core verification using Keycloak internals
     */
    private static AccessToken verifyAccessToken(
            KeycloakSession session,
            String tokenString) throws OAuthErrorException {

        return session.tokens().decode(tokenString, AccessToken.class);
    }
}