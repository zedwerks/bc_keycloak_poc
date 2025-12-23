terraform {
  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "= 5.4.0"
    }
  }
}

# Note: This client (service account) needs "realm-admin" role to be able to manage the realm
# via Terraform.
provider "keycloak" {
  client_id     = var.KEYCLOAK_TERRAFORM_CLIENT_ID
  client_secret = var.KEYCLOAK_TERRAFORM_CLIENT_SECRET
  url           = var.keycloak_base_url
  realm         = var.realm_id
}