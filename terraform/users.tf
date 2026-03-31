resource "keycloak_user" "test_user_amina" {
  realm_id       = keycloak_realm.target.realm
  username       = "amina"
  enabled        = true
  first_name     = "Amina"
  last_name      = "Smith"
  email          = "amina@hotmail.com"
  email_verified = true
  initial_password {
    value     = "password"
    temporary = false
  }
}

resource "keycloak_user" "test_user_joe" {
  realm_id       = keycloak_realm.target.realm
  username       = "joe"
  enabled        = true
  first_name     = "Joe"
  last_name      = "Smith"
  email          = "joe@gmail.com"
  email_verified = true
  initial_password {
    value     = "password"
    temporary = false
  }
}
