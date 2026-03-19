resource "keycloak_openid_client_scope" "delegation_scope" {
  realm_id = keycloak_realm.target.realm
  name     = "delegation"
}

resource "keycloak_openid_user_session_note_protocol_mapper" "act_claim" {
  realm_id = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.delegation_scope.id
  name = "act-claim-mapper"
  claim_name = "act"
  claim_value_type = "JSON"
  session_note ="delegation-act-claim"

  # 👇 Where it appears
  add_to_access_token = true
  add_to_id_token     = false
  depends_on = [ keycloak_openid_client_scope.delegation_scope ]
}
