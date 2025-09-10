terraform {
  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = ">= 5.4.0"
    }
  }
}

# Note: This client (service account) needs "realm-admin" role to be able to manage the realm
# via Terraform.
provider "keycloak" {
  client_id     = var.keycloak_terraform_client_id
  client_secret = var.keycloak_terraform_client_secret
  url           = var.keycloak_base_url
  realm         = var.keycloak_realm
}