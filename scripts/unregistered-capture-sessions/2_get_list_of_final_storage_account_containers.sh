#!/opt/homebrew/bin/bash

# ==================================================================================
# Script Name: 2_get_list_of_final_storage_account_containers.sh
# Description: Gets unfiltered lists of final storage account containers, by prefix
# ==================================================================================

OUTPUT_DIR="output/storage-containers"
ACCOUNT_NAME=prefinalsaprod
NUM_RESULTS=20000

main() {
  mkdir -p $OUTPUT_DIR

  for i in {0..9} {a..z}
  do
      echo "Getting list of containers with prefix $i"
      az storage container list --prefix "$i" --query "[].{name:name}" --num-results $NUM_RESULTS --output tsv  --account-name $ACCOUNT_NAME --auth-mode login --include-metadata false > "$OUTPUT_DIR/storage_containers_$i.txt"
  done
}

main
