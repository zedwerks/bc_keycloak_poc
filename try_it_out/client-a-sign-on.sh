#!/bin/bash
# client-a-sign-on.sh
# This script initiates the OAuth 2.0 Authorization Code flow with PKCE for client-a.
# It uses the oauth-tool to handle the authorization process.
# 3a) Run with discovery (simplest)
ISSUER="https://keycloak.local:8080/realms/poc" \
CLIENT_ID="client-a" \
REDIRECT_URI="http://127.0.0.1:3001/callback" \
./oidc-pkce-login.sh