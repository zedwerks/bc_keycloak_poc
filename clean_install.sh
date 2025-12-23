cd keycloak_install
sh install.sh
cd ../terraform
source env/config.env
printenv | grep TF_
rm -rf .terraform.tfstate .terraform.tfstate.backup
terraform init -upgrade
terraform import -input -var-file=terraform.tfvars keycloak_realm.target poc
terraform apply -var-file=terraform.tfvars  -auto-approve
cd ../try_it_out
echo done.