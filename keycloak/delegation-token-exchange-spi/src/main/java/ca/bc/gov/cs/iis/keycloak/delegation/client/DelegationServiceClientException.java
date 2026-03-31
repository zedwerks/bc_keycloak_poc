package ca.bc.gov.cs.iis.keycloak.delegation.client;

public class DelegationServiceClientException extends RuntimeException {
    public DelegationServiceClientException(String message) {
        super(message);
    }

    public DelegationServiceClientException(String message, Throwable cause) {
        super(message, cause);
    }

}