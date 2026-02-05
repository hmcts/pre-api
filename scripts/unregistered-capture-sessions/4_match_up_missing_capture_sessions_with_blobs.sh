#!/opt/homebrew/bin/bash

# =================================================================================
# Script Name: 4_match_up_missing_capture_sessions_with_blobs.sh
# Description: Matches up unregistered recordings by looking for track names
#   in final storage account that match capture session IDs with NO_RECORDING
#   status, with hyphens removed
# =================================================================================

OUTPUT_DIR="output"
CAPTURE_SESSIONS_CSV="$OUTPUT_DIR/capture_sessions_no_recording_in_database.csv"

BLOBS_DIR="$OUTPUT_DIR/storage-blobs"

BLOBS_CSV="$BLOBS_DIR/blobs-mp4-list-4.csv"

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

  if [ ! -f $BLOBS_CSV ]; then
     echo "$BLOBS_CSV does not exist. You need to run the previous scripts first."
     exit
  fi

  touch "$CONCLUSIONS_CSV"

#  grep -if "${CAPTURE_SESSIONS_CSV}" "${BLOBS_CSV}" >> $CONCLUSIONS_CSV
#
#  while IFS="," read -r recording_id_container_name capture_session_id_from_track_name
#  do
#    echo "Container Name-$recording_id_container_name"
#    echo "Capture Session ID: $capture_session_id_from_track_name"
#    echo ""
#  done < <(tail -n +2 $BLOBS_CSV)

  while IFS= read -r capture_session_id; do
    without_hyphens="${capture_session_id//-}"
#    echo "Checking capture session: $without_hyphens"

    grep "$without_hyphens" $BLOBS_CSV

  done < "$CAPTURE_SESSIONS_CSV"


}

main
