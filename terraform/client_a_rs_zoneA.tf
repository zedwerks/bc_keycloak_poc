resource "keycloak_openid_client" "client_a_rs" {
  realm_id                        = keycloak_realm.target.realm
  client_id                       = "client-a-rs"
  name                            = "Client A (Resource Server APIs)"
  enabled                         = true
  access_type                     = "CONFIDENTIAL"
  client_secret                   = var.client_a_rs_secret
  direct_access_grants_enabled    = false
  implicit_flow_enabled           = false
  standard_flow_enabled           = false
  service_accounts_enabled        = true
  full_scope_allowed              = false

  oauth2_device_authorization_grant_enabled = false

  extra_config = {
    # Pairwise subject (privacy zone behavior)
    "subject_type" = "pairwise"
    # You’ll set a sector identifier URI per zone.
    # (Exact attribute keys vary by KC version; treat as the intent.)
    "sector.identifier.uri" = "https://sector-b.local:9443/sector-a.json"
  }
}

resource "keycloak_openid_client_default_scopes" "client_a_rs_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_a_rs.id
  default_scopes = ["openid", keycloak_openid_client_scope.scope_ppid_sector_a.name]
  depends_on     = [keycloak_openid_client.client_a_rs, keycloak_openid_client_scope.scope_ppid_sector_a]
}

resource "keycloak_openid_client_optional_scopes" "client_a_rs_optional_scopes" {
  realm_id        = keycloak_realm.target.realm
  client_id       = keycloak_openid_client.client_b_rs.id
  optional_scopes = ["profile"]
  depends_on      = [keycloak_openid_client.client_a_rs]
}
