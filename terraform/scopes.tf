# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "delegate_resolve_user_id" {
  realm_id    = keycloak_realm.target.realm
  name        = "iss:delegate:resolve:user" # <-- this is the scope you will request
  description = "When requested, we can resolve the user ID for delegation"
}