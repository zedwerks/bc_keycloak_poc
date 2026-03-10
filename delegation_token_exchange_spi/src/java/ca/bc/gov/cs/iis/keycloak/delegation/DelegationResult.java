package ca.bc.gov.cs.iis.keycloak.delegation;

import java.time.Instant;

public final class DelegationResult {
    private final boolean allowed;
    private final String delegationJson;
    private final Instant expiresAt;

    public DelegationResult(boolean allowed, String delegationJson, Instant expiresAt) {
        this.allowed = allowed;
        this.delegationJson = delegationJson;
        this.expiresAt = expiresAt;
    }

    public boolean isAllowed() { return allowed; }
    public String getDelegationJson() { return delegationJson; }
    public Instant getExpiresAt() { return expiresAt; }
}