data "keycloak_realm" "realm" {
  realm = var.keycloak_realm
}

resource "keycloak_realm" "poc" {
  realm                     = var.keycloak_realm
  enabled                   = true
  display_name              = "POC Realm"

  # Set subject type to pairwise
  #attributes = {
  #  "pairwiseSubjectIdentifierEnabled" = "true"
  #}
}