# HelmChat Risen — Release Notes

|                      |                                  |
|----------------------|----------------------------------|
| Version              | 1.6                              |
| Version code         | 6                                |
| Tag                  | `V1.6`                           |
| Release date         | 2026-11-22                       |
| Last updated         | 2026-11-22                       |
| Minimum Android      | 8.0 (API 26)                     |
| Target / compile SDK | 37 / 37.2                        |
| Application ID       | `dev.communicator.helmchatrisen` |

Release history in chronological order (oldest first). The current release
candidate is **1.6** on branch `V1.6`; create the `V1.6` tag from the final
release commit before publishing. The shared sections at the end describe the
functionality and build/upgrade rules that apply to the current build.

## Release history

- [1.4 — 2026-11-14](#14--2026-11-14)
- [1.5 — 2026-11-17](#15--2026-11-17)
- [1.6 — 2026-11-22 (current)](#16--2026-11-22-current)

## Release matrix

| Release | Version code | Tag    | Release date                          | Application ID                     | Minimum Android | Target / compile SDK |
|---------|-------------:|--------|---------------------------------------|------------------------------------|-----------------|----------------------|
| 1.4     | 4            | `V1.4` | 2026-11-14                            | `com.example.helmChatRisen`        | 7.0 (API 24)    | 37 / 37              |
| 1.5     | 5            | `V1.5` | 2026-11-17 (tag commit 2026-11-19)    | `dev.communicator.helmchatrisen`   | 8.0 (API 26)    | 37 / 37              |
| 1.6     | 6            | `V1.6` | 2026-11-22 (candidate 4b48505; tag pending) | `dev.communicator.helmchatrisen` | 8.0 (API 26) | 37 / 37.2 |

## 1.4 — 2026-11-14

### Summary

The first release documented in this file. It established the audio-only
Jitsi intercom experience and made participant counting reliable when riders
join a room that already has other participants.

### Highlights

- **Robust participant tracking:** a dedicated `ParticipantTracker` adds an
  authoritative snapshot on join, buffers in-flight join/leave events, and
  counts the local rider by presence.
- **Audio-only group calls:** video disabled, car-mode/low-bandwidth defaults,
  Opus capped at 24 kbps, and forced noise suppression, echo cancellation and
  automatic gain control.
- **Motorcycle-optimized UI:** high-contrast dark theme, large glove-friendly
  controls, haptics, adaptive phone/tablet layout, Picture-in-Picture call
  controls and room sharing.
- **Helmet audio:** Bluetooth SCO/HFP routing with a helmet/speaker toggle,
  foreground microphone service with mute/end notification actions, and a
  one-time battery-optimization exemption prompt.
- **Servers and rooms:** curated public Jitsi servers, custom/self-hosted
  servers, optional JWT token, recent rooms (last 8) with re-join/edit/delete.
- **Localization:** 30 locales with an in-app flag picker and Android 13+
  per-app language integration.

### Changes

#### Participant tracking

- Replaced the simple participant ID set/counter with `ParticipantTracker`
  (`198915f`).
- Read Jitsi `participantId` broadcast extras with a legacy `id` fallback and
  reject blank/`null`/`undefined` values.
- Request an authoritative `ParticipantsService` snapshot on
  `CONFERENCE_JOINED`; guard it by call session and request ID.
- Buffer and replay join/leave events received while a snapshot is in flight;
  fall back to broadcast events after a 5 s deadline.
- Ignore late `CONFERENCE_JOINED` events after hang-up and unregister the
  receiver on teardown.

#### App identity and documentation

- Changed the launcher label to `HelmChat Risen` and marked it
  `translatable="false"`; removed duplicated `app_name` entries from 29 locale
  catalogs.
- Added the first consolidated `RELEASE_NOTES.md` and ignored `/app/release/` in
  `.gitignore`.

#### Build and verification

- Gradle 9.6.0, AGP 9.5.1, Kotlin 2.2.10, KSP 2.3.8, Compose BOM 2026.05.01,
  Navigation 3 1.1.2, Jitsi Meet SDK 10.2.0, minSdk 24 and target/compileSdk 37.
- Java 11 source compatibility with JDK 17+ required to build; R8-minified and
  resource-shrunk release APKs for `arm64-v8a`, `armeabi-v7a` and universal.
- The build patched the Jitsi JS bundle to shorten reload/websocket retry
  delays and failed loudly if the SDK patch targets changed.
- 97 JVM tests were present at the tag; `assembleDebug`, `testDebugUnitTest`,
  `lintDebug` and `assembleRelease` were reported passing, plus an emulator
  smoke test on `Medium_Phone_API_37.0`.

### Limitations at 1.4

- Multi-rider participant counting had not been confirmed on physical devices.
- `meet.jit.si` could require browser moderator sign-in and password-protected
  rooms might not work in the embedded view.
- Unreachable servers surfaced a localized error after 30 s.
- Physical Bluetooth helmet routing verification was still pending.

## 1.5 — 2026-11-17

> **Upgrade warning:** 1.5 changed the application ID from
> `com.example.helmChatRisen` to `dev.communicator.helmchatrisen`. Upgrading
> from 1.4 installs as a **separate app**; recent rooms, JWT tokens and the
> language preference are not migrated.

### Summary

A platform and stability release. It moved the app to its final package name,
upgraded Jitsi to 11.6.3 (raising the minimum Android version to 8.0),
refactored routing and call lifecycle handling, added reliable teardown and an
in-app info dialog, and fixed Android App Bundle packaging.

### Highlights

- **New application ID:** package, namespace, service actions and all sources
  moved to `dev.communicator.helmchatrisen` (`c21b87e`).
- **Jitsi 11.6.3 and minSdk 26:** Android 7.0/7.1 support and the unreachable
  API 24/25 PiP fallbacks were removed (`54b60c8`, `7ac9660`).
- **Audio routing refactor:** explicit API 31+ communication-device and legacy
  SCO paths, centralized speakerphone flags, safer receiver registration and
  expanded KDoc (`45f01b9`).
- **Reliable teardown:** a new `Ending` call state, hang-up confirmation with
  fallback/abort grace periods, and Jitsi view lifecycle ownership prevent the
  microphone from outliving the call (`0b45475`).
- **Call polish:** mute state resets between calls (`9e0a8c8`), Timber logging
  and localized timeout errors (`01ad837`), and an application info dialog
  backed by `BuildConfig` (`0b45475`).
- **Packaging fix:** ABI splits are disabled for `bundle*` tasks so AAB builds
  no longer fail with duplicate shrunk-resources outputs (`45f01b9`).

### Changes

#### Identity and platform

- Renamed the package/application ID and moved service action strings.
- Renamed `values-iw` to `values-he` and removed the redundant launcher
  activity label in favor of the application label.
- Centralized version/release metadata in Gradle and exposed it through
  `BuildConfig` for the in-app info dialog.

#### Dependencies and build

- Kotlin 2.4.21, Compose BOM 2026.09.00, Navigation 3 1.2.1, Lifecycle 2.11.0,
  core-ktx 1.19.1, DataStore 1.3.0, coroutines 1.11.0, serialization 1.13.0 and
  Timber 5.0.1.
- Removed KSP and pruned unused Room, Camera, Coil, Retrofit/Moshi/OkHttp,
  location, Accompanist, Espresso and Compose UI-test dependencies.
- Rewrote the Jitsi JS reconnect patch with structural, idempotent patterns and
  a loud failure when the SDK bundle changes.
- Downgraded the Gradle daemon JVM toolchain from 25 to 21 while keeping app
  source compatibility at Java 11.

#### Audio and call lifecycle

- Split `BluetoothAudioRouter` into API 31+ and legacy SCO routing paths and
  registered the SCO/"audio becoming noisy" receiver as not exported where
  supported.
- Unified `AudioCallService` connected/reconnecting session handling and
  notification refresh; simplified foreground stop handling while retaining
  microphone foreground service behavior.
- Added `CallState.Ending` with a 3 s confirmation fallback and a further 2 s
  abort grace period; kept the call screen, service and notification mounted
  until `Idle`.
- Ignored stale Jitsi events, duplicate leave actions and re-entrant mute
  changes during teardown.

#### UI and documentation

- Added an Application info dialog on the home screen and an "Ending call…"
  state in portrait, PiP and host-setup layouts.
- Locked the activity to portrait orientation while keeping compact PiP
  support.
- Added broad KDoc across audio, call, data, UI host and tracking code, and
  added local project skills for Jitsi SDK, Navigation 3 and Kotlin/Android
  quality guidance.

### Verification and caveats

- Android 7.x devices are no longer supported (`minSdk 26`).
- New app-info and "ending call" strings were supplied only in English and
  Polish; other locales fell back to English.
- `usesCleartextTraffic` remained enabled for rider-supplied HTTP Jitsi
  servers; curated presets were sanitized to HTTPS.
- Real-device verification was still needed for Bluetooth helmet routing, PiP
  continuity and multi-rider participant counting.

## 1.6 — 2026-11-22 (current)

> **Scope note:** branch HEAD `4b48505` contains the dependency bump,
> routing/watchdog work and call-service cleanup. The follow-ups listed below
> are present in the current `V1.6` working tree and must be committed before
> the release tag; they are included in the 1.6 build described here.

### Summary

The current maintenance release. It upgrades the Jitsi SDK and AndroidX stack,
reworks helmet routing and reconnect handling, removes explicit audio-focus
management, and applies the latest invite-copy and PiP-position UI changes.

### Highlights

- **Jitsi 12.1.5 and refreshed dependencies:** Compose BOM 2026.11.01,
  Lifecycle 2.11.1 and Navigation 3 1.2.2 with compileSdk 37.2 (`d24dd93`).
- **Headset routing and reconnect watchdog:** HFP/SCO-only Bluetooth routes,
  wired/USB detection, bounded SCO retries, speaker fallback and Jitsi reload
  deadlines (`907172e`).
- **Audio focus removed:** neither HelmChat nor Jitsi requests or abandons
  audio focus, so background radio/music is not explicitly ducked or paused by
  the app.
- **Background audio preserved:** communication mode is entered once at call
  start; the mid-call watchdog and routing-callback reassertions were removed
  after they caused player/radio audio to duck or disappear on some devices.
- **Reconnect hardening:** failed OEM HFP lookups, last-SCO removal, noisy audio
  and reconnect events are handled without stale cache or latch state.
- **UI updates:** invite text now uses "conversation/rozmowa" wording in all 30
  locales; the PiP icon moved to the top-left and invite stays at the top-right.

### Changes committed in `V1.6`

#### Dependencies and release metadata

- `d24dd93`: upgraded Jitsi Meet SDK 11.6.3 → 12.1.5 and refreshed Lifecycle,
  Compose and Navigation 3 artifacts.
- Raised the compile SDK minor level to 37.2 and added BuildConfig/AppInfo
  metadata support.
- Bumped `versionName` to 1.6 and `versionCode` to 6; updated README and
  release notes.
- Narrowed the Jitsi JS patch to the reload countdown and added a build-time
  assertion if the bundle logic changes.

#### Routing, watchdog and call service

- `907172e`: reworked `BluetoothAudioRouter` with HFP/SCO-only Bluetooth
  routes, wired/USB detection, broadcast plus `AudioDeviceCallback` handling,
  background profile lookups, bounded SCO retries and speaker fallback.
- Extracted testable routing rules (`HeadsetKind`, `RoutePreference`,
  `resolveAudioRouteControl`) and disabled the audio-route button when no
  headset is connected.
- Paused the Jitsi reconnect watchdog while offline and used 60 s reload /
  150 s rejoin deadlines; the join timeout is handed over only after a real
  rejoin.
- Hid Jitsi's audio-device picker because the native layer owns route
  selection.
- `4b48505`: removed unused `AudioCallService.stopCall()` and a redundant SDK
  version check for notification channel creation.

### Current 1.6 follow-ups

#### Call audio

- The app no longer requests or abandons audio focus, and Jitsi's own focus
  handling stays disabled. Platform communication-mode policies can still
  attenuate media on some devices.
- Communication mode is entered exactly once when routing starts. The review's
  1 s recovery watchdog and callback reassertions were removed because repeated
  transitions into call mode made OEM audio policies duck or pause the rider's
  player/radio; `MODE_IN_CALL` and `MODE_RINGTONE` remain telephony-owned,
  including during intercom teardown.
- A silent, streaming `USAGE_VOICE_COMMUNICATION` playback track now stays active
  for the whole call. Jitsi opens that kind of stream only after another rider
  joins; without it, some OEM policies kept `STREAM_MUSIC` on A2DP when the lone
  rider switched intercom audio to the phone speaker. The stream contains only
  zero samples, takes no audio focus and does not duck other apps.

#### Failed-call lifecycle

- `CallState.Error` now tears down the embedded Jitsi view, broadcast receiver
  and network callback immediately, and the foreground microphone service is
  stopped via `AudioCallService`. The error message stays on screen for 4 s
  before `Idle` lets Navigation 3 remove the call route.
- Connect timeouts, reconnect timeouts and join exceptions share that cleanup
  path. Late Jitsi termination/broadcast events are ignored in terminal states,
  so a failed or finished call cannot be revived by stale SDK callbacks.

#### Bluetooth helmet routing and reconnect

- Local SCO teardown for a speaker toggle or wired/USB handover is no longer
  mistaken for a physical headset disconnect, so toggling back restores the
  helmet route. Device-removal and noisy-audio events emitted while the router
  stops SCO are ignored; HFP/profile and plug events remain authoritative.
- The local-stop token is now released only by the resulting SCO DISCONNECTED
  broadcast (or a newly connected channel), not by intermediate CONNECTING
  chatter. Only a genuine SCO disconnect can clear the rider's speaker choice,
  and repeated headset re-enumeration can no longer bounce call audio back to
  the helmet.
- Switching back to the helmet re-checks headset availability instead of
  trusting a remembered "no headset" state, and telephony-owned routes are left
  untouched by both intercom teardown and the next-call reset.
- A failed or unsupported HFP query is no longer treated as proof of a
  disconnect; enumerated SCO devices and real platform events decide the route.
- Removing the last SCO output, or receiving noisy audio with no SCO output,
  clears the cached HFP state even when the profile query is unsupported.
- Suppressed SCO devices are no longer classified as a wired route, which had
  routed call audio to the phone and hidden the named intercom label.
- A timed-out Bluetooth route no longer keeps the speaker latched forever: a
  real headset reconnect restores automatic routing, while an explicit rider
  speaker choice is preserved. A device-added callback with a re-enumerated
  SCO output now clears the stale-signal suppression latch as well, so a helmet
  returning after a confirmed disconnect is routed automatically.
- Transient SCO errors no longer replace the named `Interkom: nazwa` label while
  a headset remains attached; a real failure still reports an error and retries.

#### Call UI and localization

- `home_invite_message` now uses "conversation/rozmowa" wording in all 30
  locales. Polish is exactly
  `Dołącz do mojej rozmowy w HelmChat!\nPokój: %1$s\nLink: %2$s`.
- Moved the Picture-in-Picture icon to the top-left of the call screen; the
  invite action remains at the top-right. The 64 dp touch target and 8 dp
  margin are unchanged.

#### Project hygiene

- Documented the deliberate portrait lock plus compact PiP combination in
  `AndroidManifest.xml` and suppressed `PictureInPictureIssue` where this lint
  version reports it.
- Local debug APK output under `/app/debug/` and raw `.agent/*logcat*.txt`
  captures are ignored by Git.
- Android Gradle Plugin is pinned to 9.5.2 in the version catalog.

### Verification

- Final candidate tree: `./gradlew testDebugUnitTest assembleDebug lintDebug
  --console=plain --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m
  -Dfile.encoding=UTF-8"` passed with 181 JVM tests (0 failures, 0 errors,
  0 skipped), lint reported 0 errors / 25 warnings, and debug APKs were produced
  for `arm64-v8a`, `armeabi-v7a` and universal.
- `./gradlew assembleRelease --console=plain` and, in a separate invocation,
  `./gradlew bundleRelease --console=plain` also passed, producing the three
  release APKs and the AAB named below. No signing properties were configured,
  so those current release outputs are unsigned and not publishable.
- Coverage includes headset-family classification, tri-state HFP lookup,
  stale-SCO suppression, local-stop token retirement, SCO-removal/noisy-event
  suppression, one-shot communication-mode entry, named-route retention during
  transient SCO errors, disconnect
  confirmation from device removal, speaker-latch release on reconnect,
  re-enumerated SCO suppression reset, telephony-owned teardown, terminal-state
  filtering and the bounded `Error` → `Idle` display window.
- A physical device (`R5CX9339VAJ`) was attached for the final packaging pass.
  The first `connectedAndroidTest` attempt was blocked by the already-installed
  app's different signature. After the user approved removing that install, a
  second run completed successfully: 1 instrumented test passed with 0 failures
  and 0 skipped. An `adb uninstall` attempt still reported
  `DELETE_FAILED_INTERNAL_ERROR`, but the Gradle runner replaced the app and
  executed the test. Physical helmet/PiP checks remain manual.

## Current feature baseline

The following capabilities are carried forward into the current 1.6 build.

- **Audio-only group calls:** any Jitsi server, with video disabled, car-mode
  defaults, Opus capped at 24 kbps, and forced WebRTC noise suppression, echo
  cancellation and automatic gain control.
- **Participant tracking:** live count including the local rider, authoritative
  snapshot merge on join, buffered replay of in-flight join/leave events,
  request/session invalidation and snapshot timeout fallback.
- **Motorcycle-optimized UI:** high-contrast dark theme, glove-friendly
  controls, haptic feedback, adaptive phone/tablet layout (Compose Material 3
  Adaptive + Navigation 3), Picture-in-Picture call controls and room sharing.
- **Helmet and background audio:** Bluetooth SCO/HFP routing with a
  helmet/speaker toggle, foreground microphone service with mute/end
  notification actions, and a one-time battery-optimization exemption prompt.
- **Servers and rooms:** curated public Jitsi server list, custom/self-hosted
  servers, optional JWT token, recent rooms (last 8) with quick
  re-join/edit/delete.
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

- Gradle Kotlin DSL, version catalog and AGP 9.5.2; app source compatibility is
  Java 11 while the Gradle daemon JVM toolchain is 21.
- SDK levels: `compileSdk 37.2` / `targetSdk 37` / `minSdk 26` (required by
  Jitsi Meet SDK 12.1.5 and Compose 1.13.0).
- Release APK builds: per-ABI (`arm64-v8a`, `armeabi-v7a`) plus a universal APK,
  R8-minified and resource-shrunk.
- App Bundle builds (`bundle*`): ABI splits are disabled automatically to
  produce a single AAB-compatible shrunk-resources output.
- Release signing is supplied out-of-band via all four Gradle properties
  `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias` and
  `releaseKeyPassword`; signing material is never committed. If none are
  present the release tasks succeed but produce **unsigned, unverifiable**
  artifacts that must not be published.
- Deterministic output names: `HelmChatRisen-1.6-release-<abi>.apk`,
  `HelmChatRisen-1.6-release.apk` (universal) and
  `HelmChatRisen-1.6-release.aab`.
- The build patches the merged Jitsi JS bundle to shorten the conference reload
  countdown to ~5 s; its pattern ignores minified identifier names and fails
  loudly if that JS logic changes in a future SDK upgrade.

## Verification commands

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- Release/AAB packaging: `./gradlew assembleRelease` and, in a **separate**
  invocation, `./gradlew bundleRelease` (ABI splits are disabled for `bundle*`).
- Signed-artifact check: `apksigner verify --print-certs <apk>` for each release
  APK; a missing signature means the signing properties were not supplied.
- Static analysis/inspections in the IDE or CI are recommended as well.
- Latest run (`2026-11-22`): 182 JVM tests pass with 0 failures/errors/skips;
  lint reports 0 errors / 25 warnings; debug build, `bundleRelease`,
  `assembleRelease` and `connectedAndroidTest` all pass. The single
  instrumented test ran on `SM-S721B` / `R5CX9339VAJ` after stale incompatible
  debug/test packages were removed from the device. All release outputs are
  unsigned because the four signing properties were not supplied.

## Known limitations (current 1.6 build)

- Physical verification of Bluetooth helmet intercom routing remains
  environment-dependent. The recent fixes still need a retest on the Samsung
  Galaxy S25 with the actual helmet intercom; available logs were captured with
  `Redmi Buds 5 Pro (Bartosz)`.
- The one-shot communication-mode fix must be confirmed on the Samsung Galaxy
  S25 with a player/radio already running before an intercom call. If background
  media is still attenuated, the likely cause is platform/OEM communication-mode
  ducking rather than app audio focus; that finding must be recorded instead of
  claiming the symptom is fixed.
- The in-call route button is intended to move both the intercom call and the
  rider's background radio/music. The silent voice-communication keep-alive
  reproduces Jitsi's multi-rider playback state so OEM policies can apply the
  same route to `STREAM_MUSIC` with one rider. Final confirmation on the
  Samsung Galaxy S25 with a real helmet intercom and a running player is still
  required; Android does not expose a public API to force another app's media
  output, so this remains platform-policy dependent. Logcat evidence from
  `2026-11-22` shows A2DP suspension and `STREAM_MUSIC` suppression while SCO is
  active, with no focus/duck request from this app.
- Real network-loss rejoin and multi-rider participant counting remain
  unverified on device.
- `meet.jit.si` may require moderator sign-in via the browser before joining
  from the app; password-protected rooms may not work in the embedded view.
- Unreachable or misconfigured servers surface a localized error after the
  connection timeout.

## Installation & upgrade notes

- **1.4 → 1.5:** the application ID changed from `com.example.helmChatRisen`
  to `dev.communicator.helmchatrisen`, so the 1.5 build installs as a separate
  app. Data such as recent rooms, JWT tokens and the language preference is not
  migrated; uninstall the old build to avoid duplicate launcher icons.
- **1.5 → 1.6:** the application ID is unchanged, so this is a normal in-place
  upgrade.
- App label: `HelmChat Risen`; in-app title: `HelmChat - Risen`.
- Release signing configuration is required for installable/publishable release
  artifacts: all four Gradle properties must be present, otherwise the build
  emits unsigned APKs/AABs. Debug builds work without it.
- Release APKs: `HelmChatRisen-1.6-release-arm64-v8a.apk`,
  `HelmChatRisen-1.6-release-armeabi-v7a.apk` and the universal
  `HelmChatRisen-1.6-release.apk`.

## Building release artifacts

```bash
# After committing the release tree and creating the annotated V1.6 tag:
git checkout tags/V1.6

# Provide releaseStoreFile, releaseStorePassword, releaseKeyAlias and
# releaseKeyPassword via ~/.gradle/gradle.properties or -P before building.
./gradlew assembleRelease
# Run separately: ABI splits are disabled for bundle* tasks.
./gradlew bundleRelease

# Confirm every APK is signed before publishing.
apksigner verify --print-certs \
  app/build/outputs/apk/release/HelmChatRisen-1.6-release-arm64-v8a.apk
```

APKs are written under `app/build/outputs/apk/release/` and bundles under
`app/build/outputs/bundle/release/`. Any manual copy under `app/release/` is
local-only and ignored by Git. Until the tag exists, `git checkout V1.6`
selects the same-named branch rather than a release tag.
