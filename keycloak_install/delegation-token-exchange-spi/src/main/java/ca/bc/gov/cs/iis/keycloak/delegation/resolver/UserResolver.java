package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;

public interface UserResolver {
    String computePairwiseSub(RealmModel realm, ClientModel client, UserModel user);
}