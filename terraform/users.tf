resource "keycloak_user" "test_user_alice" {
  realm_id       = data.keycloak_realm.realm.id
  username       = "alice"
  enabled        = true
  first_name     = "Alice"
  last_name      = "Smith"
  email          = "brad@zedwerks.com"
  email_verified = true
  initial_password {
    value     = "password"
    temporary = false
  }
}
