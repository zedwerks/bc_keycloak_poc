package ca.bc.gov.cs.iis.keycloak.delegation;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.oidc.TokenExchangeProvider;
import org.keycloak.protocol.oidc.TokenExchangeProviderFactory;

public final class DelegationTokenExchangeProviderFactory implements TokenExchangeProviderFactory {

    public static final String PROVIDER_ID = "delegation-token-exchange";

    @Override
    public TokenExchangeProvider create(KeycloakSession session) {
        // @TODO: build a real HTTP client + config
        DelegationServiceClient client = new SimpleDelegationServiceClient(session);
        return new DelegationTokenExchangeProvider(session, client);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public int order() {
        // Ensure we are evaluated before the standard provider.
        // The exact value isn’t important as long as it’s higher than standard.
        return 200;
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}
}