# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "read_finance" {
  realm_id    = data.keycloak_realm.realm.id
  name        = "read:finance" # <-- this is the scope you will request
  description = "When requested, we can read the finance data for the subject"
}