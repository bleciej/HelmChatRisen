#!/usr/bin/env bash
# F-Droid buildserver entry point: bootstrap the pinned Node.js toolchain,
# publish the libre Jitsi SDK from source, then assemble the release APKs.
#
# The metadata in fdroid/dev.communicator.helmchatrisen.yml installs the JDKs
# via its sudo field and calls this script from the build phase.
set -euo pipefail

readonly PROJECT_ROOT="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly NODE_VERSION="24.22.3"
readonly NODE_SHA256="4f2b175a6d53a5c617a80dba2676238a0688cef28c6da4eba35c7d844e66fcfb"
readonly NODE_DIR="$PROJECT_ROOT/build/helmchat-jitsi-build/node24"
readonly JDK17_HOME="${JDK17_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
readonly JDK21_HOME="${JDK21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

for jdk in "$JDK17_HOME" "$JDK21_HOME"; do
  if [[ ! -x "$jdk/bin/java" ]]; then
    echo "Missing JDK at $jdk; install openjdk-17-jdk-headless and openjdk-21-jdk-headless." >&2
    exit 1
  fi
done

node_archive_dir="$(mktemp -d)"
trap 'rm -rf "$node_archive_dir"' EXIT
node_archive="$node_archive_dir/node.tar.xz"
curl -fsSL -o "$node_archive" \
  "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz"
echo "${NODE_SHA256}  ${node_archive}" | sha256sum -c -
mkdir -p "$NODE_DIR"
tar -xJf "$node_archive" -C "$NODE_DIR" --strip-components=1

export PATH="$NODE_DIR/bin:$PATH"
export JAVA_HOME="$JDK21_HOME"
export GRADLE_OPTS="-Dorg.gradle.java.installations.paths=$JDK17_HOME -Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8"

cd "$PROJECT_ROOT"
./scripts/build-jitsi-libre-sdk.sh
./gradlew assembleRelease --console=plain --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"
