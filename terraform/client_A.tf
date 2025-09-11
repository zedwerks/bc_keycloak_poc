resource "keycloak_openid_client" "client_a" {
  realm_id                        = data.keycloak_realm.realm.id
  client_id                       = "client-a"
  name                            = "Client A"
  enabled                         = true
  access_type                     = "CONFIDENTIAL"
  client_secret                   = "RNrsYos1urQ4DqNTr40NILKCLWEQgfJL"
  standard_flow_enabled           = true
  valid_redirect_uris             = ["https://oauth.pstmn.io/v1/callback"]
  web_origins                     = ["https://oauth.pstmn.io"]
  base_url                        = "https://localhost:9001"
  pkce_code_challenge_method      = "S256"
  full_scope_allowed              = false
  standard_token_exchange_enabled = true
}

resource "keycloak_openid_audience_protocol_mapper" "client_a_audience_mapper" {
  realm_id                 = data.keycloak_realm.realm.id
  client_id                = keycloak_openid_client.client_a.id
  name                     = "client-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_a.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_a]
}

# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "allow_client_b" {
  realm_id    = data.keycloak_realm.realm.id
  name        = "allow:client-b" # <-- this is the scope you will request
  description = "When requested, add client-b to 'aud'"
}

resource "keycloak_openid_client_default_scopes" "client_a_default_scopes" {
  realm_id       = data.keycloak_realm.realm.id
  client_id      = keycloak_openid_client.client_a.id
  default_scopes = [keycloak_openid_client_scope.scope_ppid_sector_a.name]
  depends_on     = [keycloak_openid_client.client_a, keycloak_openid_client_scope.scope_ppid_sector_a]
}

resource "keycloak_openid_client_optional_scopes" "client_a_optional_scopes" {
  realm_id        = data.keycloak_realm.realm.id
  client_id       = keycloak_openid_client.client_a.id
  optional_scopes = ["profile", keycloak_openid_client_scope.allow_client_b.name]
  depends_on      = [keycloak_openid_client.client_a]
}

# --- Audience protocol mapper attached to the client scope ---
# Adds client-b to the 'aud' claim whenever scope=allow:client-b is present.
/*resource "keycloak_openid_audience_protocol_mapper" "aud_client_b" {
  realm_id        = data.keycloak_realm.realm.id
  client_scope_id = keycloak_openid_client_scope.allow_client_b.id

  name                     = "client-b"
  included_client_audience = keycloak_openid_client.client_b.client_id # "client-b"

  # choose which tokens to affect:
  add_to_access_token = true
  add_to_id_token     = false

}*/

resource "keycloak_generic_protocol_mapper" "aud_client_b" {
  realm_id        = data.keycloak_realm.realm.id
  client_scope_id = keycloak_openid_client_scope.allow_client_b.id
  name            = "client-b-audience-maapper"
  protocol        = "openid-connect"
  protocol_mapper = "oidc-audience-mapper"
  config = {
    "included.client.audience"       = keycloak_openid_client.client_b.client_id
    "introspection.token.claim"      = "true"
    "userinfo.token.claim"           = "false"
    "id.token.claim"                 = "false"
    "access.token.claim"             = "true"
    "access.tokenResponse.claim"     = "false"
    "lightweight.access.token.claim" = "true"
  }
  depends_on = [keycloak_openid_client_scope.allow_client_b]
}

