# ----------------------------------------------------------------------------
# Dockerfile - a Keycloak image that includes the SMART on FHIR extensions.
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------

# Build Stage
#FROM maven:3.8.3-openjdk-11-slim AS builder
FROM maven:3.9.11-eclipse-temurin-21 AS builder

# Set the working directory in the container
WORKDIR /workspace
# Copy the files needed
COPY keycloak/ /workspace/keycloak/

## =================================================================================================
# Package Stage - add to an official Keycloak image
FROM quay.io/keycloak/keycloak:26.3.3

USER root

# need the Keycloak Port ENV var set so we can use it in entrypoint to 
# apply realm admin rights to the terraform client. 
# Used in: /opt/keycloak/bin/terraform_realm_admin.sh
ENV KEYCLOAK_PORT=8080

# Default ENV vars for the Keycloak image
ENV KEYCLOAK_ADMIN=admin
ENV KEYCLOAK_ADMIN_PASSWORD=admin
ENV KC_BOOTSTRAP_ADMIN_USERNAME=admin
ENV KC_BOOTSTRAP_ADMIN_PASSWORD=admin
ENV KEYCLOAK_TARGET_REALM_DISPLAY_NAME="IIS"
ENV KEYCLOAK_TARGET_REALM=poc
ENV KEYCLOAK_TERRAFORM_CLIENT_ID=terraform
ENV KEYCLOAK_TERRAFORM_CLIENT_SECRET=terraform!secret

# Make sure target directories exist
RUN mkdir -p /opt/keycloak/bin/ 
RUN mkdir -p /opt/keycloak/data/import/

# Copy the script that sets the realm admin roles for the terraform client
COPY --from=builder /workspace/keycloak/bin/bootstrap.sh /opt/keycloak/bin/bootstrap.sh
COPY --from=builder /workspace/keycloak/bin/terraform-realm-admin.sh /opt/keycloak/bin/terraform-realm-admin.sh

# Copy the Keycloak Realm file (which in build phase, the realm name was resolved to the realm name)
COPY --from=builder /workspace/keycloak/import/realm-template.json /opt/keycloak/data/import/realm.json

RUN chmod +x /opt/keycloak/bin/bootstrap.sh
RUN chown keycloak:root -R /opt/keycloak/bin/ /opt/keycloak/data/import/

USER keycloak

# Expose the port if needed
EXPOSE $KEYCLOAK_PORT

# Start Keycloak with the import-realm optin to load in the SMART on FHIR realm
ENTRYPOINT ["/opt/keycloak/bin/bootstrap.sh"]
CMD ["start-dev", "--import-realm", "--hostname=localhost"]