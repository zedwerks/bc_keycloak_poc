#!/bin/bash

echo "Building Services..."
if docker compose --file ./docker-compose.yml build; then
    echo "Services built successfully. Now starting..."
    docker compose --file ./docker-compose.yml up -d
else 
    echo "Services failed to start."
    exit 1
fi
# okay, so now let's get terraform with realm-managmeent roles to 
# be able to administer the realm.

# Wait for the service to start.
echo "Waiting for Keycloak to start..."
until $(curl --output /dev/null --silent --head --fail http://localhost:8080); do
    printf '.'
    sleep 1
done
echo ""
echo "Keycloak is up and running."


# Setup the permissions for the terraform client to be able to manage the realm.
echo "Setting up permissions for the Terraform client..."
docker exec -it keycloak /bin/bash /opt/keycloak/bin/terraform-realm-admin.sh