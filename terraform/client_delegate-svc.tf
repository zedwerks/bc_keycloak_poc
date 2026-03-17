resource "keycloak_openid_client" "client_delegate_svc" {
  realm_id                        = keycloak_realm.target.realm
  client_id                       = "client-delegate-svc"
  name                            = "Delegate Service"
  access_type                     = "CONFIDENTIAL"
  client_secret                   = var.client_d_secret
  standard_flow_enabled           = false
  direct_access_grants_enabled    = false
  service_accounts_enabled        = true
  standard_token_exchange_enabled = true

  # REQUIRED
  oauth2_device_authorization_grant_enabled = false
}

resource "keycloak_openid_client_default_scopes" "client_delegate_svc_default_scopes" {
  realm_id       = keycloak_realm.target.realm
  client_id      = keycloak_openid_client.client_delegate_svc.id
  default_scopes = [keycloak_openid_client_scope.delegate_resolve_user_id.id]
  depends_on     = [keycloak_openid_client.client_delegate_svc]
}

resource "keycloak_role" "client_delegate_svc_role" {
  realm_id = keycloak_realm.target.realm
  name     = "iis:delegation:service"
  description = "Role for the delegate service to indicate it can perform delegation-related functions"
}

resource "keycloak_generic_role_mapper" "client_delegate_svc_role_mapping" {
  role_id = keycloak_role.client_delegate_svc_role.id
  realm_id  = keycloak_realm.target.realm
  client_id = keycloak_openid_client.client_delegate_svc.id
}
