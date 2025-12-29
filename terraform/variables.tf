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

variable "client_d_secret" {
  type = string
  default = "a284e7d646199aab"
}

variable "client_k_secret" {
  type = string
  default = "dkadsfkasdfasjiei"
}

variable "client_a_rs_secret"{
  type = string
  default = "RNrsYos1urQ4DqNTr40NILKCLWEQgfJL"
}

variable "client_a1_secret"{
  type = string
  default = "RNrsYos1urQ4DqNTr40NILKCLWEQgfJL"
}

variable "client_a2_secret"{
  type = string
  default = "RNrsYos1urQ4DqNTr40NILKCLWEQgfJL"
}

variable "client_b_rs_secret"{
  type = string
  default = "wmug4dloTqA24lniq9dK5EVSxVhhFtZE"
}