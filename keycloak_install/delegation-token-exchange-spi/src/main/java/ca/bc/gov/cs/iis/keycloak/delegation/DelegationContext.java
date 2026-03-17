package ca.bc.gov.cs.iis.keycloak.delegation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class DelegationContext {

    @JsonProperty("id")
    private String id;

    @JsonProperty("delegator_user_id")
    private String delegatorUserId;

    @JsonProperty("delegate_user_id")
    private String delegateUserId;

    @JsonProperty("scope")
    private List<String> scope;

    @JsonProperty("expires_at")
    private long expiresAt;

    // Required by Jackson
    public DelegationContext() {
    }

    public DelegationContext(String id, String delegatorUserId, String delegateUserId, List<String> scope, long expiresAt) {
        this.id = id;
        this.delegatorUserId = delegatorUserId;
        this.delegateUserId = delegateUserId;
        this.scope = scope;
        this.expiresAt = expiresAt;
    }


    public String getId() {
        return id;
    }

    public String getDelegatorUserId() {
        return delegatorUserId;
    }

    public String getDelegateUserId() {
        return delegateUserId;
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

    public void setDelegatorUserId(String delegatorUserId) {
        this.delegatorUserId = delegatorUserId;
    }

    public void setDelegateUserId(String delegateUserId) {
        this.delegateUserId = delegateUserId;
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

public static DelegationContext fromBase64Url(String encoded) {

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

        DelegationContext ctx = MAPPER.readValue(json, DelegationContext.class);

        // 🔐 minimal validation (you can expand this)
        if (ctx.getDelegateUserId() == null || ctx.getDelegatorUserId() == null) {
            throw new IllegalArgumentException("Missing required delegation fields");
        }

        if (ctx.getExpiresAt() != 0 && ctx.getExpiresAt() < (System.currentTimeMillis() / 1000)) {
            throw new IllegalArgumentException("Delegation context is expired");
        }

        return ctx;

    } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            "Invalid delegation JSON: " + e.getOriginalMessage(), e
        );
    }
}

    public static String toJsonString(DelegationContext context) {
        try {
            return new ObjectMapper().writeValueAsString(context);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize delegation context", e);
        }
    }
}