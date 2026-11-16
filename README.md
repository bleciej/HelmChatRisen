# HelmChat — Risen

**Voice intercom for motorcyclists.** An Android, audio-only push-and-talk-style group calling app built on the Jitsi Meet SDK, with an interface designed to be operated with gloves on — at speed.

## Why HelmChat?

Riders need reliable voice communication, not video. HelmChat strips everything a motorcyclist doesn't need out of a standard Jitsi client:

- **Audio-only conferences** — video streams are strictly disabled (`startAudioOnly`, `startWithVideoMuted`, `startLastN=0`, `channelLastN=0`) and the Opus codec is capped at 24 kbps to keep data usage and battery drain minimal.
- **Glove-friendly controls** — a 168 dp mic toggle, 84 dp leave button and 76 dp drop-downs with haptic feedback; everything is reachable while wearing gloves.
- **High-contrast dark theme** — readable in direct sunlight and at night.
- **Helmet intercom routing** — Bluetooth SCO/HFP audio routing via `AudioManager` with a dedicated helmet/speaker toggle for A2DP and HFP headsets.
- **Motorcycle-tuned reconnection** — the bundled Jitsi JS is patched at build time to shorten the conference reload (~5 s instead of 10–30 s) and websocket retry (5 s instead of 30 s) delays, so a transient network drop in your pocket doesn't feel like a dropped call.
- **Foreground service** — an ongoing notification (with mute and end actions) keeps the call alive while the app is in the background; a one-time battery-optimization exemption is requested so Android doesn't kill the connection on the road.

## Features

- Join or create an audio room on any Jitsi server.
- Curated drop-down of public Jitsi servers (meet.ffmuc.net, webex.muc.ccc.de, fairmeeting.net, meet.systemli.org), plus support for custom self-hosted instances.
- Optional **JWT token** field for token-authenticated Jitsi deployments.
- **Recent rooms** — the last 8 rooms persist across restarts and can be re-joined with one tap, edited or deleted.
- **Invite friends** — share a room invitation (room name + plain browser link) from the join screen or from an active call; recipients without the app can join in a browser.
- **meet.jit.si aware** — for `meet.jit.si` the app explains its moderator-auth limitations and can open the room in the browser so the moderator can sign in before you join from the app.
- **19 languages** with an in-app picker (flags, alphabetical, persists across restarts) and full Android 13+ per-app language integration.
- **Adaptive layout** — one pane on phones, list-detail side-by-side on tablets and foldables (Compose Material 3 Adaptive + Navigation 3).

## Tech stack

| Component     | Choice                                                                          |
|---------------|---------------------------------------------------------------------------------|
| Language      | Kotlin, Java 11 compatibility                                                   |
| UI            | Jetpack Compose, Material 3, Compose Material Adaptive                          |
| Navigation    | Jetpack Navigation 3 (`rememberNavBackStack` + `NavDisplay`)                    |
| Async / state | Kotlin Coroutines, `StateFlow`                                                  |
| VoIP          | Jitsi Meet SDK 10.3.0 (audio-only)                                              |
| Audio routing | Android `AudioManager`, Bluetooth SCO/HFP                                       |
| Persistence   | DataStore Preferences (recent rooms), SharedPreferences (language < Android 13) |
| Build         | Gradle (Kotlin DSL), AGP 9, KSP, version catalog                                |
| SDK levels    | compileSdk 37 / targetSdk 37 / minSdk 24                                        |

## Project layout

```
app/src/main/java/com/example/helmChatRisen/
├── MainActivity.kt          # FragmentActivity hosting the Compose UI + Jitsi lifecycle
├── jitsi/                   # JitsiCallManager, JitsiServers, RoomLinks, ServerUrlNormalizer
├── audio/                   # BluetoothAudioRouter (SCO/HFP helmet routing)
├── service/                 # AudioCallService (microphone foreground service + notification)
├── ui/
│   ├── home/                # HomeScreen + HomeViewModel (join form, servers, recents)
│   ├── call/                # CallScreen + JitsiConferenceView (embedded JitsiMeetView)
│   ├── navigation/          # Navigation 3 routes
│   └── theme/               # High-contrast motorcycle theme
└── data/                    # RecentRoomsRepository, AppLanguage
```

## Building

Requirements: JDK 17+, Android SDK with platform 37.

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Release APKs (arm64-v8a + armeabi-v7a + universal)
./gradlew assembleRelease
```

Release builds are **per-ABI** (`arm64-v8a`, `armeabi-v7a`) and minified. Release signing is supplied out-of-band via Gradle properties (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`) and is never committed.

The build automatically patches the merged Jitsi JS bundle (see the `androidComponents` block in [`app/build.gradle.kts`](app/build.gradle.kts)) to shorten reconnect delays; the patch fails the build loudly if the Jitsi SDK is upgraded and the targets change.

## Permissions

| Permission                                                               | Purpose                                     |
|--------------------------------------------------------------------------|---------------------------------------------|
| `RECORD_AUDIO`                                                           | Voice transmission                          |
| `BLUETOOTH_CONNECT`                                                      | Helmet/headset audio routing (Android 12+)  |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`                   | Keep the call alive in the background       |
| `POST_NOTIFICATIONS`                                                     | Ongoing call notification                   |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`                                   | Prevent Android from killing the connection |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` | Core connectivity and audio                 |

## Testing

Unit tests (`app/src/test`) cover server URL normalization, preset matching, recent-room merging, JWT handling, room-link encoding, the language catalog ordering and the join-form view model. Run them with `./gradlew testDebugUnitTest`.

## Notes for contributors

- UI strings live in `app/src/main/res/values/strings.xml` (English is the default catalog) with translations in `values-*`; every catalog must keep identical keys and placeholders.
- Keep the motorcycle-optimized UX intact: high contrast, extra-large touch targets, glove-friendly controls, audio-only behavior.
- Keep video disabled and bandwidth conservative — any Jitsi configuration change must stay audio-only.

## License

This project is licensed under the [MIT License](LICENSE).
