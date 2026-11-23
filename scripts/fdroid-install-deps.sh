#!/usr/bin/env bash
# Buildserver dependencies for the source-built Jitsi SDK and the app build.
# F-Droid runs this through its metadata "sudo" field.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y \
  build-essential \
  curl \
  git \
  python3 \
  xz-utils \
  openjdk-17-jdk-headless \
  openjdk-21-jdk-headless
