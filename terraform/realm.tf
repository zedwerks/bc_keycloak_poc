
resource "keycloak_realm" "target" {
  realm                     = var.realm_id
  enabled                   = true
  display_name              = "poc Proof of Concepts Realm"

  # Set subject type to pairwise
  #attributes = {
  #  "pairwiseSubjectIdentifierEnabled" = "true"
  #}
}