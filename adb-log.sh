#/bin/bash
# Local variables
ADB=/home/burt/Android/Sdk/platform-tools/adb
DEV=R5CX9339VAJ
# Logging to file
$ADB -s "$DEV" logcat -c
$ADB -s "$DEV" logcat -v threadtime > .agent/helmchat-logcat.txt