#!/bin/bash

# =================================================================================
# Script Name: 4_match_up_missing_capture_sessions_with_blobs.sh
# Description: Matches up unregistered recordings by looking for track names
#   in final storage account that match capture session IDs with NO_RECORDING
#   status, with hyphens removed
# =================================================================================

OUTPUT_DIR="output"
CAPTURE_SESSIONS_CSV="$OUTPUT_DIR/capture_sessions_no_recording_in_database.csv"

BLOBS_DIR="$OUTPUT_DIR/storage-blobs"

CONCLUSIONS_CSV="$OUTPUT_DIR/conclusions.csv"

main() {
  if [ ! -d "$OUTPUT_DIR" ]; then
    echo "$OUTPUT_DIR does not exist. You need to run the previous scripts first"
    exit
  fi

  if [ ! -d "$BLOBS_DIR" ]; then
    echo "$BLOBS_DIR does not exist. You need to run the previous scripts first"
    exit
  fi

  if [ ! -f $CAPTURE_SESSIONS_CSV ]; then
     echo "$CAPTURE_SESSIONS_CSV does not exist. You need to run the previous scripts first"
     exit
  fi

  if [ ! -f $CONCLUSIONS_CSV ]; then
     touch "$CONCLUSIONS_CSV"
     # Headers for the CSV file
     echo "capture_session_id_from_track_name,recording_id_container_name" >> "$CONCLUSIONS_CSV"
  fi

  while IFS= read -r capture_session_id; do
    nohyphens="${capture_session_id//-}"
    echo "Checking capture session: $nohyphens"
    grep -r "${BLOBS_DIR}" -e $nohyphens --no-filename >> "$CONCLUSIONS_CSV"
  done < "$CAPTURE_SESSIONS_CSV"
}

main
