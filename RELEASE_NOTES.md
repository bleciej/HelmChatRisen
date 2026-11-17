# HelmChat Risen 1.5 — Release Notes

|                      |                                  |
|----------------------|----------------------------------|
| Version              | 1.5                              |
| Version code         | 5                                |
| Tag                  | `V1.5`                           |
| Release date         | 2026-11-17                       |
| Minimum Android      | 8.0 (API 26)                     |
| Target / compile SDK | 37                               |
| Application ID       | `dev.communicator.helmchatrisen` |

HelmChat Risen 1.5 is a maintenance release focused on the package rename to
`dev.communicator.helmchatrisen`, cleaner and better-documented audio/call
internals, and packaging fixes for Android App Bundle builds. It keeps the same
audio-only Jitsi call flow, participant tracking, helmet routing, and
motorcycle-optimized UI introduced in earlier releases.

> **Heads-up:** this release changes the application ID to
> `dev.communicator.helmchatrisen`. Upgrading from a build that used a different
> application ID (including `com.example.helmChatRisen`) installs as a
> **separate app** — see [Installation & upgrade notes](#installation--upgrade-notes).

## Highlights

- **New application ID and package:** the app is now published as
  `dev.communicator.helmchatrisen` (namespace, package and identifiers aligned).
- **Audio routing refactor:** `BluetoothAudioRouter` is reworked with clearer
  API-level routing paths (API 31+ communication-device selection and legacy SCO
  fallback below Android 12), safer receiver registration, and expanded KDoc.
- **Call service cleanup:** `AudioCallService` centralizes connected/reconnecting
  session handling, refreshes the foreground notification consistently, and
  simplifies foreground stop/channel handling.
- **Logging and diagnostics:** logging is migrated to Timber and call error
  reporting is localized.
- **Build and packaging updates:** Kotlin/Compose/Jitsi SDK and related
  dependencies are refreshed, KSP and unused dependencies are removed, and ABI
  splits are automatically disabled for `bundle*` tasks to avoid AAB packaging
  failures.
- **Documentation and code hygiene:** broad KDoc coverage added across audio,
  call, data, UI host, and server/participant tracking code, plus manifest
  clarifications for reviewed cleartext traffic and foreground-service policy.

## Changes since 1.4

### Identity, logging, and dependencies

- Renamed the package and application ID to `dev.communicator.helmchatrisen`
  (`c21b87e`).
- Migrated logging to Timber and localized call errors (`01ad837`).
- Removed KSP, bumped Kotlin, and refreshed Compose/Jitsi SDK and other
  dependencies while pruning unused libraries (`3d9ff9f`, `54b60c8`, `7820020`).
- Upgraded Jitsi Meet SDK to 11.6.3 and raised `minSdk` to 26 to match its
  manifest requirement; dropped the now-unreachable API 24/25 PiP fallbacks.

### Audio routing and call lifecycle

- Refactored `BluetoothAudioRouter` to split speakerphone/Bluetooth routing into
  explicit API 31+ and legacy paths, centralize speakerphone flag handling, and
  keep routing state updates consistent.
- Registered the SCO/"audio becoming noisy" receiver as not exported via
  `ContextCompat.registerReceiver` on supported APIs and documented legacy SCO
  usage where it remains required below Android 12.
- Updated `AudioCallService` to apply connected/reconnecting session state in one
  place, re-post notification updates for mute/connectivity/session changes, use
  simplified foreground stop behavior, and retain microphone-foreground handling
  for the ongoing intercom call.
- Added/expanded KDoc in call, participant tracking, recent-room, language, and
  activity code to document behavior, parameters, and API-level constraints.

### Manifest and build

- Documented the reviewed cleartext-traffic exception in `AndroidManifest.xml`
  (self-hosted `http://` Jitsi servers remain possible, while curated presets are
  upgraded to `https` by `JitsiCallManager.sanitizeServerUrl`).
- Added manifest/tooling annotations for the foreground-service policy and target
  API context, and removed the redundant launcher activity label in favor of the
  application label.
- Bumped `versionName` to `1.5` (with `versionCode` at `5`).
- Disabled ABI splits when a `bundle*` Gradle task is requested so AAB builds do
  not fail with "Multiple shrunk-resources files found" (see Google issue
  402800800); APK builds continue to produce per-ABI and universal outputs.

## Core functionality (carried forward)

- **Audio-only group calls** on any Jitsi server with low-bandwidth/car-mode
  defaults (video disabled, Opus capped at 24 kbps) and forced WebRTC noise
  suppression, echo cancellation, and automatic gain control.
- **Participant tracking:** live participant count including the local rider,
  authoritative snapshot merge on join, buffered replay of in-flight join/leave
  events, request/session invalidation, and snapshot timeout fallback.
- **Motorcycle-optimized UI:** high-contrast dark theme, glove-friendly controls
  (large mic/leave/drop-down targets), haptic feedback, adaptive phone/tablet
  layout (Compose Material 3 Adaptive + Navigation 3), Picture-in-Picture call
  controls, and room sharing.
- **Helmet and background audio:** Bluetooth SCO/HFP helmet routing with
  helmet/speaker toggle, foreground microphone service with mute/end notification
  actions, and a one-time battery-optimization exemption prompt.
- **Servers and rooms:** curated public Jitsi server list, custom/self-hosted
  servers, optional JWT token, recent rooms (last 8) with quick re-join/edit/delete.
- **Localization:** 30 supported locales with an in-app picker (flags,
  alphabetical, persistent) and Android 13+ per-app language integration.

## Permissions

| Permission                                                               | Purpose                                     |
|--------------------------------------------------------------------------|---------------------------------------------|
| `RECORD_AUDIO`                                                           | Voice transmission                          |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`                      | Helmet/headset audio routing                |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`                    | Keep the call alive in the background       |
| `POST_NOTIFICATIONS`                                                     | Ongoing call notification                   |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`                                   | Prevent Android from killing the connection |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` | Core connectivity and audio                 |

> `usesCleartextTraffic` remains enabled for rider-supplied self-hosted servers
> reachable only over HTTP. Curated preset servers are always sanitized to HTTPS.

## Build & packaging

- Gradle (Kotlin DSL), AGP 9, version catalog; Java 11 bytecode target (JDK 17+
  required to build).
- SDK levels: `compileSdk 37` / `targetSdk 37` / `minSdk 26` (required by Jitsi
  Meet SDK 11.6.3).
- Release APK builds: **per-ABI** (`arm64-v8a`, `armeabi-v7a`) plus a universal
  APK, R8-minified and resource-shrunk.
- **App Bundle builds (`bundle*`)**: ABI splits are disabled automatically to
  produce a single AAB-compatible shrunk-resources output.
- Release signing is supplied out-of-band via Gradle properties
  `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, and
  `releaseKeyPassword`; signing material is never committed.
- Deterministic output names: `HelmChatRisen-1.5-release-<abi>.apk` and
  `HelmChatRisen-1.5-release.apk`.
- The build patches the merged Jitsi JS bundle to shorten reconnect delays; its
  patterns ignore minified identifier names and fail loudly if the JS logic
  changes in a future SDK upgrade.
- Version metadata: `versionName` `1.5`, `versionCode` `5`.

## Testing

- Existing JVM unit tests cover server URL normalization/preset matching, recent
  room merging, JWT handling, room-link encoding, language catalog ordering, the
  join-form view model, participant id parsing/snapshot merging, session/request
  invalidation, the participant state machine (pending snapshot window, buffered
  replay, deadline, local-rider accounting), and participant-session liveness.
- Recommended verification for this release: `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`, and `./gradlew lintDebug` (and SonarQube
  inspections in the IDE/CI), plus release/AAB packaging checks:
  `./gradlew assembleRelease` and `./gradlew bundleRelease`.
- Manual/device verification is still important for Bluetooth helmet routing,
  foreground microphone behavior across API levels, Picture-in-Picture/call
  continuity, and multi-rider participant counting.

## Known limitations

- Multi-rider participant counting remains covered by unit tests but still needs
  confirmation in real multi-device rides.
- `meet.jit.si` may require moderator sign-in via the browser before joining from
  the app; password-protected rooms may not work in the embedded view.
- Unreachable or misconfigured servers surface a localized error after the
  connection timeout.
- Physical verification of Bluetooth helmet intercom routing on specific headsets
  remains environment-dependent.

## Installation & upgrade notes

- **Application ID:** `dev.communicator.helmchatrisen`.
- Builds that used a previous application ID (for example
  `com.example.helmChatRisen` or older pre-release IDs) install as a **separate
  app**. Data such as recent rooms, JWT tokens, and the language preference is
  **not migrated**; uninstall the old build to avoid duplicate launcher icons.
  upgrades depends on the previously installed build's application ID and version
  code.
- App label: `HelmChat Risen`; in-app title: `HelmChat - Risen`.
- Release signing configuration is required for signed release artifacts; debug
  builds work without it.
- Release APKs: `HelmChatRisen-1.5-release-arm64-v8a.apk`,
  `HelmChatRisen-1.5-release-armeabi-v7a.apk`, and the universal
  `HelmChatRisen-1.5-release.apk`.

## Building release artifacts

```bash
git checkout V1.5   # once the release tag exists
./gradlew assembleRelease
# For Play/App Bundle packaging:
./gradlew bundleRelease
```

APKs are written under `app/build/outputs/apk/release/` and bundles under
`app/build/outputs/bundle/release/`. Any manual copy under `app/release/` is
local-only and ignored by Git.
