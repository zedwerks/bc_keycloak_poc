package ca.bc.gov.cs.iis.keycloak.delegation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class DelegationContract {

    @JsonProperty("id")
    private String id;

    @JsonProperty("delegator")
    private Delegator delegatorParty;

    @JsonProperty("delegate")
    private Delegate delegateParty;

    @JsonProperty("scope")
    private List<String> scope;

    @JsonProperty("expires_at")
    private long expiresAt;

    // Required by Jackson
    public DelegationContract() {
    }

    public String getId() {
        return id;
    }

    public Delegator getDelegator() {
        return delegatorParty;
    }

    public Delegate getDelegate() {
        return delegateParty;
    }

    public List<String> getScope() {
        return scope;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDelegator(Delegator delegator) {
        this.delegatorParty = delegator;
    }

    public void setDelegate(Delegate delegate) {
        this.delegateParty = delegate;
    }

    public void setScope(List<String> scope) {
        this.scope = scope;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public static DelegationContract fromJsonString(String json) {

        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("delegation JSON is missing");
        }

        try {
            DelegationContract ctx = MAPPER.readValue(json, DelegationContract.class);

            // 🔐 minimal validation
            if (ctx.getDelegate() == null || ctx.getDelegator() == null) {
                throw new IllegalArgumentException("Missing required delegation fields");
            }

            if (ctx.getExpiresAt() != 0 &&
                    ctx.getExpiresAt() < (System.currentTimeMillis() / 1000)) {
                throw new IllegalArgumentException("Delegation context is expired");
            }

            return ctx;

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid delegation JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static DelegationContract fromBase64Url(String encoded) {

        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("delegation_context is missing");
        }

        final byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64url encoding", e);
        }

        try {
            String json = new String(decoded, StandardCharsets.UTF_8);

            DelegationContract ctx = MAPPER.readValue(json, DelegationContract.class);

            // 🔐 minimal validation (you can expand this)
            if (ctx.getDelegate() == null || ctx.getDelegator() == null) {
                throw new IllegalArgumentException("Missing required delegation fields");
            }

            if (ctx.getExpiresAt() != 0 && ctx.getExpiresAt() < (System.currentTimeMillis() / 1000)) {
                throw new IllegalArgumentException("Delegation context is expired");
            }

            return ctx;

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid delegation JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static String toJsonString(DelegationContract context) {
        try {
            return new ObjectMapper().writeValueAsString(context);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize delegation context", e);
        }
    }

    public static class Delegator {
        @JsonProperty("user_id")
        private String userId;

        public Delegator() {
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    public static class Delegate extends Delegator {

        @JsonProperty("service_account_client_id")
        private String serviceAccountClientId;

        public String getServiceAccountClientId() {
            return serviceAccountClientId;
        }

        public Delegate() {
            super();
        }
    }

}