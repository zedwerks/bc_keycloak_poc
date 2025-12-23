resource "keycloak_user_roles" "user_can_exchange" {
  realm_id = var.realm_id
  user_id  = keycloak_user.test_user_alice.id

  role_ids = [
    keycloak_role.token_exchange.id
  ]

  depends_on = [
    keycloak_role.token_exchange
  ]
}