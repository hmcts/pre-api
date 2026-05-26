#!/bin/bash

# Written for Github Actions (see .github/workflows)
#
# Run from project root folder.
#
# Install api-spec-converter before running:
# npm install -g api-spec-converter --ignore-scripts

main() {
  set_up
  add_warning_comment
#  convert_open_api_to_swagger
#  bump_revision_number_and_commit_changes_to_github
  echo "done"
}

set_up() {
  if ! test -f "specs/pre-api.json"; then
    echo "Could not find API spec at path specs/pre-api.json"
    exit 1
  fi
}

convert_open_api_to_swagger() {
  mkdir -p swagger_docs

  echo "Converting OpenAPI to Swagger for pre-api"

  api-spec-converter --to=swagger_2 --from=openapi_3  --syntax=yaml \
    --order=alpha "specs/pre-api.json" > "swagger_docs/1.yaml"

  sed -E "s/basePath\: \//basePath\: \/pre-api/g" "swagger_docs/1.yaml" > "swagger_docs/2.yaml"
  sed -E "s/\- http/\- https/g" "swagger_docs/2.yaml" > "swagger_docs/3.yaml"
  sed -E "s/(host\: .+)/host\: 'sds-api-mgmt.staging.platform.hmcts.net'/g" "swagger_docs/3.yaml" > "swagger_docs/4.yaml"

  cp swagger_docs/4.yaml "pre-api-stg.yaml"

  rm -rf swagger_docs
};

add_warning_comment(){
  echo "Adding warning comment that API spec has been updated"

  branch_name=$(git branch --show-current)

  commentBody="""
:bangbang: Change to API Spec detected

Monitor carefully when deploying to production.

Follow the release process and manually check that the Streaming Manager and portal are healthy after the release.

See https://tools.hmcts.net/confluence/spaces/S28/pages/1958069495/API+release+process for details."""

  gh pr comment "$branch_name" --edit-last --create-if-none -b "$commentBody"
}

bump_revision_number_and_commit_changes_to_github(){
  CHANGED=$(git diff --name-only "specs/pre-api.json" pre-api-stg.yaml infrastructure/main.tf)

  if [ "$CHANGED" != "" ]; then
    echo "OpenAPI spec has been updated this commit"
    git add -u "specs/pre-api.json" pre-api-stg.yaml infrastructure/main.tf
    commit_message="Update OpenAPI Spec for pre-api"
    echo "About to commit: ${commit_message}"
    git -c user.name='PRE DevOps' -c user.email='138598290+pre-devops@users.noreply.github.com' commit -m "$commit_message"
    git push
    add_warning_comment
  fi
}

main

