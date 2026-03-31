# The delegation client scope
resource "keycloak_openid_client_scope" "delegation_scope" {
  realm_id               = var.realm_id
  name                   = "delegation"
  description            = "Delegation token exchange scope - adds act claim to delegated tokens"
  include_in_token_scope = true
}

# Register your custom DelegationActMapper on the scope
resource "keycloak_generic_protocol_mapper" "delegation_act_mapper" {
  realm_id    = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.delegation_scope.id
  name            = "delegation-act-claim"
  protocol        = "openid-connect"
  protocol_mapper = "oidc-delegation-act-claim-protocol-mapper"   # matches Java code: ActClaimProtocolMapper.PROVIDER_ID

  config = {}   # your mapper has no config properties
}
