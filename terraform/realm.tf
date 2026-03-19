
resource "keycloak_realm" "target" {
  realm                     = var.realm_id
  enabled                   = true
  display_name              = "poc Proof of Concepts Realm"

  # Set subject type to pairwise
  #attributes = {
  #  "pairwiseSubjectIdentifierEnabled" = "true"
  #}
  attributes = {
    "pairwiseSubSalt" = "86f09047-0025-4aa3-83f5-ff69905a0f3b" # do not change salt!!
  }
}