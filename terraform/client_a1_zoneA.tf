resource "keycloak_openid_client" "client_a1" {
  realm_id                        = var.realm_id
  client_id                       = "client-a1"
  name                            = "Client A1 (Zone A)"
  enabled                         = true
  access_type                     = "CONFIDENTIAL"
  client_secret                   = var.client_a1_secret
  standard_flow_enabled           = true
  valid_redirect_uris             = ["https://oauth.pstmn.io/v1/callback", "http://127.0.0.1:3001/callback"]
  web_origins                     = ["https://oauth.pstmn.io"]
  base_url                        = "https://localhost:9001"
  pkce_code_challenge_method      = "S256"
  full_scope_allowed              = false

  oauth2_device_authorization_grant_enabled = false

  extra_config = {
    # Pairwise subject (privacy zone behavior)
    "subject_type" = "pairwise"
    # You’ll set a sector identifier URI per zone.
    # (Exact attribute keys vary by KC version; treat as the intent.)
    "sector.identifier.uri" = "https://sector-a.local:9443/sector-a.json"
  }
}

resource "keycloak_openid_audience_protocol_mapper" "client_a1_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_a1.id
  name                     = "client-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_a1.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_a1]
}

# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_default_scopes" "client_a1_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_a1.id
  default_scopes = ["openid", keycloak_openid_client_scope.scope_ppid_sector_a.name]
  depends_on     = [keycloak_openid_client.client_a1, keycloak_openid_client_scope.scope_ppid_sector_a]
}

resource "keycloak_openid_client_optional_scopes" "client_a1_optional_scopes" {
  realm_id        = keycloak_realm.target.realm
  client_id       = keycloak_openid_client.client_a1.id
  optional_scopes = ["profile", keycloak_openid_client_scope.read_sdpr.name, keycloak_openid_client_scope.read_finance.name]
  depends_on      = [keycloak_openid_client.client_a1, keycloak_openid_client_scope.read_sdpr,
    keycloak_openid_client_scope.read_finance]
}


resource "keycloak_openid_audience_protocol_mapper" "client_a1k_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_a1.id
  name                     = "client-a1-kong-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_kong.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_kong]
}

