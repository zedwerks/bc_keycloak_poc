#!/bin/bash

source ../terraform/env/config.env

printenv | grep TF_

# Check if docker is installed
if ! command -v docker &> /dev/null
then
    echo "Docker could not be found. Please install it first."
    exit
fi
# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null
then
    echo "Docker Compose could not be found. Please install it first."
    exit
fi
# Check if docker is running
if ! docker info &> /dev/null
then
    echo "Docker is not running. Please start Docker first."
    exit
fi

echo "[1] Setup the Certificates for HTTPS..."
cd ./https
if [ -d certs ]; then
    echo "SSL Certs already generated; Assume you added these with mkcerts"
else
    echo "Follow the README in /example_install/keycloak/https to setup trusted certs for HTTPS"
    exit 1
fi
cd ..

echo "[2] Building Services..."
docker compose down -v -f ./docker-compose.yml
if docker compose --file ./docker-compose.yml build --no-cache; then
    echo "Services built successfully. Now starting..."
    docker compose --file ./docker-compose.yml up -d
else 
    echo "Services failed to start."
    exit 1
fi
# okay, so now let's get terraform with realm-managmeent roles to 
# be able to administer the realm.

# Wait for the service to start.
echo "[3] Waiting for Keycloak to start..."
until $(curl --output /dev/null --silent --head --fail $TF_VAR_keycloak_base_url); do
    printf '.'
    sleep 1
done
echo ""
echo "Keycloak is up and running."


# Setup the permissions for the terraform client to be able to manage the realm.
echo "Setting up permissions for the Terraform client..."
docker exec -it keycloak /bin/bash /opt/keycloak/bin/terraform-realm-admin.sh