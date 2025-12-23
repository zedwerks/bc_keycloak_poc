variable "realm_id" {
  type = string
  default ="poc"
}

variable "keycloak_base_url" {
  type = string
}

variable "keycloak_terraform_client_id" {
  type = string
  default = "terraform"
}

variable "keycloak_terraform_client_secret" {
  type = string
  default = "dasfaksjdfasdfje"
}

variable "delegate_client_secret" {
  type = string
  default = "dfasdflksdfasdfakjg"
}