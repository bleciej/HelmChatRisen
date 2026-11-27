# HelmChat Risen — Release Notes

|                      |                                  |
|----------------------|----------------------------------|
| Version              | 1.7                              |
| Version code         | 7                                |
| Tag                  | `V1.7`                           |
| Release date         | 2026-11-29                       |
| Last updated         | 2026-11-29                       |
| Minimum Android      | 8.0 (API 26)                     |
| Target / compile SDK | 37 / 37.2                        |
| Application ID       | `dev.communicator.helmchatrisen` |

Release history in chronological order (oldest first). The current release is
**1.7** with version code 7 and tag `V1.7`; create the tag from the final release
commit before publishing. The shared sections at the end describe the
functionality and build/upgrade rules that apply to the current build.

## Release history

- [1.4 — 2026-11-14](#14--2026-11-14)
- [1.5 — 2026-11-17](#15--2026-11-17)
- [1.6 — 2026-11-22](#16--2026-11-22)
- [1.7 — 2026-11-29 (current)](#17--2026-11-29-current)

## Release matrix

| Release | Version code | Tag    | Release date | Application ID                   | Minimum Android | Target / compile SDK |
|---------|-------------:|--------|--------------|----------------------------------|-----------------|----------------------|
| 1.4     |            4 | `V1.4` | 2026-11-14   | `com.example.helmChatRisen`      | 7.0 (API 24)    | 37 / 37              |
| 1.5     |            5 | `V1.5` | 2026-11-17   | `dev.communicator.helmchatrisen` | 8.0 (API 26)    | 37 / 37              |
| 1.6     |            6 | `V1.6` | 2026-11-22   | `dev.communicator.helmchatrisen` | 8.0 (API 26)    | 37 / 37.2            |
| 1.7     |            7 | `V1.7` | 2026-11-29   | `dev.communicator.helmchatrisen` | 8.0 (API 26)    | 37 / 37.2            |

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

## 1.6 — 2026-11-22

> **Scope note:** branch HEAD `4b48505` contains the dependency bump,
> routing/watchdog work and call-service cleanup. The follow-ups listed below
> are present in the `V1.6` working tree and must be committed before the
> release tag; they are included in the 1.6 build described here.

### Summary

The maintenance release that upgrades the Jitsi SDK and AndroidX stack, reworks
helmet routing and reconnect handling, removes explicit audio-focus management,
and applies the latest invite-copy and PiP-position UI changes.

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

### 1.6 follow-ups

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
  so those release outputs were unsigned and not publishable.
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

## 1.7 — 2026-11-29 (current)

### Summary

This release replaces the prebuilt Jitsi AAR with a FLOSS SDK compiled from
source and advances the release metadata to version 1.7, code 7 and tag `V1.7`.

### Highlights

- **FLOSS Jitsi SDK 13.1.1:** `gradle/libs.versions.toml` pins
  `org.jitsi.react:jitsi-meet-sdk:13.1.1` and `settings.gradle.kts` resolves it
  from `mavenLocal()` with no remote Jitsi Maven repository.
  `scripts/build-jitsi-libre-sdk.sh` builds the pinned `mobile-sdk-13.1.1`
  commit with `LIBRE_BUILD=true`, strips the install-attribution/GMS hooks and
  verifies the published POMs. `setAudioOnly()` and the audio-only behavior are
  unchanged.
- **Release metadata:** `appVersionName = "1.7"`, `appVersionCode = 7`,
  `appReleaseTag = "V1.7"` and `appReleaseDate = "2026-11-29"`; the in-app
  Application info dialog and artifact names use the new version. The dialog
  now shows the bundled Jitsi SDK version (from the version catalog) instead of
  the internal version code and tag, which are not useful to riders.
- **F-Droid recipe:** candidate metadata in `fdroid/` and upstream descriptions
  in `fastlane/metadata/android/` publish only the `arm64-v8a` package with
  F-Droid code 700; the 32-bit package is deliberately not submitted.
- **Reconnect patch:** verified against the 13.1.1 JS bundle and applied to both
  debug and release assets.
- **Startup crash fixed:** SDK 13.1.1 starts its bridgeless ReactHost from
  `JitsiInitializer` during Application startup and creates AppCompat widgets;
  the former `android:Theme.Material` parent aborted the process with "requires
  your app theme to be Theme.AppCompat". The shared theme now descends from
  `Theme.AppCompat.NoActionBar`, with AppCompat declared explicitly (currently
  `androidx.appcompat:appcompat:1.8.0`). An instrumented smoke test constructs an
  AppCompat widget with the app context to guard the launch path.
- **Ducking audio focus:** `AudioFocusController` requests
  `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with voice-communication attributes once
  per intercom call and abandons it at teardown. Jitsi keeps
  `audio-focus.disabled=true`, so the app is the only focus owner. Denied,
  delayed or lost focus never ends the call or changes the helmet route. A
  `2026-11-23` device run confirmed TuneIn ducked to 25 % rather than pausing.
- **In-call duration timer:** `JitsiCallManager` records the first confirmed
  join through `CallStartTracker`; a lifecycle-scoped ticker drives a large
  timer above the route button, with a localized label translated in all 29 app
  locales. Reconnects keep one continuous count; the compact PiP layout is
  unchanged.

### Verification

- `./gradlew testDebugUnitTest assembleDebug lintDebug --console=plain
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m
  -Dfile.encoding=UTF-8"` passed: 199 JVM tests (0 failures/errors/skips),
  lint 0 errors / 25 warnings,
  three debug APKs.
- `./gradlew connectedAndroidTest` passed on `Medium_Phone_API_37.0`: 3 tests,
  including the AppCompat theme smoke test and the call timer accessibility
  test. The rebuilt debug APK was installed
  on the emulator, launched, stayed alive and rendered the home screen; no
  `Theme.AppCompat` or fatal Jitsi/React Native error appeared in logcat.
- `./gradlew assembleRelease` and, in a separate invocation,
  `./gradlew bundleRelease` passed; three release APKs and the AAB are named
  below. All four signing properties were supplied for the final run, so the
  artifacts are signed with the developer's own self-signed certificate
  (APK Signature Scheme v2).
- Generated debug/release BuildConfig contains `VERSION_CODE=7`,
  `VERSION_NAME="1.7"`, `RELEASE_TAG="V1.7"` and `RELEASE_DATE="2026-11-29"`.
  `releaseRuntimeClasspath` resolves Jitsi 13.1.1 locally and contains no
  Play Services, Firebase, GMS, install-referrer, Amplitude, Giphy or
  Google Sign-In dependency.
- An x86_64 emulator was attached for the launch check; call audio and helmet
  routing with SDK 13.1.1 on a physical device/intercom still require a manual
  check.

## Current feature baseline

The following capabilities are carried forward into the current 1.7 build.

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
  Jitsi Meet SDK 13.1.1 and Compose 1.13.0).
- Release APK builds: per-ABI (`arm64-v8a`, `armeabi-v7a`) plus a universal APK,
  R8-minified and resource-shrunk.
- App Bundle builds (`bundle*`): ABI splits are disabled automatically to
  produce a single AAB-compatible shrunk-resources output.
- Release signing is supplied out-of-band via all four Gradle properties
  `releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias` and
  `releaseKeyPassword`; signing material is never committed. If none are
  present the release tasks succeed but produce **unsigned, unverifiable**
  artifacts that must not be published. The final 1.7 artifacts were built with
  a local `keystore.jks` (PKCS#12) and verified with `apksigner`.
- Deterministic output names: `HelmChatRisen-1.7-release-<abi>.apk`,
  `HelmChatRisen-1.7-release.apk` (universal) and
  `HelmChatRisen-1.7-release.aab`.
- The build patches the merged Jitsi JS bundle to shorten the conference reload
  countdown to ~5 s; its pattern ignores minified identifier names and fails
  loudly if that JS logic changes in a future SDK upgrade.

## F-Droid readiness (1.7)

Audit against the F-Droid inclusion policy and developer FAQ on 2026-11-22.

The previous blockers from the prebuilt Jitsi Meet SDK 12.1.5 are gone: the app
now consumes a locally built **Jitsi Meet SDK 13.1.1** from `mavenLocal()`.
`scripts/build-jitsi-libre-sdk.sh` pins the `mobile-sdk-13.1.1` source tag, sets
`LIBRE_BUILD=true` (which excludes Amplitude, Giphy and Google Sign-In), strips
the install-referrer/Firebase/GMS IID hooks from `react-native-device-info`,
publishes the artifacts and checks their POM metadata. The untrusted remote
Jitsi Maven repository is no longer configured, and built AARs are never
committed — an F-Droid recipe must run the source build itself.

Already compliant: MIT license with `LICENSE` at the repository root, public
source on GitHub with a consistent `V*` tag scheme (the `V1.7` tag was moved to
the final master commit `3e2f332` and pushed to both `origin` and GitHub),
static `versionName`/`versionCode`, committed Gradle wrapper and jar, no ads
and no application-level Firebase, Play Services or analytics,
user-selectable/self-hostable Jitsi servers (no `TetheredNet` dependence), and
no forbidden group in `releaseRuntimeClasspath`.

Remaining work before official inclusion:

- The candidate recipe and upstream descriptions in
  [`fdroid/`](fdroid/README.md) and `fastlane/metadata/android/` were copied into
  a local `fdroiddata` checkout. The inclusion merge request still has to be
  pushed from that checkout; the recipe runs
  `scripts/fdroid-install-deps.sh` first (JDK 17/21, build tools and a
  checksum-pinned Gradle 9.6), then `scripts/build-jitsi-libre-sdk.sh`
  (Node.js 24+ and a local Maven repository), and then builds the app.
- `versionName` and `versionCode` are local Gradle `val`s, so the recipe sets
  explicit `UpdateCheckData` regexes (checked against `app/build.gradle.kts`)
  and `UpdateCheckMode: Tags V*`.
- `assembleRelease` emits three APKs; the recipe selects only `arm64-v8a` with
  one build block (code 700) and an explicit `output` glob. The 32-bit build was
  dropped from F-Droid because it repeats the long Jitsi SDK/npm compile.
- The `commit:` field pins the final 1.7 release commit
  `3e2f332109e973e2a630472bf0595eae1ba642ac`; the published `V1.7` tag was
  force-moved from `03572db` to that commit and pushed to `origin` and GitHub.
  `fdroid readmeta`/`rewritemeta`/`lint` passed before the Gradle provisioning
  fix, and a quick Gradle build from that commit with `appVersionCode = 700`
  produced the `arm64-v8a` APK. A full `fdroid build -v
  dev.communicator.helmchatrisen:700` still has to be rerun in `fdroidserver`
  after the pinned Gradle installation was added.
- The locally generated toolchain and checkout live in the gitignored
  `build/helmchat-jitsi-build` and `build/helmchat-jitsi-sdk` directories and
  must be recreated in F-Droid's build environment.

## Verification commands

- Source-built SDK: `scripts/build-jitsi-libre-sdk.sh` (Node.js 24+ on `PATH`),
  then `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
  must show no Play Services, Firebase, install-referrer, Amplitude, Giphy or
  Google Sign-In dependency.
- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- Release/AAB packaging: `./gradlew assembleRelease` and, in a **separate**
  invocation, `./gradlew bundleRelease` (ABI splits are disabled for `bundle*`).
- Signed-artifact check: `apksigner verify --print-certs <apk>` for each release
  APK; a missing signature means the signing properties were not supplied.
  Final 1.7 run: all three APKs verify with v2; signer SHA-256 is
  `dbce6bc9199558ae86ed0774d300adc2ecdd97b6438ae60c8af9e871876254c2` and the
  AAB passes `jarsigner -verify`.
- Static analysis/inspections in the IDE or CI are recommended as well.
- Latest run (`2026-11-24`, tag metadata `V1.7`, source-built SDK 13.1.1): 199 JVM tests pass with
  0 failures/errors/skips; lint reports 0 errors / 25 warnings; `assembleDebug`,
  `assembleRelease`, `bundleRelease` and `connectedAndroidTest` (3 tests on the
  x86_64 emulator) all pass, and the reconnect JS patch is applied to both debug
  and release bundles. The launch crash caused by the non-AppCompat theme was
  reproduced and fixed; the app now stays alive and displays the home screen.
  The physical-device `connectedAndroidTest` on `SM-S721B` / `R5CX9339VAJ`
  predates the SDK switch, so call audio and helmet routing with 13.1.1 still
  need a device check. All release outputs are now signed with the developer's
  self-signed certificate; F-Droid re-signs its own source builds.

### Feature detail: transient audio focus (included in 1.7)

Included in the rebuilt and signed 1.7 APKs/AAB and in the F-Droid candidate
recipe:

- `AudioFocusController` requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` once per
  intercom call with `USAGE_VOICE_COMMUNICATION` / `CONTENT_TYPE_SPEECH`, then
  abandons it at teardown. Jitsi keeps `audio-focus.disabled=true`, so the app
  is the only focus owner.
- A denied, delayed or lost focus never ends the call or changes the helmet
  route. `AUDIOFOCUS_LOSS` only clears the held flag; transient losses are
  logged. The intent is to make the rider's radio duck rather than stop.
- Verified in the final 1.7 build: five `AudioFocusControllerTest` cases pass
  within 199 JVM tests (0 failures/errors/skips), lint 0 errors / 25 warnings,
  and `connectedAndroidTest` runs 3/3 on `Medium_Phone_API_37.0`. The release
  APKs and AAB were rebuilt with this code and re-signed.
- **Device audit (`2026-11-23`, 16:44:11–16:45:37, app PID 27456, TuneIn PID
  6920):** the app issued exactly one `requestAudioFocus()` at 16:44:17.786
  (`req=3` / `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, voice-communication
  attributes), received `AUDIOFOCUS_REQUEST_GRANTED`, and TuneIn immediately got
  `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` (`-3`), set its `AudioTrack` volume to
  0.25 and logged `Audio focus lost and audio ducked`. The radio was **ducked,
  not paused**. At 16:45:06 the Bluetooth ACL to `Redmi Buds 5 Pro` dropped;
  TuneIn's own `Audio output disconnect` handler paused it and restored volume —
  this was not a focus action by this app. At 16:45:11 the user tapped play in
  TuneIn, which requested `AUDIOFOCUS_GAIN` (`req=1`); the system removed the
  app's focus entry and dispatched `AUDIOFOCUS_LOSS` (`-1`). The controller
  logged `Intercom audio focus lost; keeping the call and route unchanged`; the
  call, SCO route and hang-up proceeded normally (SCO reconnected at 16:45:23,
  `Jitsi confirmed the hang-up` at 16:45:29, service destroyed at 16:45:29).
  No `FATAL EXCEPTION`, ANR or `am_crash` occurred; the known non-fatal React
  Native soft exception was logged again. Because focus had already been lost,
  teardown correctly skipped the abandon call and no stale focus entry remained.
- Result: the feature **works for its stated goal at call start**. A manual
  resume of the radio mid-call legitimately takes focus back and undoes the
  ducking; the app deliberately does not fight that user action. Still pending:
  a run with the actual helmet intercom, and a normal teardown without a prior
  focus loss to observe `abandonAudioFocusRequest()` on device.

### Feature detail: in-call duration timer (included in 1.7)

Included in the rebuilt and signed 1.7 APKs/AAB and in the F-Droid candidate
recipe:

- `JitsiCallManager.callStartedAtMillis` records `SystemClock.elapsedRealtime()`
  at the first confirmed `CONFERENCE_JOINED` and is cleared when a new join or a
  teardown starts. Reconnects and activity recreation therefore keep one
  continuous timer.
- `CallDuration.kt` formats elapsed time as `MM:SS`, switching to `H:MM:SS` from
  one hour onward, and exposes a cold ticker collected with the host lifecycle.
  The collector lives in a call-route-only composable, so only the timer text
  recomposes each second and no one-second updates are posted while the host is
  stopped.
- `CallScreen` shows the localized `call_duration` label above the timer, which
  sits directly above the intercom/speaker button. The timer is hidden until the
  first confirmed join, stays visible through reconnects, and is hidden on error
  and teardown. The label and value are merged into one accessibility node. The
  label is translated in all 29 app locales and the base English entry no longer
  suppresses `MissingTranslation`.
- Verified in the final 1.7 build: 199 JVM tests (0 failures/errors/skips,
  including formatter, ticker and timestamp-lifecycle cases), lint 0 errors /
  25 warnings, `assembleDebug` with per-ABI outputs, and `connectedAndroidTest`
  3/3 on `Medium_Phone_API_37.0`, including the timer semantics test. Release
  APKs and AAB were rebuilt with this code and re-signed.
- The compact Picture-in-Picture layout has no route button and therefore no
  timer; it stays unchanged.
- **Manual verification pending:** watch the label advance during a live call,
  including across a reconnect.

## Known limitations (current 1.7 build)

- Physical verification of Bluetooth helmet intercom routing remains
  environment-dependent. The recent fixes still need a retest on the Samsung
  Galaxy S25 with the actual helmet intercom; available logs were captured with
  `Redmi Buds 5 Pro (Bartosz)`.
- The one-shot communication-mode fix has now been exercised on the Samsung
  Galaxy S25 with TuneIn already running: the 1.7 focus request made the radio
  duck to 25 % volume on `2026-11-23` rather than pause it. Audible
  confirmation with the actual helmet intercom is still needed; OEM
  communication-mode attenuation can occur independently of audio focus.
- The in-call route button is intended to move both the intercom call and the
  rider's background radio/music. The silent voice-communication keep-alive
  reproduces Jitsi's multi-rider playback state so OEM policies can apply the
  same route to `STREAM_MUSIC` with one rider. Final confirmation on the
  Samsung Galaxy S25 with a real helmet intercom and a running player is still
  required; Android does not expose a public API to force another app's media
  output, so this remains platform-policy dependent. Logcat evidence from
  `2026-11-22` shows A2DP suspension and `STREAM_MUSIC` suppression while SCO is
  active, with no focus/duck request from this app. The 1.7 focus feature is
  included in the rebuilt artifacts; its `2026-11-23` device run ducked TuneIn
  but still needs a helmet-intercom run.
- Real network-loss rejoin and multi-rider participant counting remain
  unverified on device.
- A `2026-11-23` device run with SDK 13.1.1 logged React Native 0.85.2's
  bridgeless host reporting `ReactNoCrashSoftException: getNativeModule(UIManagerModule.class)
  cannot be called when the bridge is disabled` from `react-native-safe-area-context`
  and `react-native-screens` while the hidden Jitsi view attached. React Native
  classifies this as a soft exception: there was no crash, and the full call
  cycle completed (`CONFERENCE_JOINED`, mute toggles, `CONFERENCE_TERMINATED`,
  `AudioCallService destroyed`). Jitsi also logged a dynamic-branding network
  error when outbound access was blocked; neither affects call audio.
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
- **1.6 → 1.7:** the application ID and stored user data are unchanged; the
  source-built FLOSS Jitsi SDK replaces the prebuilt AAR in the same in-place
  upgrade.
- App label: `HelmChat Risen`; in-app title: `HelmChat - Risen`.
- Release signing configuration is required for installable/publishable release
  artifacts: all four Gradle properties must be present, otherwise the build
  emits unsigned APKs/AABs. Debug builds work without it. For 1.7 the local
  `keystore.jks` is configured in `~/.gradle/gradle.properties` (outside the
  repository) and the outputs are signed with a self-signed certificate.
- Release APKs: `HelmChatRisen-1.7-release-arm64-v8a.apk`,
  `HelmChatRisen-1.7-release-armeabi-v7a.apk` and the universal
  `HelmChatRisen-1.7-release.apk`. F-Droid publishes only the first one, as
  `dev.communicator.helmchatrisen_700.apk`.

## Building release artifacts

```bash
# After committing the release tree and creating the annotated V1.7 tag:
git checkout tags/V1.7

# Provide releaseStoreFile, releaseStorePassword, releaseKeyAlias and
# releaseKeyPassword via ~/.gradle/gradle.properties or -P before building.
./gradlew assembleRelease
# Run separately: ABI splits are disabled for bundle* tasks.
./gradlew bundleRelease

# Confirm every APK is signed before publishing.
apksigner verify --print-certs \
  app/build/outputs/apk/release/HelmChatRisen-1.7-release-arm64-v8a.apk
```

APKs are written under `app/build/outputs/apk/release/` and bundles under
`app/build/outputs/bundle/release/`. Any manual copy under `app/release/` is
local-only and ignored by Git. The `V1.7` tag is published on `origin` and
GitHub; verify it before building published binaries from a clean tag.
