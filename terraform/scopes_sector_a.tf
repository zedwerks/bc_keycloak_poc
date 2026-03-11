# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "read_sdpr" {
  realm_id    = keycloak_realm.target.realm
  name        = "sdpr:myss:case:read" # <-- this is the scope you will request
  description = "When requested, we can read the finance data for the subject"
}

resource "keycloak_openid_audience_protocol_mapper" "allow_zone_a_aud" {
  realm_id    = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.read_sdpr.id
  name                     = "aud-client-a-rs"
  included_client_audience = keycloak_openid_client.client_a_rs.client_id
  add_to_access_token      = true
}
