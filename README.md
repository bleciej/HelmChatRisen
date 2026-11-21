# HelmChat — Risen

**Voice intercom for motorcyclists.** An Android, audio-only push-and-talk-style group calling app built on the Jitsi Meet SDK, with an interface designed to be operated with gloves on — at speed.

## Why HelmChat?

Riders need reliable voice communication, not video. HelmChat strips everything a motorcyclist doesn't need out of a standard Jitsi client:

- **Audio-only conferences** — video streams are strictly disabled (`startAudioOnly`, `startWithVideoMuted`, `startLastN=0`, `channelLastN=0`) and the Opus codec is capped at 24 kbps to keep data usage and battery drain minimal.
- **Glove-friendly controls** — a 168 dp mic toggle, 84 dp leave button and 76 dp drop-downs with haptic feedback; everything is reachable while wearing gloves.
- **High-contrast dark theme** — readable in direct sunlight and at night.
- **Helmet intercom routing** — Bluetooth SCO/HFP audio routing via `AudioManager` with a dedicated helmet/speaker toggle for A2DP and HFP headsets.
- **Motorcycle-tuned reconnection** — the bundled Jitsi JS is patched at build time to shorten the conference reload countdown to ~5 s; Jitsi's own XMPP/backoff recovery is intentionally left untouched. Native reconnect deadlines and offline pausing keep a transient network drop from feeling like a dropped call.
- **Foreground service** — an ongoing notification (with mute and end actions) keeps the call alive while the app is in the background; a one-time battery-optimization exemption is requested so Android doesn't kill the connection on the road.

## Features

- Join or create an audio room on any Jitsi server.
- Curated drop-down of public Jitsi servers (meet.ffmuc.net, webex.muc.ccc.de, fairmeeting.net, meet.systemli.org), plus support for custom self-hosted instances.
- Optional **JWT token** field for token-authenticated Jitsi deployments.
- **Recent rooms** — the last 8 rooms persist across restarts and can be re-joined with one tap, edited or deleted.
- **Invite friends** — share a room invitation (room name + plain browser link) from the join screen or from an active call; recipients without the app can join in a browser.
- **meet.jit.si aware** — for `meet.jit.si` the app explains its moderator-auth limitations and can open the room in the browser so the moderator can sign in before you join from the app.
- **30 languages** with an in-app picker (flags, alphabetical, persists across restarts) and full Android 13+ per-app language integration.
- **Adaptive layout** — one pane on phones, list-detail side-by-side on tablets and foldables (Compose Material 3 Adaptive + Navigation 3).

## Tech stack

| Component     | Choice                                                                          |
|---------------|---------------------------------------------------------------------------------|
| Language      | Kotlin, Java 11 compatibility                                                   |
| UI            | Jetpack Compose, Material 3, Compose Material Adaptive                          |
| Navigation    | Jetpack Navigation 3 (`rememberNavBackStack` + `NavDisplay`)                    |
| Async / state | Kotlin Coroutines, `StateFlow`                                                  |
| VoIP          | Jitsi Meet SDK 12.1.5 (audio-only)                                              |
| Audio routing | Android `AudioManager`, Bluetooth SCO/HFP                                       |
| Persistence   | DataStore Preferences (recent rooms), SharedPreferences (language < Android 13) |
| Build         | Gradle 9.6.0 (Kotlin DSL), AGP 9.5.2, version catalog                           |
| SDK levels    | compileSdk 37.2 / targetSdk 37 / minSdk 26                                      |

## Project layout

```
app/src/main/java/dev/communicator/helmchatrisen/
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

Requirements: JDK 21 for the Gradle daemon (app source stays Java 11 compatible), Android SDK platform 37.2 and Android build tools.

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Release APKs (arm64-v8a + armeabi-v7a + universal)
./gradlew assembleRelease

# Release App Bundle (run separately from assembleRelease)
./gradlew bundleRelease
```

Release APKs are **per-ABI** (`arm64-v8a`, `armeabi-v7a`) plus a universal build, and are R8-minified. ABI splits are disabled automatically for `bundle*`, so `bundleRelease` produces one AAB-compatible output. Outputs are written to `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/`:

- `HelmChatRisen-1.6-release-arm64-v8a.apk`
- `HelmChatRisen-1.6-release-armeabi-v7a.apk`
- `HelmChatRisen-1.6-release.apk` (universal)
- `HelmChatRisen-1.6-release.aab`

Release signing is supplied out-of-band via all four Gradle properties (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`) and is never committed. **If they are absent the build still succeeds but produces unsigned, unverifiable artifacts.** Verify signed APKs before publishing, for example:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/HelmChatRisen-1.6-release-arm64-v8a.apk
```

The build patches the merged Jitsi JS bundle (see the `androidComponents` block in [`app/build.gradle.kts`](app/build.gradle.kts)) to shorten the conference reload countdown to ~5 s; the patch fails loudly if the SDK bundle changes so the target can be updated deliberately.

## Installing and upgrading

Full history and upgrade notes are in [`RELEASE_NOTES.md`](RELEASE_NOTES.md#installation--upgrade-notes). In short:

- **1.5 → 1.6:** same application ID (`dev.communicator.helmchatrisen`), normal in-place upgrade.
- **1.4 → 1.5:** application ID changed from `com.example.helmChatRisen` to `dev.communicator.helmchatrisen`; installs as a separate app and does not migrate recent rooms, JWT tokens or the language choice.
- Launcher label: `HelmChat Risen`. In-app title: `HelmChat - Risen`.
- Build and tag the final release commit before publishing; release artifacts require the signing properties described above.

## Permissions

| Permission                                                               | Purpose                                     |
|--------------------------------------------------------------------------|---------------------------------------------|
| `RECORD_AUDIO`                                                           | Voice transmission                          |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`                      | Helmet/headset audio routing                |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`                   | Keep the call alive in the background       |
| `POST_NOTIFICATIONS`                                                     | Ongoing call notification                   |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`                                   | Prevent Android from killing the connection |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS` | Core connectivity and audio                 |

## Testing

Unit tests (`app/src/test`) cover server URL normalization, preset matching, recent-room merging, JWT handling, room-link encoding, the language catalog ordering and the join-form view model. Run them with `./gradlew testDebugUnitTest`.

## Notes for contributors

- UI strings live in `app/src/main/res/values/strings.xml` (English is the default catalog) with translations in `values-*`; keep placeholders identical for every translated key. `app_name` and the documented `app_info_*` / `call_ending` fallbacks are intentionally non-translatable or English-only in some catalogs.
- Keep the motorcycle-optimized UX intact: high contrast, extra-large touch targets, glove-friendly controls, audio-only behavior.
- Keep video disabled and bandwidth conservative — any Jitsi configuration change must stay audio-only.

## License

This project is licensed under the [MIT License](LICENSE).
