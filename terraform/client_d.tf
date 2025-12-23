resource "keycloak_openid_client" "delegate_client" {
  realm_id                     = keycloak_realm.target.realm
  client_id                    = "client-d"
  name                         = "Delegate Client"
  access_type                  = "CONFIDENTIAL"
  client_secret                = var.delegate_client_secret
  standard_flow_enabled        = false
  direct_access_grants_enabled = true
  service_accounts_enabled     = true

  # REQUIRED
  oauth2_device_authorization_grant_enabled = false
}