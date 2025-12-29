# --- Client scope that will carry the Pairwise subject identifier mapper
resource "keycloak_openid_client_scope" "scope_ppid_sector_a" {
  realm_id               = var.realm_id
  name                   = "ppid-sector-a"
  include_in_token_scope = false # we don't want this scope to be requested directly, or viewable in the token
  description            = "Pairwise subject identifier scope for privacy sector A"
}

# --- Attach the built-in Pairwise subject identifier mapper to that scope
# Use your provider's generic protocol-mapper resource. The exact type name
# can vary by distribution; many expose one called "keycloak_generic_protocol_mapper".
resource "keycloak_generic_protocol_mapper" "ppid_sub_a" {
  realm_id        = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.scope_ppid_sector_a.id

  name            = "pairwise-sub-sector-a"
  protocol        = "openid-connect"
  protocol_mapper = "oidc-sha256-pairwise-sub-mapper"

  config = {
    "access.token.claim"       = "true"
    "lightweight.token.claim"  = "true"
    "id.token.claim"           = "true"
    "userinfo.token.claim"     = "true"
    "pairwiseSubAlgorithmSalt" = "4575bda8-40eb-4b06-9f9c-92e3dceb2eb7"
    "sectorIdentifierUri"      = "https://sector-a.local:9443/sector-a.json" # <-- must be HTTPS
  }
}
