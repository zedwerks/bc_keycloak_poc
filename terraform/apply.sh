#!/bin/bash
# Script to locally run the docker container including re-building the image, if necessary.
workdir="."

if ! command -v terraform &> /dev/null; then
    echo "Terraform is not installed or not in PATH."
    exit 1
else
    echo "Terraform is installed:"
    terraform version
fi
source env/config.env
printenv | grep TF_
terraform apply -auto-approve