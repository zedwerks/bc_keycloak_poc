/**
 * Copyright 2026 Province of British Columbia
 * 
 * @author brad.head@gov.bc.ca
 * 
 */

package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class DelegationUserResolverProviderFactory implements RealmResourceProviderFactory {

    public static final String ID = "iis-delegation-user-resolver";
    private static final Logger logger = Logger.getLogger(DelegationUserResolverProviderFactory.class);

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new DelegationUserResolverProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        logger.info("🟢 IIS [DelegationUserResolverProviderFactory] successfully initalized.");
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
