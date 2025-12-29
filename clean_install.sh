cd keycloak_install
sh install.sh
cd ../terraform
source env/config.env
printenv | grep TF_
rm -rf terraform.tfstate terraform.tfstate.backup
terraform init -upgrade
terraform import -input keycloak_realm.target poc
terraform apply -auto-approve
cd ../try_it_out
echo $TF_VAR_keycloak_base_url
echo done.