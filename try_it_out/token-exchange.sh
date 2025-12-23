#!/bin/bash
# token-exchange.sh
# This script performs a token exchange from client-a to client-b using the access token obtained from
# client-a's sign-on process. It uses Curl to handle the token exchange.
REALM=$TF_VAR_realm_id
ISSUER_BASE="http://localhost:8080/realms/$REALM"            # your realm issuer base
TOKEN_ENDPOINT="$ISSUER_BASE/protocol/openid-connect/token"
CLIENT_ID="client-b" 
CLIENT_SECRET="wmug4dloTqA24lniq9dK5EVSxVhhFtZE" 

oauth-tool exchange \
  --token-endpoint "$TOKEN_ENPOINT" \
  --client-id "$CLIENT_ID" \
  --client-secret "$CLIENT_SECRET" \
  --subject-token "$(cat tokens/access-token.jwt)" \
  --subject-token-type "urn:ietf:params:oauth:token-type:access_token" \
  --requested-token-type "urn:ietf:params:oauth:token-type:access_token" \
  --audience "client-b" \
  --scope "openid read:finance"