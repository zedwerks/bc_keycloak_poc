package ca.bc.gov.cs.iis.keycloak.delegation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonFormat;

public class ActClaim {
    @JsonProperty("sub")
    private String sub;

    @JsonProperty("original_client_id")
    private String clientId;

    public ActClaim() {
    }

    public ActClaim(String sub) {
        this.sub = sub;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getClientId() {
        return clientId;
    }
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public static String toJsonString(ActClaim actClaim) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(actClaim);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ActClaim to JSON", e);
        }
    }
}