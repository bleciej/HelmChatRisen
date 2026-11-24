#!/usr/bin/env bash
# Builds the pinned Jitsi Meet SDK from source with LIBRE_BUILD=true and publishes
# every generated Maven artifact to the local repository used by this project.
#
# The built AARs are intentionally NOT committed: F-Droid builds them from source
# in its recipe, and local builds must use the same reproducible input.
#
# Local toolchain: build/helmchat-jitsi-build/node24 and .../jdk17.
# Source checkout: build/helmchat-jitsi-sdk (both gitignored via /build).
set -euo pipefail

readonly PROJECT_ROOT="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly JITSI_REPO="https://github.com/jitsi/jitsi-meet.git"
readonly JITSI_TAG="mobile-sdk-13.1.1"
# Pin the tag to an immutable commit so F-Droid and local builds use identical inputs.
readonly JITSI_TAG_COMMIT="1a308a6873098c038cf264f3d8f5378bb975601b"
readonly SDK_VERSION="13.1.1"
# Keep the checkout on disk: node_modules alone is several GiB and /tmp is often tmpfs.
readonly WORK_DIR="${JITSI_SOURCE_DIR:-$PROJECT_ROOT/build/helmchat-jitsi-sdk}"
readonly LOCAL_MAVEN_REPO="${LOCAL_MAVEN_REPO:-$HOME/.m2/repository}"

if [[ -z "${ANDROID_HOME:-}" && -f "$PROJECT_ROOT/local.properties" ]]; then
  local_sdk_dir="$(grep -m1 '^sdk.dir=' "$PROJECT_ROOT/local.properties" | cut -d= -f2- || true)"
  if [[ -n "$local_sdk_dir" ]]; then
    export ANDROID_HOME="$local_sdk_dir"
    export ANDROID_SDK_ROOT="$local_sdk_dir"
  fi
fi

if ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 24 ? 0 : 1)' 2>/dev/null; then
  echo "Node.js 24 or newer is required to install Jitsi $JITSI_TAG dependencies." >&2
  echo "Set PATH so that 'node' and 'npm' point to the Node 24 installation." >&2
  exit 1
fi

if [[ ! -d "$WORK_DIR/.git" ]]; then
  echo "Cloning $JITSI_TAG into $WORK_DIR"
  git clone --depth 1 --branch "$JITSI_TAG" "$JITSI_REPO" "$WORK_DIR"
fi

source_commit="$(git -C "$WORK_DIR" rev-parse HEAD)"
if [[ "$source_commit" != "$JITSI_TAG_COMMIT" ]]; then
  echo "Unexpected Jitsi checkout $source_commit; expected $JITSI_TAG_COMMIT for $JITSI_TAG." >&2
  exit 1
fi

cd "$WORK_DIR"

if [[ "${SKIP_NPM_CI:-0}" != "1" ]]; then
  echo "Installing JavaScript dependencies for Jitsi $SDK_VERSION"
  npm ci --no-audit --no-fund
fi

# The autolinking manifest stores absolute paths, so regenerate it when the
# checkout has been moved (for example into a different build directory).
echo "Regenerating Android autolinking metadata"
npm run android-autolinking

# CMake stores absolute paths in .cxx; after moving the checkout its clean task
# would try to delete a directory at the old location.
echo "Clearing stale CMake caches"
npm run android-clean-cmake-cache

# react-native-device-info still ships an install-attribution dependency and
# optional Firebase/GMS IID hooks in its published POM. Jitsi's own SDK excludes
# those groups on its dependency, but the module's POM is generated separately,
# so they must be removed at the source before publishing.
readonly DEVICE_INFO_GRADLE="node_modules/react-native-device-info/android/build.gradle"
if [[ ! -f "$DEVICE_INFO_GRADLE" ]]; then
  echo "Missing $DEVICE_INFO_GRADLE after npm ci" >&2
  exit 1
fi
awk '
  /implementation "com\.android\.installreferrer:installreferrer:/ { skipping = 1; next }
  skipping && /^  }$/ { skipping = 0; next }
  !skipping { print }
' "$DEVICE_INFO_GRADLE" > "$DEVICE_INFO_GRADLE.tmp"
mv "$DEVICE_INFO_GRADLE.tmp" "$DEVICE_INFO_GRADLE"

# Gradle 8.14 rejects the sources JAR reading generated codegen output without an
# explicit task dependency. Upstream's release script has not been updated yet.
python3 - <<'PY'
from pathlib import Path

path = Path("android/build.gradle")
text = path.read_text()
needle = "    from android.sourceSets.main.java.source\n"
addition = "    dependsOn tasks.matching { it.name == 'generateCodegenArtifactsFromSchema' }\n"
if needle not in text:
    raise SystemExit("Missing jitsiAndroidSourcesJar source declaration in android/build.gradle")
if addition not in text:
    text = text.replace(needle, needle + addition, 1)

aar_needle = '''                    artifact("${project.buildDir}/outputs/aar/${project.name}-release.aar") {
                        extension "aar"
                    }
'''
aar_addition = '''                    artifact("${project.buildDir}/outputs/aar/${project.name}-release.aar") {
                        extension "aar"
                        builtBy tasks.matching { it.name == 'bundleReleaseAar' }
                    }
'''
if aar_addition not in text:
    if aar_needle not in text:
        raise SystemExit("Missing AAR publication artifact in android/build.gradle")
    text = text.replace(aar_needle, aar_addition, 1)

path.write_text(text)
PY

mkdir -p "$LOCAL_MAVEN_REPO"
readonly MVN_REPO_URI="file:$(realpath "$LOCAL_MAVEN_REPO")"

echo "Building and publishing libre Jitsi $SDK_VERSION to $MVN_REPO_URI"
cd "$WORK_DIR/android"
export LIBRE_BUILD=true
export MVN_REPO="$MVN_REPO_URI"
./gradlew clean --console=plain
./gradlew assembleRelease --console=plain
./gradlew publish --console=plain

echo "Verifying POM metadata for non-FLOSS groups in $LOCAL_MAVEN_REPO"
if grep -RilE 'play-services|firebase|installreferrer|amplitude|giphy|google-signin' \
  --include='*.pom' --include='*.module' \
  "$LOCAL_MAVEN_REPO/com/facebook/react" \
  "$LOCAL_MAVEN_REPO/org/jitsi/react" 2>/dev/null; then
  echo "Non-FLOSS dependency metadata found in the local Maven repository." >&2
  exit 1
fi

echo "Jitsi $SDK_VERSION libre SDK is ready in $LOCAL_MAVEN_REPO"
