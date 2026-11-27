---
name: Shell Scripts
description: Author and maintain project shell scripts in scripts/ - English comments, Bash strict mode, safe F-Droid build helpers, environment setup, logging, and no secret/build-output commits.
---

# Shell scripts for HelmChatRisen

Use this skill when creating or editing `*.sh` files, especially the F-Droid
build helpers under `scripts/`.

## Style rules

- Start with `#!/usr/bin/env bash`.
- **Write all comments in English.** This is a project preference.
- Keep user-facing console messages in Polish unless the user asks otherwise;
  Polish is the primary language for rider-facing text.
- Use `set -euo pipefail` by default. If a failure must be collected for later
  handling, put the command in an `if`/`else` block so `errexit` does not exit
  before the status is handled, instead of dropping `-e`.
- Resolve paths from `BASH_SOURCE[0]` instead of depending on the current
  directory:

  ```bash
  readonly SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  readonly PROJECT_ROOT="$(cd -P "$SCRIPT_DIR/.." && pwd)"
  ```

- Quote variables, prefer `[[ ... ]]` for Bash conditions, and expose local
  defaults through environment overrides (`${VAR:-default}`).
- Thin per-version helpers should `exec` the shared script so signals and exit
  codes are preserved.
- Validate every changed script with `bash -n`; run `shellcheck` when available.

## Failure patterns to avoid

These caused real defects in the F-Droid helpers; check for them before editing:

- Do not interpolate a variable that was never assigned. Project paths are
  uppercase (`CONFIG`, `BUILD_DIR`); messages such as `echo "$config.yml"`
  abort under `set -u` and must use `${CONFIG}`.
- Do not patch a tracked file before installing its restore trap. Define the
  restore function and `trap restore_config EXIT` before `cp`/`sed`/`chmod`.
- Do not restore permissions with a hardcoded `chmod`. Back up with `cp -p`
  and restore with `cp -p`, so the original mode is preserved.
- Do not drop `-e` just to capture a failing command's status. Use
  `if command; then status=0; else status=$?; fi` instead of `set -uo pipefail`
  followed by `command; status=$?`.
- Do not assume a `sed` replacement is harmless. Verify the rewritten command
  (for example `gradle assembleRelease`) and confirm the resolved tool version
  with `gradle --version` or the distribution checksum.
- Do not commit local IDE/device state. Keep `.idea/deploymentTargetSelector.xml`
  ignored and never leave a physical-device serial in a tracked file.

## Project layout

- `scripts/` — committed helper scripts; keep them small and reviewable.
- `build/` — gitignored logs, temporary downloads, JDK/Gradle shims and local
  verification checkouts. Generated files belong here, not in `scripts/`.
- `fdroid/` — candidate F-Droid recipe and submission notes.
- Local F-Droid checkout: `/home/burt/AndroidStudioProjects/fdroiddata`
  (override with `FDROID_DATA`). Final F-Droid APKs land in its `unsigned/`
  directory; detailed build logs go to `build/fdroid-logs/`.

## F-Droid helper environment

The current recipe publishes only `arm64-v8a` with F-Droid version code `700`,
derived from upstream code `7` by `VercodeOperation: 100 * %c`. Current helper
scripts are `scripts/fdroid-build-local.sh` and
`scripts/fdroid-build-700.sh`.

Local fdroidserver 2.2.1 needs these workarounds:

- `JDK17_HOME` must be **exported** for Jitsi's Gradle toolchain; JDK 21 is the
  app Gradle daemon toolchain.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` must point to the installed SDK.
- `TMPDIR` must be disk-backed, e.g. `build/fdroid-tmp`; `/tmp` is tmpfs and
  Jitsi's `npm ci` can exhaust it.
- F-Droid strips `gradlew` and the wrapper JAR, so the metadata `prebuild`
  replaces `./gradlew` with `gradle`; the `sudo` helper installs the pinned
  Gradle 9.6 on `PATH`. Locally, create a Gradle 9.6 shim under
  `build/gradle-shim` and prepend it to `PATH`.
- Reusing `build/helmchat-jitsi-sdk` with `JITSI_SOURCE_DIR` and
  `SKIP_NPM_CI=1` avoids a second npm install.
- `fdroiddata/config.yml` uses `{env: ...}` values that Ubuntu fdroidserver
  2.2.1 cannot parse. Back it up with `cp -p`, patch the values to throwaway
  local paths, set mode `0600`, and restore it with an `EXIT` trap installed
  before patching. Never commit that file.

## Safety checklist

- Never commit `keystore.jks`, signing passwords, `config.yml`, APKs, logs,
  `build/` outputs, or IDE folders such as `.idea/`.
- Do not commit app-repository changes unless the user explicitly asks.
  Current shell helpers are intentionally uncommitted.
- Ask before destructively deleting modules, changing `applicationId`, or
  changing release versioning.
- Prefer adding a new commit in `fdroiddata` over rewriting an existing one
  unless the user explicitly requests a squash or amend.

## Verification checklist

- `bash -n <script>` for every changed script.
- Grep changed scripts for stale variable names (for example `$config.yml`) and
  verify every `sed` replacement against the actual target file.
- For helpers that patch external files, simulate both success and failure with
  fake commands; confirm the patched file is restored byte-for-byte with its
  original permissions.
- `fdroid readmeta`, `fdroid rewritemeta <appid>`, `fdroid lint <appid>` and
  `fdroid build -v <appid>:<code>` after metadata or toolchain changes.
- For APK changes, check the built artifact:

  ```bash
  aapt dump badging <apk> | grep -E '^package:|native-code|sdkVersion:'
  ```

- Check Git status in both repositories before declaring work complete; only
  intended metadata should be staged or committed.
