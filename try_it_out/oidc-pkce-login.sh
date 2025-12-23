#!/usr/bin/env bash
set -euo pipefail

# --------- CONFIG ---------
# Required: your OIDC client and realm
REALM=$TF_VAR_realm_id
ISSUER="http://localhost:8080/realms/$REALM"    
CLIENT_ID="${CLIENT_ID:-client-a}"

# Optional (only needed if not using discovery)
AUTH_ENDPOINT="${AUTH_ENDPOINT:-}"
TOKEN_ENDPOINT="${TOKEN_ENDPOINT:-}"

# Redirect URI registered in your client (localhost-friendly)
REDIRECT_URI="${REDIRECT_URI:-http://127.0.0.1:3000/callback}"

# Scopes (include 'openid' for OIDC)
SCOPE="${SCOPE:-openid profile email}"

# Files to write
OUT_DIR="${OUT_DIR:-./tokens}"
ACCESS_OUT="$OUT_DIR/access-token.jwt"
ID_OUT="$OUT_DIR/id-token.jwt"
REFRESH_OUT="$OUT_DIR/refresh-token.jwt"
FULL_JSON_OUT="$OUT_DIR/result.json"

# Options
OPEN_BROWSER="${OPEN_BROWSER:-true}"   # set to false to copy URL manually
LISTEN_PORT="$(echo "$REDIRECT_URI" | awk -F[/:] '{print $5}')"
# --------------------------

mkdir -p "$OUT_DIR"

echo "🔐 Starting OIDC Authorization Code + PKCE login for client_id='$CLIENT_ID'"
echo "   Issuer: $ISSUER"
echo "   Redirect URI: $REDIRECT_URI"
echo "   Scope: $SCOPE"
echo

# Build base command
CMD=( oauth-tool authorize
  --client-id "$CLIENT_ID"
  --redirect-uri "$REDIRECT_URI"
  --scope "$SCOPE"
  --pkce
  --listen
)

# Discovery vs explicit endpoints
if [[ -n "$AUTH_ENDPOINT" && -n "$TOKEN_ENDPOINT" ]]; then
  CMD+=( --auth-endpoint "$AUTH_ENDPOINT" --token-endpoint "$TOKEN_ENDPOINT" )
else
  # Use discovery from the issuer (oauth-tool will fetch /.well-known/openid-configuration)
  CMD+=( --issuer "$ISSUER" )
fi

# Browser behavior
if [[ "$OPEN_BROWSER" != "true" ]]; then
  CMD+=( --no-browser )
fi

echo "➡️  Running: ${CMD[*]}"
RESULT="$("${CMD[@]}")" || { echo "❌ oauth-tool failed"; exit 1; }

# Save full JSON
echo "$RESULT" | jq . > "$FULL_JSON_OUT"

# Extract tokens
ACCESS_TOKEN=$(echo "$RESULT" | jq -r '.access_token // empty')
ID_TOKEN=$(echo "$RESULT" | jq -r '.id_token // empty')
REFRESH_TOKEN=$(echo "$RESULT" | jq -r '.refresh_token // empty')

if [[ -z "$ACCESS_TOKEN" ]]; then
  echo "❌ No access_token in response; see $FULL_JSON_OUT"
  exit 1
fi

echo -n "$ACCESS_TOKEN" > "$ACCESS_OUT"
[[ -n "$ID_TOKEN" ]] && echo -n "$ID_TOKEN" > "$ID_OUT"
[[ -n "$REFRESH_TOKEN" ]] && echo -n "$REFRESH_TOKEN" > "$REFRESH_OUT"

echo
echo "✅ Tokens saved in $OUT_DIR/"
ls -1 "$OUT_DIR"

# Optional: quick decode (payload only, no verify)
decode() { python - "$1" <<'PY'
import sys, json, base64
t=sys.argv[1].split('.')
if len(t)<2: print("not a jwt"); sys.exit(0)
pad=lambda s: s + "="*(-len(s)%4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(pad(t[1]))), indent=2))
PY
}

echo
echo "🔎 Access token payload:"
decode "$ACCESS_TOKEN" | sed 's/^/  /'

echo
echo "💡 Next steps:"
echo "  - Use the access token with your API:"
echo "      curl -H 'Authorization: Bearer $(cut -c -25 "$ACCESS_OUT")...'"
echo "  - Full JSON response is at: $FULL_JSON_OUT"
echo "  - Refresh token (if issued) is at: $REFRESH_OUT"