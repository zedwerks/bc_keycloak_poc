# --- Client scope that will carry the Pairwise subject identifier mapper
resource "keycloak_openid_client_scope" "scope_ppid_sector_b" {
  realm_id               = var.realm_id
  name                   = "ppid-sector-b"
  include_in_token_scope = false # we don't want this scope to be requested directly, or viewable in the token
  description            = "Pairwise subject identifier scope for privacy sector B"
}

# --- Attach the built-in Pairwise subject identifier mapper to that scope
# Use your provider's generic protocol-mapper resource. The exact type name
# can vary by distribution; many expose one called "keycloak_generic_protocol_mapper".
resource "keycloak_generic_protocol_mapper" "ppid_sub_b" {
  realm_id        = keycloak_realm.target.realm
  client_scope_id = keycloak_openid_client_scope.scope_ppid_sector_b.id

  name            = "pairwise-sub-sector-b"
  protocol        = "openid-connect"
  protocol_mapper = "oidc-sha256-pairwise-sub-mapper"

  config = {
    "access.token.claim"       = "true"
    "id.token.claim"           = "true"
    "userinfo.token.claim"     = "true"
    "pairwiseSubAlgorithmSalt" = "8d3ee108-0f94-4b97-aa79-76e3401ce927",
    "sectorIdentifierUri"      = "https://example.com/sector-b.json" # <-- must be HTTPS
  }
}
