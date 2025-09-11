
source login_terraform.sh

curl -s -X GET "$KC_HOST/admin/realms/$REALM/client-scopes" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" | jq