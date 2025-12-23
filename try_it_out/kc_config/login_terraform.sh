export KC_HOST="http://localhost:8080"
export REALM="poc"
export CLIENT_ID=$TF_VAR_keycloak_KEYCLOAK_TERRAFORM_CLIENT_ID
export SECRET=$TF_VAR_keycloak_KEYCLOAK_TERRAFORM_CLIENT_SECRET

export ADMIN_TOKEN=$(curl -s -X POST "$KC_HOST/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$SECRET" \
  -d "grant_type=client_credentials" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  | jq -r '.access_token')


