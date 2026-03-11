# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "read_finance" {
  realm_id    = keycloak_realm.target.realm
  name        = "fin:cra:taxes:read" # <-- this is the scope you will request
  description = "When requested, we can read the finance data for the subject"
}

resource "keycloak_openid_audience_protocol_mapper" "allow_zone_b_aud" {
  realm_id    = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.read_finance.id
  name                     = "aud-client-b-rs"
  included_client_audience = keycloak_openid_client.client_b_rs.client_id
  add_to_access_token      = true
}