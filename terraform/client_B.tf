resource "keycloak_openid_client" "client_b" {
  realm_id                     = data.keycloak_realm.realm.id
  client_id                    = "client-b"
  name                         = "Client B (RS)"
  enabled                      = true
  access_type                  = "CONFIDENTIAL"
  client_authenticator_type    = "client-secret"
  direct_access_grants_enabled = false
  implicit_flow_enabled        = false
  standard_flow_enabled        = true
  valid_redirect_uris             = ["https://oauth.pstmn.io/v1/callback", "https://oauth.pstmn.io/v1/browser-callback"]
  base_url                     = "https://localhost:9000"
  web_origins                  = []
  service_accounts_enabled     = true
  full_scope_allowed           = false

  standard_token_exchange_enabled = true

  extra_config = {
    subjectType                  = "pairwise"
    # Uncomment the next line if you want to use sector identifier URI
    #sectorIdentifierUri = "http://localhost:9000/zone2.json"
  }
}

resource "keycloak_openid_client_default_scopes" "client_b_default_scopes" {
  realm_id = data.keycloak_realm.realm.id
  client_id = keycloak_openid_client.client_b.id
  default_scopes = [ "profile", keycloak_openid_client_scope.ppid.name]
  depends_on = [keycloak_openid_client.client_b]
}

