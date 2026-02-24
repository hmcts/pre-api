#!/opt/homebrew/bin/bash

# ==================================================================================
# Script Name: 3_get_list_of_blobs_with_mp4_suffix.sh
# Description: Gets list of containers which contain a MP4 file with 32-char prefix
# ==================================================================================

BLOBS_DIR="output/storage-blobs"
STORAGE_CONTAINERS_DIR="output/storage-containers"
ACCOUNT_NAME=prefinalsaprod
REGEX_UUID='^[a-z0-9]{32}[a-z0-9_]+\.mp4$'

main() {
  mkdir -p $BLOBS_DIR

  # Only goes up to f because UUID is 128-bit
  for i in {0..9} {a..f}
  do
      storage_container_prefix=${i::1}
      storage_container_file="$STORAGE_CONTAINERS_DIR/storage_containers_$storage_container_prefix.txt"
      echo "Checking containers with prefix $storage_container_prefix"
      echo "Storage container file: $storage_container_file"

      while IFS= read -r recording_id_container_name; do

          echo "Checking container: $recording_id_container_name"

          blobs=$(az storage blob list -c "$recording_id_container_name" --account-name $ACCOUNT_NAME --auth-mode login --query "[].{name:name}" --output tsv)
          while read -r -a track_names ; do
             capture_session_id_track_name="${track_names[0]}"
             if [[ "$capture_session_id_track_name" =~ $REGEX_UUID ]]; then
               echo "Found an MP4 file: $capture_session_id_track_name in container $recording_id_container_name"
               capture_session_id_from_track_name=${capture_session_id_track_name:0:32}

               file_prefix=${recording_id_container_name:0:2}
               output_file="$BLOBS_DIR/blobs-mp4-list-$file_prefix.csv"
               if ! [ -e "$output_file" ] ; then
                   touch "$output_file"

                   # Headers for the CSV file
                   echo "capture_session_id_from_track_name,recording_id_container_name" >> "$output_file"
               fi

               echo "$capture_session_id_from_track_name,$recording_id_container_name" >> "$output_file"
             fi
          done <<< "${blobs}"

      done < "$STORAGE_CONTAINERS_DIR/storage_containers_$i.txt"

  done
}

main
