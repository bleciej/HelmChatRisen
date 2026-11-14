# HelmChat Risen 1.4 — Release Notes

| | |
|---|---|
| Version | 1.4 |
| Version code | 4 |
| Tag | `V1.4` (pending) |
| Release date | 2026-11-14 |
| Minimum Android | 7.0 (API 24) |
| Target / compile SDK | 37 |
| Package | `com.example.helmChatRisen` |

HelmChat Risen 1.4 is the first public release of the motorcycle voice intercom
app. It delivers the full audio-only Jitsi call flow with a correct multi-rider
participant count, helmet audio routing, the glove-friendly Compose UI,
30-language support, and release packaging with per-ABI APKs.

> **Heads-up:** this is the first public version, so there is no earlier public
> build to upgrade from. Internal pre-release builds may use a different
> application ID and install as a separate app — see
> [Installation & upgrade notes](#installation--upgrade-notes).

## Highlights

- **Audio-only group calls** on any Jitsi server — video streams are disabled
  (`startAudioOnly`, `startWithVideoMuted`, `startLastN=0`, `channelLastN=0`) and
  Opus is capped at 24 kbps to keep data usage and battery drain low.
- **Correct participant count** for multi-rider rooms, including riders who were
  already in the room when you joined.
- **Cleaner helmet audio.** WebRTC noise suppression, echo cancellation and
  automatic gain control are enabled in every conference.
- **Motorcycle-optimized UI** with a 168 dp mic toggle, 84 dp leave button and
  76 dp drop-downs, high-contrast dark theme, and haptic feedback for gloved use.
- **Helmet intercom routing** via Android `AudioManager` (Bluetooth SCO/HFP) with
  a dedicated helmet/speaker toggle for A2DP and HFP headsets.
- **30 languages** (English plus 29 translations) with an in-app picker (flags,
  alphabetical, persists across restarts) and full Android 13+ per-app language
  integration.
- **Curated public server list** plus custom/self-hosted instances and optional
  JWT authentication.
- **Recent rooms** (last 8) that persist across restarts and can be re-joined,
  edited or deleted with one tap.
- **Invite friends** by sharing a room name + browser-openable link from the
  join screen or an active call.

## Features

### Calling

- Join or create an audio room on any Jitsi server from the home screen.
- Embedded `JitsiMeetView` with low-bandwidth "car mode" defaults and disabled
  prejoin/welcome screens.
- Connection status pill, live participant count, mute/unmute state and
  disconnect handling on the call screen.
- 30 s connection timeout that surfaces a localized error instead of an endless
  "connecting" state.
- Default Jitsi display name (`Motocyklista`) so the hidden Jitsi UI never opens
  its name prompt behind the custom overlay.
- `meet.jit.si` awareness: explains moderator-auth limitations and can open the
  room in the system browser so the moderator can sign in before joining from
  the app.

### Participant count

- Participant ids are read from the `participantId` broadcast extra that Jitsi
  10.2.0 sends, with the legacy `id` extra as a fallback; blank and
  stringified-`null` values are rejected.
- On `CONFERENCE_JOINED` the app requests an authoritative participant snapshot
  from `ParticipantsService`. This counts riders who were already in the room
  and repairs any event the app may have missed.
- Join/leave events received while the snapshot is in flight are buffered and
  replayed on top of it, so an older snapshot cannot overwrite a newer count.
- Snapshot responses are guarded by a per-call session id and a request id, so a
  late response from a previous call or an older request is ignored.
- A snapshot request is bounded by a 5 s deadline: if `ParticipantsService`
  never answers, the app falls back to the broadcast event stream, replays the
  joins and leaves observed in the meantime, and releases the callback instead
  of freezing the counter.
- The local rider is counted by presence, so a failed, unavailable or empty
  snapshot can no longer report one rider too few.
- Participant ids from both the broadcast and the snapshot paths are validated
  identically; blank, `null` and `undefined` values are rejected and only
  string-like extras can become ids.
- A `CONFERENCE_JOINED` delivered after `hangUp()` or `onViewReleased()` is
  ignored, and `hangUp()` unregisters the broadcast receiver so stale events
  cannot re-arm participant tracking.

### Audio & background

- Foreground microphone service (`AudioCallService`) with an ongoing notification
  offering mute and end actions, keeping the call alive in the background.
- One-time request for battery-optimization exemption so Android does not kill
  the connection on the road.
- Forced WebRTC audio filters: noise suppression, echo cancellation and
  automatic gain control.
- Bluetooth SCO/HFP routing for motorcycle helmet intercoms with a
  helmet/speaker toggle.
- Motorcycle-tuned reconnection: the bundled Jitsi JS is patched at build time
  to shorten conference reload (~5 s) and websocket retry (~5 s) delays.

### Home screen & navigation

- Room name, rider name, server selection and optional JWT token fields.
- Glove-friendly "Recent rooms" card with per-room delete and "clear all".
- 76 dp server drop-down listing public Jitsi instances with short descriptions,
  plus a "Custom server" switch revealing URL/JWT fields.
- 76 dp "Invite" button on the join card and a 64 dp share icon on the call
  screen.
- Pinned language action in a fixed top bar above the scrolling form.
- Adaptive layout: one pane on phones, list-detail side-by-side on tablets and
  foldables (Compose Material 3 Adaptive + Navigation 3).

### Localization

- English is the default catalog; Polish is available via `values-pl`.
- 30 supported locales: `bg`, `cs`, `da`, `de`, `el`, `en`, `es`, `et`, `fi`,
  `fr`, `he`, `hi`, `hr`, `hu`, `it`, `ja`, `lt`, `lv`, `nl`, `no`, `pl`,
  `pt`, `ro`, `ru`, `sk`, `sr`, `sv`, `tr`, `uk`, `zh`.
- In-app language picker with flags, alphabetical ordering, persistence across
  restarts and Android 13+ per-app language integration (system settings).
- The launcher label is a brand name and is not translated.

### Branding & assets

- Launcher icon generated from the supplied `helmchat.svg` artwork (adaptive,
  legacy square/round and monochrome themed variants for all densities).
- Home header uses the logo on a circular badge.
- Always-dark app theme with versioned theme resources (`values` / `values-v31`).
- Launcher label: `HelmChat Risen`; in-app title: `HelmChat - Risen`.

## Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Voice transmission |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` | Helmet/headset audio routing |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Keep the call alive in the background |
| `POST_NOTIFICATIONS` | Ongoing call notification |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the connection |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` | Core connectivity and audio |

## Build & packaging

- Gradle (Kotlin DSL), AGP 9, KSP, version catalog.
- SDK levels: compileSdk 37 / targetSdk 37 / minSdk 24.
- Java 11 compatibility; JDK 17+ required to build.
- Release builds are **per-ABI** (`arm64-v8a`, `armeabi-v7a`), R8-minified and
  resource-shrunk; a universal APK is also produced.
- Release signing is supplied out-of-band via the Gradle properties
  `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias` and
  `releaseKeyPassword`; signing material is never committed.
- Output names are deterministic: `HelmChatRisen-1.4-release-<abi>.apk` and
  `HelmChatRisen-1.4-release.apk`.
- The build patches the merged Jitsi JS bundle to shorten reconnect delays and
  fails loudly if the Jitsi SDK is upgraded and the patch targets change.
- Version metadata: `versionName` `1.4`, `versionCode` `4`.
- The untranslated brand name is declared once in the default catalog; locale
  catalogs do not duplicate it.
- Generated release artifacts in `app/release/` are ignored by version control.

## Testing

- JVM unit tests (97) cover server URL normalization, preset matching,
  recent-room merging, JWT handling, room-link encoding, language catalog
  ordering, the join-form view model, participant id parsing, snapshot merging,
  snapshot session/request invalidation, the participant state machine
  (pending-snapshot window, buffered replay, deadline, local-rider accounting)
  and the participant-session liveness predicate.
- Verification on the `Medium_Phone_API_37.0` emulator covered launch, the
  runtime permission dialog, audio-only join, mute/unmute (UI + notification),
  speaker/helmet toggle, rotation/adaptive layout, the background foreground
  service, notification teardown and clean leave/back navigation. No crashes
  were observed in those runs.
- `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` pass for
  this build.
- Multi-rider verification of the participant counter on real devices is still
  required (see [Known limitations](#known-limitations)).

## Known limitations

- Manual verification of the participant counter with two or more real riders is
  still pending. The logic is covered by unit tests but has not been exercised on
  a physical multi-rider call.
- `meet.jit.si` requires moderator sign-in via the browser before joining from
  the app; password-protected rooms may not work in the embedded view.
- Unreachable/misconfigured servers surface a localized error after the 30 s
  timeout.
- Physical device verification of Bluetooth helmet intercom routing is pending
  where noted in the project plan.

## Installation & upgrade notes

> This is the first public release; install it as a fresh app.

- Application ID: `com.example.helmChatRisen`.
- Any internal pre-release build that used a different application ID (for
  example `com.example.helmchatreborn`) installs as a **separate app**. Data such
  as recent rooms, JWT tokens and the language preference is **not migrated**;
  uninstall the old build to avoid two launcher icons.
- `versionCode` is `4`, leaving room for future in-place updates.
- App label: `HelmChat Risen`; in-app title: `HelmChat - Risen`.
- A release signing configuration is required for a signed release build; debug
  builds work without it.
- Release APKs: `HelmChatRisen-1.4-release-arm64-v8a.apk`,
  `HelmChatRisen-1.4-release-armeabi-v7a.apk` and the universal
  `HelmChatRisen-1.4-release.apk`.

## Building the release APKs

```bash
git checkout V1.4   # once the release tag exists
./gradlew assembleRelease
```

The APKs are written under `app/build/outputs/apk/release/`. A manual copy under
`app/release/` is local-only and ignored by Git.
