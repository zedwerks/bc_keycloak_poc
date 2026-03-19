resource "keycloak_openid_client" "client_b" {
  realm_id                     = keycloak_realm.target.realm
  client_id                    = "client-b"
  name                         = "Client B (User app))"
  enabled                      = true
  access_type                  = "PUBLIC"
  direct_access_grants_enabled = false
  implicit_flow_enabled        = false
  standard_flow_enabled        = true
  service_accounts_enabled     = false
  valid_redirect_uris          = ["https://127.0.0.1:3001/callback"]
  base_url                     = "https://localhost:8888"
  web_origins                  = []
  full_scope_allowed           = false

  oauth2_device_authorization_grant_enabled = false

  extra_config = {
    # Pairwise subject (privacy zone behavior)
    "subject_type" = "pairwise"
    # You’ll set a sector identifier URI per zone.
    # (Exact attribute keys vary by KC version; treat as the intent.)
    "sector.identifier.uri" = "https://sector-b.local:9443/sector-b.json"
  }
}

resource "keycloak_openid_client_default_scopes" "client_b_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_b.id
  default_scopes = [keycloak_openid_client_scope.scope_ppid_sector_b.name]
  depends_on     = [keycloak_openid_client.client_b, keycloak_openid_client_scope.scope_ppid_sector_b]
}

resource "keycloak_openid_client_optional_scopes" "client_b_optional_scopes" {
  realm_id  = keycloak_realm.target.realm
  client_id = keycloak_openid_client.client_b.id
  optional_scopes = ["profile", keycloak_openid_client_scope.fin_cra_taxes_read.name,
  keycloak_openid_client_scope.sdpr_myss_case_read.name]
  depends_on = [keycloak_openid_client.client_b,
    keycloak_openid_client_scope.fin_cra_taxes_read,
  keycloak_openid_client_scope.sdpr_myss_case_read]
}

/* Need this to allow Kong to perform token exchange. Kong must me an audience of the
 * token to be exchanged.
 */
resource "keycloak_openid_audience_protocol_mapper" "client_b_kong_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_b.id
  name                     = "client-b-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_kong.client_id
  add_to_access_token      = true
  add_to_id_token          = false
  depends_on               = [keycloak_openid_client.client_kong]
}

/* Need this to allow Delegate Service to perform token exchange. Delegate Service must be an audience of the
 * token to be exchanged.
 */
resource "keycloak_openid_audience_protocol_mapper" "client_b_delegate_svc_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_b.id
  name                     = "client-b-delegate-svc-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_delegate_svc.client_id
  add_to_access_token      = true
  add_to_id_token          = false
  depends_on               = [keycloak_openid_client.client_delegate_svc]
}
