# --- Client Scope that signals permission to add audience ---
resource "keycloak_openid_client_scope" "delegation_act" {
  realm_id    = keycloak_realm.target.realm
  name        = "delegation" # <-- this is the scope you will request
  description = "When requested, we can perform delegation actions for the subject"
}

resource "keycloak_generic_protocol_mapper" "scope_bound_delegation_act_mapper" {
  realm_id        = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.delegation_act.id

  name            = "delegation-act-mapper"
  protocol        = "openid-connect"
  protocol_mapper = "delegation-act-mapper" # matches Java Code for PROVIDER_ID

  config = {
    "access.token.claim"       = "true"
    "lightweight.token.claim"  = "true"
    "id.token.claim"           = "true"
    "userinfo.token.claim"     = "true"
  }
}

