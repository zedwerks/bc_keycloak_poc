package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import java.util.Map;
import jakarta.ws.rs.POST;
import ca.bc.gov.cs.iis.keycloak.auth.AuthTokenHelper;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.common.VerificationException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.AccessToken;

public class DelegationUserResolver {

    public static final String ENDPOINT_PATH = "resolve-user";
    public static final String REQUIRED_SCOPE = "iis:delegation:resolve:user";
    public static final String AUTH_HEADER_PREFIX = "Bearer ";
    public static final String PARAM_SUBJECT_TOKEN = "subject_token";
    public static final String RESPONSE_USER_ID_FIELD = "user_id";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    protected static final Logger logger = Logger.getLogger(DelegationUserResolver.class);

    private final KeycloakSession session;

    public DelegationUserResolver(KeycloakSession session) {
        this.session = session;
    }

    @Path(ENDPOINT_PATH)
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resolveUser(
            @HeaderParam(AUTHORIZATION_HEADER_NAME) String authorizationHeader,
            MultivaluedMap<String, String> form) {

        RealmModel realm = session.getContext().getRealm();

        String subjectTokenString = form.getFirst(PARAM_SUBJECT_TOKEN);
        if (subjectTokenString == null) {
            logger.warn("Missing subject_token parameter");
            throw new BadRequestException("Missing subject_token");
        }

        String bearerToken = AuthTokenHelper.extractBearerToken(authorizationHeader);

        String userId;

        try {
            // 1️⃣ Verify caller token
            AccessToken callerToken = AuthTokenHelper.verifyCallerBearerToken(
                    realm,
                    session,
                    bearerToken,
                    REQUIRED_SCOPE);

            // 3️⃣ Verify subject_token
            AccessToken subjectAccessToken = AuthTokenHelper.verifySubjectToken(
                    realm,
                    session,
                    subjectTokenString);

            // 4️⃣ Resolve user id
            userId = subjectAccessToken.getSubject();

            if (userId == null) {
                logger.warn("User not found in subject_token");
                throw new NotFoundException("User not found in subject_token");
            }

        } catch (VerificationException | NotAuthorizedException e) {
            logger.warn("Invalid bearer token", e);
            throw new NotAuthorizedException("Invalid or missing bearer token", e);

        } catch (ForbiddenException e) {
            logger.warn("Caller does not have required role", e);
            throw e;

        } catch (NotFoundException e) {
            logger.warn("User not found", e);
            throw e;

        } catch (Exception e) {
            logger.error("Error resolving user from subject_token", e);
            throw new jakarta.ws.rs.InternalServerErrorException("Internal server error", e);
        }

        Map<String, Object> response = Map.of(RESPONSE_USER_ID_FIELD, userId);
        return Response.ok(response).build();
    }
}
