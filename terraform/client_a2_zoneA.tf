resource "keycloak_openid_client" "client_a2" {
  realm_id                   = keycloak_realm.target.realm
  client_id                  = "client-a2"
  name                       = "Client A2 (Zone A)"
  enabled                    = true
  access_type                = "CONFIDENTIAL"
  client_secret              = var.client_a2_secret
  standard_flow_enabled      = true
  valid_redirect_uris        = ["https://oauth.pstmn.io/v1/callback", "https://127.0.0.1:3001/callback"]
  web_origins                = ["https://oauth.pstmn.io"]
  base_url                   = "https://localhost:9001"
  pkce_code_challenge_method = "S256"
  full_scope_allowed         = false

  oauth2_device_authorization_grant_enabled = false

  extra_config = {
    # Pairwise subject (privacy zone behavior)
    "subject_type" = "pairwise"
    # You’ll set a sector identifier URI per zone.
    # (Exact attribute keys vary by KC version; treat as the intent.)
    "sector.identifier.uri" = "https://sector-a.local:9443/sector-a.json"
  }
}

resource "keycloak_openid_audience_protocol_mapper" "client_a2_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_a2.id
  name                     = "client-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_a2.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_a2]
}

# --- Client Scope that signals permission to add audience ---

resource "keycloak_openid_client_default_scopes" "client_a2_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_a2.id
  default_scopes = [keycloak_openid_client_scope.scope_ppid_sector_a.name]
  depends_on     = [keycloak_openid_client.client_a2, keycloak_openid_client_scope.scope_ppid_sector_a]
}

resource "keycloak_openid_client_optional_scopes" "client_a2_optional_scopes" {
  realm_id  = keycloak_realm.target.realm
  client_id = keycloak_openid_client.client_a2.id
  optional_scopes = ["profile",
    keycloak_openid_client_scope.sdpr_myss_case_read.name,
  keycloak_openid_client_scope.fin_cra_taxes_read.name]
  depends_on = [keycloak_openid_client.client_a2,
    keycloak_openid_client_scope.sdpr_myss_case_read,
  keycloak_openid_client_scope.fin_cra_taxes_read]
}

resource "keycloak_openid_audience_protocol_mapper" "client_a2k_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_a2.id
  name                     = "client-a2-kong-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_kong.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_kong]
}
