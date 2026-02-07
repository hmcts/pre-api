#!/opt/homebrew/bin/bash

# ==============================================================================
# Script Name: 1_get_capture_sessions_no_recording_from_db
# Description: Checks for capture sessions with NO_RECORDING status
# ==============================================================================

OUTPUT_DIR="output"
CAPTURE_SESSIONS_CSV="$OUTPUT_DIR/capture_sessions_no_recording_in_database.csv"

POSTGRES_HOST=pre-db-prod.postgres.database.azure.com
DB_NAME=api
DB_USER="DTS JIT Access pre DB Reader SC"

SQL_QUERY="""
          SELECT id FROM capture_sessions
          WHERE (origin::text = 'PRE'::text)
          AND ingest_address LIKE '%mediakind.com%'
          AND (deleted_at IS NULL)
          AND ((finished_at > '2024-07-18 00:00:59.000')
          AND (status::text = 'NO_RECORDING'::text) )
          """

 main() {
   mkdir -p $OUTPUT_DIR

  echo "Signing into database"
  ../Connect-To-PRE-Production-DB.sh

  PGPASSWORD=$(az account get-access-token --resource-type oss-rdbms --query accessToken -o tsv)
  export PGPASSWORD

  echo "Executing query"
  psql -t -h $POSTGRES_HOST -U "$DB_USER" -d $DB_NAME -c "$SQL_QUERY" -o $CAPTURE_SESSIONS_CSV
}

main
