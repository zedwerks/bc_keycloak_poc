resource "keycloak_openid_client" "client_delegate_svc" {
  realm_id                        = keycloak_realm.target.realm
  client_id                       = "client-d"
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

