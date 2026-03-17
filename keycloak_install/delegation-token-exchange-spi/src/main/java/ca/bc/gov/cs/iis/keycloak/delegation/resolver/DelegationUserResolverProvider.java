
/**
 * Copyright 2026 Province of British Columbia
 *
 * 
 * @author Brad Head
 * 
 */

package ca.bc.gov.cs.iis.keycloak.delegation.resolver;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class DelegationUserResolverProvider implements RealmResourceProvider {

    private static final Logger logger = Logger.getLogger(DelegationUserResolverProvider.class);
    private final KeycloakSession session;

    public DelegationUserResolverProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        logger.debug("Creating DelegationUserResolver with session: " + session);
        return new DelegationUserResolver(this.session);
    }

    @Override
    public void close() {
    }

}