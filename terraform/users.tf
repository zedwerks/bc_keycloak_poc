resource "keycloak_user" "test_user_alice" {
  realm_id       = keycloak_realm.target.realm
  username       = "alice"
  enabled        = true
  first_name     = "Alice"
  last_name      = "Smith"
  email          = "alice@hotmail.com"
  email_verified = true
  initial_password {
    value     = "password"
    temporary = false
  }
}

resource "keycloak_user" "test_user_amina" {
  realm_id       = keycloak_realm.target.realm
  username       = "amina"
  enabled        = true
  first_name     = "Amina"
  last_name      = "SDPR"
  email          = "amina@gmail.com"
  email_verified = true
  initial_password {
    value     = "password"
    temporary = false
  }
}
