#!/bin/bash

BASTION_HOST=bastion-prod.platform.hmcts.net
POSTGRES_HOST=pre-db-prod.postgres.database.azure.com
POSTGRES_HOST_PORT=5432

CLIENT_PORT=5440

echo "Checking Azure login..."
retVal=0
CURRENT_USER="$(az ad signed-in-user show --query '{userLogin:userPrincipalName}' --output tsv)" || retVal=$?
if [ $retVal -ne 0 ]; then
    echo "Not signed-in, requesting sign-in..."
    az login
else
    echo "Already signed-in as $CURRENT_USER."
fi

echo "Updating Azure SSH config..."
rm -f ~/.ssh/az_ssh_config/\*.platform.hmcts.net/id_rsa* && az ssh config --ip \*.platform.hmcts.net --file ~/.ssh/config

DB_TOKEN="$(az account get-access-token --resource-type oss-rdbms --query accessToken -o tsv)"
echo "$DB_TOKEN" | pbcopy
echo -e "Login token (already copied to clipboard):\n$DB_TOKEN"

echo "Port forwarding through Bastion host..."
# Forward DB Connection
ssh -fN $BASTION_HOST -L $CLIENT_PORT:$POSTGRES_HOST:$POSTGRES_HOST_PORT

if [ $? -eq 0 ]; then
    cat << EOF
    Connection available
    ---------------------------------------
    Host: localhost
    Port: $CLIENT_PORT
    User: DTS JIT Access pre DB Reader SC
    Database: api
EOF
fi
