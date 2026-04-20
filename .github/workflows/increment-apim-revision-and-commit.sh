#!/bin/bash

main() {
  set_up
  convert_open_api_to_swagger
  bump_revision_number_and_commit_changes_to_github
  echo "done"
}

set_up() {
  if [[ "${API_NAME}" == "" ]]; then
    echo "Need to set API_NAME. Hint: export API_NAME=\"pre-api\""
    exit 1;
  fi

  echo "Installing API spec converter"
  npm install -g api-spec-converter --ignore-scripts

  if ! test -f "specs/${API_NAME}.json"; then
    echo "Could not find API spec at path specs/${API_NAME}.json"
    exit 1
  fi
}

convert_open_api_to_swagger() {
  mkdir -p swagger_docs

  echo "Converting OpenAPI to Swagger for ${API_NAME}"

  api-spec-converter --to=swagger_2 --from=openapi_3  --syntax=yaml \
    --order=alpha "specs/${API_NAME}.json" > "swagger_docs/1.yaml"

  sed -E "s/basePath\: \//basePath\: \/\${API_NAME}/g" "swagger_docs/1.yaml" > "swagger_docs/2.yaml"
  sed -E "s/\- http/\- https/g" "swagger_docs/2.yaml" > "swagger_docs/3.yaml"
  sed -E "s/(host\: .+)/host\: 'sds-api-mgmt.staging.platform.hmcts.net'/g" "swagger_docs/3.yaml" > "swagger_docs/4.yaml"

  cp swagger_docs/4.yaml "${API_NAME}-stg.yaml"

  rm -rf swagger_docs
};

bump_revision_number_and_commit_changes_to_github(){
  mkdir -p "temp_master"
  git show master:infrastructure/main.tf > temp_master/main.tf
  git show master:pre-api-stg.yaml > temp_master/pre-api-stg.yml

  master_revision=$(grep -i 'api_revision' temp_master/main.tf | grep -oE '[0-9]+')
  branch_revision=$(grep -i 'api_revision' infrastructure/main.tf | grep -oE '[0-9]+')
  new_revision=master_revision

  echo "Comparing master to branch"
  if cmp temp_master/pre-api-stg.yml pre-api-stg.yaml; then
    echo "No changes to pre-api-stg.yaml"
    if [[ $master_revision -gt $branch_revision ]]; then
      echo "Bump branch revision number to match master"
      awk 'BEGIN{FS=OFS="\""} /api_revision=/{$2=new_revision}1' infrastructure/main.tf > temp && mv temp infrastructure/main.tf
      git add infrastructure/main.tf
    fi
  else
    echo "Main API Spec has changed compared to master, ensure revision is incremented"
    echo "Master revision $master_revision; branch revision $branch_revision"
    if [[ $master_revision -ge $branch_revision ]]; then
      echo "Master revision $master_revision is less than/equal to branch revision $branch_revision"
      new_revision=$((master_revision + 1))
      echo "New revision number: $new_revision"
      sed -e "s/^  api_revision.*\"$branch_revision\"/  api_revision = \"$new_revision\"/"  infrastructure/main.tf > temp && mv temp infrastructure/main.tf
      git add infrastructure/main.tf
    fi
  fi

  rm -rf temp_master

  commit_message=""

  CHANGED=$(git diff --name-only "specs/${API_NAME}.json" "${API_NAME}-stg.yaml" infrastructure/main.tf)

  if [ "$CHANGED" != "" ]; then
    echo "OpenAPI spec has been updated this commit"
    git add -u "specs/${API_NAME}.json" "${API_NAME}-stg.yaml" infrastructure/main.tf
    commit_message="Update OpenAPI Spec for ${API_NAME} with revision number $new_revision"
    echo "About to commit: ${commit_message}"
    git commit -m "$commit_message"
    git push
  fi
}

main

