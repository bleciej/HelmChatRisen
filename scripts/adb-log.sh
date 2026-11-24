#!/usr/bin/env bash
set -euo pipefail

# Local variables
ADB="$HOME/Android/Sdk/platform-tools/adb"
LOG_FILE=".agent/helmchat-logcat.txt"

# Detect the first connected physical device in the "device" (debugging) state.
# Emulators (e.g. emulator-5554, sdk_gphone, generic_x86) are ignored.
# `adb devices -l` also reports the model, which is shown for confirmation.
DEVICE_LINE=$("$ADB" devices -l | awk '
  $2 == "device" {
    serial = $1
    model = "unknown"
    product = ""
    device = ""

    for (i = 3; i <= NF; i++) {
      if ($i ~ /^model:/)   { sub(/^model:/, "", $i); model = $i }
      if ($i ~ /^product:/) { sub(/^product:/, "", $i); product = $i }
      if ($i ~ /^device:/)  { sub(/^device:/, "", $i); device = $i }
    }

    # AVDs use the "emulator-<port>" serial and/or well-known image identifiers.
    if (serial ~ /^emulator-/) next
    if (product ~ /(^|_)sdk_|emulator|generic_x86|goldfish/) next
    if (device ~ /^(emu|generic|goldfish|sdk)/) next
    if (model ~ /(^|_)sdk_|emulator|generic_x86|goldfish/) next

    print serial, model
    exit
  }
')

if [[ -z "$DEVICE_LINE" ]]; then
  echo "No device for debugging connected(adb devices -l)." >&2
  exit 1
fi

read -r DEV MODEL <<< "$DEVICE_LINE"
echo "Found device: $DEV ($MODEL)"

# Logging to file
"$ADB" -s "$DEV" logcat -c
"$ADB" -s "$DEV" logcat -v threadtime > "$LOG_FILE"
