
data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.target.realm
  client_id = "realm-management"
}

resource "keycloak_role" "token_exchange" {
  realm_id    = keycloak_realm.target.realm
  client_id   = data.keycloak_openid_client.realm_management.id
  name        = "token-exchange"
  description = "Allows OAuth 2.0 Token Exchange"
}

