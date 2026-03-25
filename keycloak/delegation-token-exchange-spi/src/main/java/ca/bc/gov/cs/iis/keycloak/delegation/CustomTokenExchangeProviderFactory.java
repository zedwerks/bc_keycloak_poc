package ca.bc.gov.cs.iis.keycloak.delegation;

import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.TokenExchangeProvider;
import org.keycloak.protocol.oidc.TokenExchangeProviderFactory;

public final class CustomTokenExchangeProviderFactory implements TokenExchangeProviderFactory {

    @Override
    public TokenExchangeProvider create(KeycloakSession session) {
        return new CustomTokenExchangeProvider();
    }

    @Override
    public String getId() {
        return CustomTokenExchangeProvider.PROVIDER_ID;
    }

    @Override
    public int order() {
        // Ensure we are evaluated before the standard provider.
        // The exact value isn’t important as long as it’s higher than standard.
        return 200;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
    }

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}