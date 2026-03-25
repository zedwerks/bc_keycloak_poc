
cd keycloak
sh install.sh
cd ../terraform
source env/config.env
printenv | grep TF_
rm -r terraform.tfstate
rn -r terraform.tfstate.backup
terraform init -upgrade
terraform import -input keycloak_realm.target poc
terraform apply -auto-approve
cd ../try_it_out
echo $TF_VAR_keycloak_base_url
echo done. 