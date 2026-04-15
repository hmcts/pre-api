#!/usr/bin/env bash
#
# Usage:
#   bash PRE_synch_fix.sh [root_dir] [log_file] [--dry-run|-n] [--all|-a]
#
# Examples:
#   bash PRE_synch_fix.sh
#   bash PRE_synch_fix.sh --dry-run
#   bash PRE_synch_fix.sh --all
#   bash PRE_synch_fix.sh /videos --all
#   bash PRE_synch_fix.sh /videos log.csv --all -n
#
# Behaviour:
#   - --all / -a : process ALL .mp4 files (skip ffprobe error check)
#   - --dry-run / -n : simulate only
#   - Otherwise: only process files where ffprobe reports errors

set -u

root_dir="."
log_file="trim_log.csv"
dry_run=0
process_all=0

count_total=0
count_no_error=0
count_no_second_keyframe=0
count_would_cut=0
count_cut=0
count_rename_failed=0
count_final_move_failed=0
count_ffmpeg_failed=0

for arg in "$@"; do
  case "$arg" in
    --dry-run|-n) dry_run=1 ;;
    --all|-a) process_all=1 ;;
    *)
      if [ "$root_dir" = "." ]; then
        root_dir="$arg"
      elif [ "$log_file" = "trim_log.csv" ]; then
        log_file="$arg"
      else
        echo "Unexpected argument: $arg" >&2
        exit 1
      fi
      ;;
  esac
done

if [ ! -d "$root_dir" ]; then
  echo "Root folder does not exist: $root_dir" >&2
  exit 1
fi

if [ ! -f "$log_file" ]; then
  printf '"file","status","cut_time","probe_error"\n' > "$log_file"
fi

csv_escape() {
  local s="$1"
  s=${s//$'\r'/ }
  s=${s//$'\n'/ | }
  s=${s//\"/\"\"}
  printf '"%s"' "$s"
}

make_backup_path() {
  local f="$1"
  local dir base stem backup ts

  dir=$(dirname -- "$f")
  base=$(basename -- "$f")
  stem="${base%.mp4}"

  backup="${dir}/${stem}.original.mp4"

  if [ -e "$backup" ]; then
    ts=$(date +%Y%m%d_%H%M%S)_$$
    backup="${dir}/${stem}.original.${ts}.mp4"
  fi

  printf '%s\n' "$backup"
}

while IFS= read -r -d '' f; do
  base=$(basename -- "$f")

  case "$base" in
    *.tmpfixed.mp4) continue ;;
  esac

  count_total=$((count_total + 1))

  probe_errors=""

  if [ "$process_all" -eq 0 ]; then
    probe_errors=$(
      ffprobe -v error "$f" 2>&1 >/dev/null
    )

    if [ -z "$probe_errors" ]; then
      printf '%s,%s,%s,%s\n' \
        "$(csv_escape "$f")" \
        "$(csv_escape "no_error")" \
        "$(csv_escape "")" \
        "$(csv_escape "")" \
        >> "$log_file"
      count_no_error=$((count_no_error + 1))
      continue
    fi
  fi

  kf2=$(
    ffprobe -v error \
      -select_streams v:0 \
      -skip_frame nokey \
      -show_entries frame=pts_time \
      -of csv=p=0 \
      "$f" | sed -n '2p'
  )

  if [ -z "$kf2" ]; then
    printf '%s,%s,%s,%s\n' \
      "$(csv_escape "$f")" \
      "$(csv_escape "no_second_keyframe")" \
      "$(csv_escape "")" \
      "$(csv_escape "$probe_errors")" \
      >> "$log_file"
    count_no_second_keyframe=$((count_no_second_keyframe + 1))
    continue
  fi

  if [ "$dry_run" -eq 1 ]; then
    status="would_cut"
    [ "$process_all" -eq 1 ] && status="would_cut_all"

    printf '%s,%s,%s,%s\n' \
      "$(csv_escape "$f")" \
      "$(csv_escape "$status")" \
      "$(csv_escape "$kf2")" \
      "$(csv_escape "$probe_errors")" \
      >> "$log_file"

    count_would_cut=$((count_would_cut + 1))
    continue
  fi

  dir=$(dirname -- "$f")
  fname=$(basename -- "$f")
  stem="${fname%.mp4}"

  backup=$(make_backup_path "$f")
  temp_out="${dir}/${stem}.tmpfixed.mp4"

  rm -f -- "$temp_out"

  if ! mv -- "$f" "$backup"; then
    printf '%s,%s,%s,%s\n' \
      "$(csv_escape "$f")" \
      "$(csv_escape "rename_failed")" \
      "$(csv_escape "$kf2")" \
      "$(csv_escape "$probe_errors")" \
      >> "$log_file"
    count_rename_failed=$((count_rename_failed + 1))
    continue
  fi

  if ffmpeg -nostdin -y -ss "$kf2" -i "$backup" -c copy -avoid_negative_ts 1 -copytb 1 "$temp_out" >/dev/null 2>&1; then
    if mv -- "$temp_out" "$f"; then
      status="cut"
      [ "$process_all" -eq 1 ] && status="cut_all"

      printf '%s,%s,%s,%s\n' \
        "$(csv_escape "$f")" \
        "$(csv_escape "$status")" \
        "$(csv_escape "$kf2")" \
        "$(csv_escape "$probe_errors")" \
        >> "$log_file"

      count_cut=$((count_cut + 1))
    else
      mv -- "$backup" "$f" 2>/dev/null
      rm -f -- "$temp_out"
      printf '%s,%s,%s,%s\n' \
        "$(csv_escape "$f")" \
        "$(csv_escape "final_move_failed")" \
        "$(csv_escape "$kf2")" \
        "$(csv_escape "$probe_errors")" \
        >> "$log_file"
      count_final_move_failed=$((count_final_move_failed + 1))
    fi
  else
    mv -- "$backup" "$f" 2>/dev/null
    rm -f -- "$temp_out"
    printf '%s,%s,%s,%s\n' \
      "$(csv_escape "$f")" \
      "$(csv_escape "ffmpeg_failed")" \
      "$(csv_escape "$kf2")" \
      "$(csv_escape "$probe_errors")" \
      >> "$log_file"
    count_ffmpeg_failed=$((count_ffmpeg_failed + 1))
  fi

done < <(find "$root_dir" -type f -iname '*.mp4' -print0)

echo "Summary"
echo "-------"
echo "Root directory       : $root_dir"
echo "Log file             : $log_file"
echo "Mode                 : $([ "$dry_run" -eq 1 ] && echo "dry-run" || echo "live")"
echo "Process all          : $([ "$process_all" -eq 1 ] && echo "yes" || echo "no")"
echo "Files processed      : $count_total"
if [ "$process_all" -eq 1 ]; then
  echo "Mode detail          : forced processing (error check skipped)"
fi
echo "No second keyframe   : $count_no_second_keyframe"
echo "Would cut            : $count_would_cut"
echo "Cut                  : $count_cut"
echo "Rename failed        : $count_rename_failed"
echo "Final move failed    : $count_final_move_failed"
echo "FFmpeg failed        : $count_ffmpeg_failed"
