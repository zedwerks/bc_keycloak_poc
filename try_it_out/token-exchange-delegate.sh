#!/bin/bash
# token-exchange.sh
# This script performs a token exchange from client-a to client-b using the access token obtained from
# client-a's sign-on process. It uses Curl to handle the token exchange.
REALM=$TF_VAR_realm_id
ISSUER_BASE="http://localhost:8080/realms/$REALM"    
TOKEN_ENDPOINT="$ISSUER_BASE/protocol/openid-connect/token"
CLIENT_ID="client-d" 
CLIENT_SECRET=$TF_VAR_client_delegate_svc_secret
USER_ACCESS_TOKEN=$(cat tokens/access-token.jwt)

DELEGATED_SUBJECT_USER_ID=${amina_sub_uuid}

  curl -X POST "$ISSUER_BASE/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
  --data-urlencode "subject_token=${USER_ACCESS_TOKEN}" \
  --data-urlencode "requested_subject=${DELEGATED_SUBJECT_USER_ID}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}"