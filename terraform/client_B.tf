resource "keycloak_openid_client" "client_b" {
  realm_id                     = var.realm_id
  client_id                    = "client-b"
  name                         = "Client B (RS)"
  enabled                      = true
  access_type                  = "CONFIDENTIAL"
  client_authenticator_type    = "client-secret"
  client_secret                = "wmug4dloTqA24lniq9dK5EVSxVhhFtZE"
  direct_access_grants_enabled = false
  implicit_flow_enabled        = false
  standard_flow_enabled        = true
  valid_redirect_uris          = ["https://oauth.pstmn.io/v1/browser-callback", "http://127.0.0.1:3002/callback"]
  base_url                     = "https://localhost:8888"
  web_origins                  = []
  service_accounts_enabled     = true
  full_scope_allowed           = false
  standard_token_exchange_enabled = true
}

resource "keycloak_openid_client_default_scopes" "client_b_default_scopes" {
  realm_id       = var.realm_id
  client_id      = keycloak_openid_client.client_b.id
  default_scopes = [keycloak_openid_client_scope.scope_ppid_sector_b.name]
  depends_on     = [keycloak_openid_client.client_b, keycloak_openid_client_scope.scope_ppid_sector_b]
}

resource "keycloak_openid_client_optional_scopes" "client_b_optional_scopes" {
  realm_id        = var.realm_id
  client_id       = keycloak_openid_client.client_b.id
  optional_scopes = ["profile", keycloak_openid_client_scope.read_finance.name]
  depends_on      = [keycloak_openid_client.client_b, keycloak_openid_client_scope.read_finance]
}

resource "keycloak_openid_audience_protocol_mapper" "client_b_audience_mapper" {
  realm_id                 = var.realm_id
  client_id                = keycloak_openid_client.client_b.id
  name                     = "client-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_b.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_b]
}
