#!/usr/bin/env bash
# Local helper for verifying the F-Droid recipe for HelmChatRisen.
# It is not part of the recipe or the V1.7 tag; it only adjusts the local environment.
#
# Usage:
#   ./scripts/fdroid-build-local.sh          # build only 700 (arm64-v8a) by default
#   ./scripts/fdroid-build-700.sh            # shortcut for 700
#   SKIP_NPM_CI=1 ./scripts/fdroid-build-700.sh
set -euo pipefail

readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -P "$SCRIPT_DIR/.." && pwd)"
readonly FDROID_DATA="${FDROID_DATA:-/home/burt/AndroidStudioProjects/fdroiddata}"
readonly APP_ID="dev.communicator.helmchatrisen"
readonly BUILD_DIR="$PROJECT_ROOT/build"
readonly LOG_DIR="$BUILD_DIR/fdroid-logs"
readonly TMP_DIR="$BUILD_DIR/fdroid-tmp"
readonly SHIM_DIR="$BUILD_DIR/gradle-shim"
readonly CONFIG="$FDROID_DATA/config.yml"
readonly CONFIG_BACKUP="$BUILD_DIR/fdroiddata-config.yml.backup"
readonly RELEASE_TAG="${FDROID_RELEASE_TAG:-V1.7}"
readonly RELEASE_COMMIT="${FDROID_RELEASE_COMMIT:-62b57523121fe33ea79c4598431c7b276bafe611}"
readonly RECIPE="$PROJECT_ROOT/fdroid/dev.communicator.helmchatrisen.yml"
readonly FDROID_RECIPE="$FDROID_DATA/metadata/$APP_ID.yml"

mkdir -p "$LOG_DIR" "$TMP_DIR" "$SHIM_DIR" "$BUILD_DIR/fdroid-local/gpg" "$BUILD_DIR/fdroid-local/webroot"
touch "$BUILD_DIR/fdroid-local/dummy.jks"

if [[ ! -d "$FDROID_DATA" ]]; then
  echo "Brak katalogu fdroiddata pod $FDROID_DATA (ustaw FDROID_DATA)." >&2
  exit 1
fi
if [[ ! -f "$CONFIG" ]]; then
  echo "Brak pliku $CONFIG (ustaw FDROID_DATA)." >&2
  exit 1
fi

# Check that the local tag and both recipe copies point at the requested release commit.
verify_release_pin() {
  local tag_commit recipe
  if [[ ! -f "$RECIPE" ]]; then
    echo "Brak receptury $RECIPE." >&2
    exit 1
  fi
  if [[ ! -f "$FDROID_RECIPE" ]]; then
    echo "Brak receptury $FDROID_RECIPE w fdroiddata." >&2
    exit 1
  fi
  if ! tag_commit="$(git -C "$PROJECT_ROOT" rev-parse --verify "refs/tags/${RELEASE_TAG}^{commit}" 2>/dev/null)"; then
    echo "Brak tagu $RELEASE_TAG w repozytorium $PROJECT_ROOT." >&2
    exit 1
  fi
  if [[ "$tag_commit" != "$RELEASE_COMMIT" ]]; then
    echo "Tag $RELEASE_TAG wskazuje $tag_commit, a nie $RELEASE_COMMIT." >&2
    exit 1
  fi
  for recipe in "$RECIPE" "$FDROID_RECIPE"; do
    if ! grep -Eq "^[[:space:]]*commit:[[:space:]]*${RELEASE_COMMIT}[[:space:]]*$" "$recipe"; then
      echo "$recipe nie jest przypięty do $RELEASE_COMMIT." >&2
      exit 1
    fi
  done
  echo "Zweryfikowano $RELEASE_TAG = $RELEASE_COMMIT w recepturze F-Droid."
}
verify_release_pin

# 1. JDK 17 (Jitsi) - downloaded locally by Gradle; the buildserver installs it via sudo.
export JDK17_HOME="${JDK17_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2}"
if [[ ! -x "$JDK17_HOME/bin/java" ]]; then
  echo "Brak JDK 17 pod $JDK17_HOME (ustaw JDK17_HOME)." >&2
  exit 1
fi

# 2. Android SDK.
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "Brak Android SDK pod $ANDROID_HOME (ustaw ANDROID_HOME)." >&2
  exit 1
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# 3. Gradle 9.6 on PATH: F-Droid removes gradlew and the wrapper JAR from the source,
#    so the metadata prebuild replaces ./gradlew with gradle.
gradle_bin="$(find "$HOME/.gradle/wrapper/dists" -maxdepth 5 -type f \
  -path '*/gradle-9.6.0/bin/gradle' -print -quit 2>/dev/null || true)"
if [[ -z "$gradle_bin" ]] && command -v gradle >/dev/null 2>&1; then
  if gradle --version 2>/dev/null | grep -q 'Gradle 9\.6'; then
    gradle_bin="$(command -v gradle)"
  fi
fi
if [[ -n "$gradle_bin" ]]; then
  ln -sfn "$gradle_bin" "$SHIM_DIR/gradle"
else
  echo "Nie znaleziono Gradle 9.6 w ~/.gradle/wrapper/dists ani na PATH." >&2
  exit 1
fi
export PATH="$SHIM_DIR:$PATH"

# 4. Disk-backed TMPDIR: /tmp is tmpfs and too small for Jitsi's npm ci.
export TMPDIR="$TMP_DIR"

# 5. Optionally reuse an existing Jitsi checkout (skips npm ci).
if [[ -z "${JITSI_SOURCE_DIR:-}" && -d "$BUILD_DIR/helmchat-jitsi-sdk" ]]; then
  export JITSI_SOURCE_DIR="$BUILD_DIR/helmchat-jitsi-sdk"
fi
export SKIP_NPM_CI="${SKIP_NPM_CI:-0}"

# 6. Temporary config.yml fix for Ubuntu fdroidserver 2.2.1.
config_patched=0
restore_config() {
  if [[ "$config_patched" == "1" ]]; then
    if [[ -f "$CONFIG_BACKUP" ]]; then
      cp -p "$CONFIG_BACKUP" "$CONFIG"
      echo "Przywrócono oryginalny $CONFIG"
    else
      echo "OSTRZEŻENIE: brak kopii $CONFIG_BACKUP; $CONFIG nie został przywrócony." >&2
    fi
  fi
}
trap restore_config EXIT

if grep -q '{env:' "$CONFIG"; then
  cp -p "$CONFIG" "$CONFIG_BACKUP"
  sed -i \
    -e "s|^gpghome: .*|gpghome: $BUILD_DIR/fdroid-local/gpg|" \
    -e "s|^keystore: .*|keystore: $BUILD_DIR/fdroid-local/dummy.jks|" \
    -e 's|^keystorepass: .*|keystorepass: dummy|' \
    -e 's|^keypass: .*|keypass: dummy|' \
    -e "s|^serverwebroot: .*|serverwebroot: $BUILD_DIR/fdroid-local/webroot|" \
    "$CONFIG"
  chmod 600 "$CONFIG"
  config_patched=1
  echo "Tymczasowo poprawiono $CONFIG (nie commituj tego pliku!)"
fi

versions=("$@")
if (( ${#versions[@]} == 0 )); then
  # F-Droid publishes only the arm64-v8a package (version code 700).
  versions=(700)
fi

overall=0
for version in "${versions[@]}"; do
  log="$LOG_DIR/${APP_ID}-${version}-$(date +%Y%m%d-%H%M%S).log"
  echo "=== Buduję $APP_ID:$version -> $log ==="
  if (cd "$FDROID_DATA" && fdroid build -v "$APP_ID:$version") > "$log" 2>&1; then
    status=0
  else
    status=$?
  fi
  if grep -q 'Could not build' "$log"; then
    status=1
  fi
  if (( status != 0 )); then
    echo "BŁĄD: $version (status=$status). Ostatnie linie:"
    tail -25 "$log"
    overall=1
  else
    echo "OK: $version"
    grep -E 'Successfully built version' "$log" | tail -2 || true
  fi
done

echo "=== Wygenerowane APK ==="
find "$FDROID_DATA" -maxdepth 2 -name 'HelmChatRisen-*.apk' -print 2>/dev/null || true
exit "$overall"
