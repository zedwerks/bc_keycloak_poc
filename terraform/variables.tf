variable "realm_id" {
  type = string
  default ="poc"
}

variable "KEYCLOAK_TERRAFORM_CLIENT_ID" {
  type = string
}

variable "KEYCLOAK_TERRAFORM_CLIENT_SECRET" {
  type = string
}

variable "keycloak_base_url" {
  type = string
}

variable "delegate_client_secret" {
  type = string
}