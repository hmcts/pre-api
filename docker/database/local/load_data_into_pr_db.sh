#!/usr/bin/env bash

# This script is for loading the PR database with dummy data
# Needs to be run from the same folder as the zip (local_db_data.tar.gz) containing the .sql files
# Requires Azure CLI, kubelogin and kubectl to be installed and configured
# Usage: ./load_data_into_pr_db.sh <pull-request-id>
# Example: ./load_data_into_pr_db.sh pr-1362

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "ERROR: You must provide a pull request."
  echo "Example: $0 pr-1362"
  exit 1
fi

PULL_REQUEST="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE="$SCRIPT_DIR/local_db_data.tar.gz"
EXTRACT_ROOT="/tmp/seed"
SQL_GLOB="$EXTRACT_ROOT/data/*/*.sql"

# Cluster CONFIG
AZ_SUBSCRIPTION="DTS-SHAREDSERVICES-DEV"
AZ_RESOURCE_GROUP="ss-dev-00-rg"
AKS_CLUSTER="ss-dev-00-aks"
NAMESPACE="pre"

echo "Checking sql files zip exists..."
if [[ ! -f "$ARCHIVE" ]]; then
  echo "ERROR: Cannot find sql zip at $ARCHIVE! Script must be in same directory as sql zip"
  exit 1
fi

# Logging into Azure
echo "Checking Azure login..."
retVal=0
CURRENT_USER="$(az ad signed-in-user show --query '{userLogin:userPrincipalName}' --output tsv)" || retVal=$?
if [ $retVal -ne 0 ]; then
    echo "Not signed-in, requesting sign-in..."
    az login
else
    echo "Already signed-in as $CURRENT_USER."
fi

# Connect to the dev cluster and find the database pod for PR
echo "Connecting to Dev AKS Cluster..."
az aks get-credentials \
  --resource-group "$AZ_RESOURCE_GROUP" \
  --name "$AKS_CLUSTER" \
  --subscription "$AZ_SUBSCRIPTION" \
  --overwrite-existing

kubelogin convert-kubeconfig -l azurecli

echo "Finding Postgres pod for PR '$PULL_REQUEST'..."
FULL_INSTANCE="pre-api-$PULL_REQUEST"
POD=$(
  kubectl -n "$NAMESPACE" get pods \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
    | grep "$PULL_REQUEST" \
    | grep "postgresql" \
    | head -n 1
)

if [[ -z "$POD" ]]; then
  echo "ERROR: No Postgres pod found matching PR '$PULL_REQUEST'"
  exit 1
fi

echo "✔ Found pod: $POD"

echo "Copying SQL zip to container $POD ..."
if ! kubectl -n "$NAMESPACE" cp "$ARCHIVE" "$POD:/tmp/local_db_data.tar.gz"; then
  echo "ERROR: Failed to copy archive to pod."
  exit 1
fi

# exec into container to unzip the sql files and execute them
echo "Running SQL logic inside container..."
kubectl -n "$NAMESPACE" exec -i "$POD" -- bash << EOF
set -euo pipefail

ARCHIVE="/tmp/local_db_data.tar.gz"
EXTRACT_ROOT="/tmp/dummy_data"
export PGPASSWORD="\$POSTGRES_PASSWORD"

echo "Making data folder for sql files: \$EXTRACT_ROOT"
mkdir -p "\$EXTRACT_ROOT"

echo "Extracting archive..."
tar --auto-compress --warning=no-unknown-keyword -xf "\$ARCHIVE" -C "\$EXTRACT_ROOT"

echo "Executing SQL files..."
find "\$EXTRACT_ROOT" -type f -name "*.sql" | sort | while read -r file; do
  echo "   -> Executing \$file"
  psql -U "\$POSTGRES_USER" -d "\$POSTGRES_DATABASE" -f "\$file"
done

echo "Cleaning up..."
rm -rf "\$EXTRACT_ROOT"
rm -f "\$ARCHIVE"
echo "Load data into PR database finished"
EOF
