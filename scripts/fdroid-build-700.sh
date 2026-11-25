#!/usr/bin/env bash
# Shortcut for local verification of F-Droid version 700 (arm64-v8a).
set -euo pipefail
readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/fdroid-build-local.sh" 700 "$@"
