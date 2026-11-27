#!/usr/bin/env bash
# Buildserver dependencies for the source-built Jitsi SDK and the app build.
# F-Droid runs this through its metadata "sudo" field.
set -euo pipefail

readonly GRADLE_VERSION="9.6.0"
readonly GRADLE_SHA256="bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
readonly GRADLE_INSTALL_DIR="${GRADLE_INSTALL_DIR:-/opt/gradle-${GRADLE_VERSION}}"
readonly GRADLE_LINK="${GRADLE_LINK:-/usr/local/bin/gradle}"
readonly GRADLE_TMP_DIR="${GRADLE_TMP_DIR:-/var/tmp}"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y \
  build-essential \
  ca-certificates \
  curl \
  git \
  python3 \
  unzip \
  xz-utils \
  openjdk-17-jdk-headless \
  openjdk-21-jdk-headless

# The buildserver's system Gradle is too old for AGP 9.5.2. Install the same
# checksum-pinned distribution as the committed wrapper and expose it on PATH.
tmp_dir="$(mktemp -d "$GRADLE_TMP_DIR/helmchat-gradle.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

archive="$tmp_dir/gradle.zip"
curl -fsSL -o "$archive" \
  "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
echo "${GRADLE_SHA256}  ${archive}" | sha256sum -c -
unzip -q "$archive" -d "$tmp_dir"

install -d -m 0755 "$(dirname "$GRADLE_INSTALL_DIR")"
rm -rf "$GRADLE_INSTALL_DIR"
mv "$tmp_dir/gradle-${GRADLE_VERSION}" "$GRADLE_INSTALL_DIR"
install -d -m 0755 "$(dirname "$GRADLE_LINK")"
ln -sfn "$GRADLE_INSTALL_DIR/bin/gradle" "$GRADLE_LINK"

"$GRADLE_LINK" --version
