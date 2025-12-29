resource "keycloak_openid_client" "client_kong" {
  realm_id                        = keycloak_realm.target.realm
  client_id                       = "client-kong"
  name                            = "Kong Token Exchanger (service account)"
  access_type                     = "CONFIDENTIAL"
  client_secret                   = var.client_k_secret
  standard_flow_enabled           = false
  direct_access_grants_enabled    = false
  service_accounts_enabled        = true
  standard_token_exchange_enabled = true

  # REQUIRED
  oauth2_device_authorization_grant_enabled = false

}

resource "keycloak_openid_client_default_scopes" "client_kong_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_kong.id
  default_scopes = ["openid", keycloak_openid_client_scope.scope_ppid_sector_b.name]
  depends_on     = [keycloak_openid_client_scope.scope_ppid_sector_b]
}

resource "keycloak_openid_client_optional_scopes" "client_kong_optional_scopes" {
  realm_id        = keycloak_realm.target.realm
  client_id       = keycloak_openid_client.client_kong.id
  optional_scopes = ["profile", keycloak_openid_client_scope.read_finance.name, keycloak_openid_client_scope.read_sdpr.name]
  depends_on      = [keycloak_openid_client.client_kong, 
    keycloak_openid_client_scope.read_finance,
    keycloak_openid_client_scope.read_sdpr]
}


/**
 * Need a mapper for each target audience otherwise Kong cannot mint a token
 * for the desired audience. The desired audience arrives in the source token
 * as an audience that is not native to the source privacy zone.
 */

resource "keycloak_openid_audience_protocol_mapper" "kong_client_b_rs_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_kong.id
  name                     = "client-b-rs-kong-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_b_rs.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_b_rs]
}

resource "keycloak_openid_audience_protocol_mapper" "kong_client_a1_audience_mapper" {
  realm_id                 = keycloak_realm.target.realm
  client_id                = keycloak_openid_client.client_kong.id
  name                     = "client-a1-kong-audience-mapper"
  included_custom_audience = keycloak_openid_client.client_a1.client_id
  add_to_access_token      = true
  add_to_id_token          = true
  depends_on               = [keycloak_openid_client.client_a1]
}