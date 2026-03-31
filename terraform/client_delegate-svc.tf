resource "keycloak_openid_client" "client_delegate_svc" {
  realm_id                        = keycloak_realm.target.realm
  client_id                       = "delegation-svc"
  name                            = "Delegate Service"
  access_type                     = "CONFIDENTIAL"
  client_secret                   = var.client_d_secret
  standard_flow_enabled           = false
  direct_access_grants_enabled    = false
  service_accounts_enabled        = true
  standard_token_exchange_enabled = true
  full_scope_allowed = false

  # REQUIRED
  oauth2_device_authorization_grant_enabled = false
}

resource "keycloak_openid_client_default_scopes" "client_delegate_svc_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_delegate_svc.id
  default_scopes = ["acr"]
  depends_on     = [keycloak_openid_client.client_delegate_svc]
}

resource "keycloak_openid_client_optional_scopes" "client_delegate_svc_optional_scopes" {
  realm_id  = keycloak_realm.target.realm
  client_id = keycloak_openid_client.client_delegate_svc.id
  optional_scopes = ["profile", "email",
    keycloak_openid_client_scope.delegate_resolve_user_id.name,
    keycloak_openid_client_scope.fin_cra_taxes_read.name,
    keycloak_openid_client_scope.sdpr_myss_case_read.name,
    keycloak_openid_client_scope.scope_ppid_sector_a.name,
    keycloak_openid_client_scope.scope_ppid_sector_b.name]
  depends_on = [keycloak_openid_client.client_delegate_svc,
    keycloak_openid_client_scope.delegate_resolve_user_id,
    keycloak_openid_client_scope.fin_cra_taxes_read,
    keycloak_openid_client_scope.sdpr_myss_case_read,
    keycloak_openid_client_scope.scope_ppid_sector_a,
    keycloak_openid_client_scope.scope_ppid_sector_b]
}

