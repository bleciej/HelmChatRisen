# Project Plan

HelmChatReborn - aplikacja Android do komunikacji głosowej (audio call) dla motocyklistów na bazie Jitsi SDK.

## Project Brief

# Project Brief: HelmChatReborn

## Overview
HelmChatReborn is an Android voice communication (audio call) application tailored specifically for motorcyclists. Built on top of the Jitsi SDK, it eliminates video streams entirely to focus on high-efficiency, low-latency audio communication. The interface is optimized for motorcycle riders with large touch targets, Bluetooth helmet intercom support, and low data/battery consumption.

---

## Features
1. **Audio-Only Room Join & Management**: Frictionless room creation and joining via the default server (`meet.ffmuc.net`) or a custom server, with video streams strictly disabled to minimize data consumption and save battery.
2. **Motorcycle-Optimized UI**: High-contrast interface featuring extra-large touch targets (mic mute toggle, leave call, status indicators) designed for easy operation while riding with gloves.
3. **Bluetooth & Helmet Intercom Integration**: Seamless audio routing integration with motorcycle Bluetooth headsets and intercom systems (A2DP/HFP profiles) via Android AudioManager.
4. **Server Configuration**: Lightweight settings menu allowing users to toggle between the default `meet.ffmuc.net` server and custom self-hosted Jitsi instances.

---

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose using the **Compose Material Adaptive** library for responsive layouts across different screen orientations and window sizes.
- **Navigation Strategy**: **Jetpack Navigation 3** (state-driven navigation architecture).
- **Async & State Management**: Kotlin Coroutines and `StateFlow`.
- **VoIP / Audio Call Engine**: Jitsi Meet SDK (audio-only configuration).
- **Audio Integration**: Android `AudioManager` & Bluetooth profile handlers.

## Implementation Steps
**Total Duration:** 18m 20s

### Task_1_SetupDependenciesAndAudioEngine: Configure project dependencies (Jitsi Meet SDK, Jetpack Navigation 3, Compose Material Adaptive) and build the Jitsi audio-only manager and Bluetooth/AudioManager routing layer supporting custom server URLs (meet.ffmuc.net default).
- **Status:** COMPLETED
- **Updates:** Integrated Jitsi Meet SDK 10.2.0, added Maven repository in settings.gradle.kts, implemented JitsiCallManager for audio-only calls on the meet.ffmuc.net default server, implemented BluetoothAudioRouter for motorcycle headset SCO/HFP routing, created AudioCallService foreground service, configured AndroidManifest.xml permissions, and verified assembleDebug build.
- **Acceptance Criteria:**
  - Jitsi SDK and audio routing infrastructure integrated
  - Audio-only room connection logic configured with server URL setting
  - Bluetooth helmet intercom audio routing support implemented
- **Duration:** 18m 20s

### Task_2_ImplementMotorcycleUIAndNavigation: Build motorcycle-optimized Jetpack Compose UI with Navigation 3: Home/Join screen with server config settings, Call screen with extra-large high-contrast touch targets for gloved operation, and adaptive layout.
- **Status:** COMPLETED
- **Updates:** Implemented state-driven Navigation 3 navigation (HomeRoute/CallRoute with `rememberNavBackStack` + `NavDisplay`) and a Material 3 Adaptive `ListDetailSceneStrategy` so phones show one pane while tablets/foldables show the join form and call side by side. Added `HomeScreen` (room name, rider name, default/custom server configuration with validation, 84dp join button), `CallScreen` (168dp mic toggle, 84dp leave button, connection status pill, audio-route toggle, glove-friendly haptics), `HomeViewModel` with StateFlow form state, Polish string resources, high-contrast motorcycle theme usage, previews, and `HomeViewModelTest` unit tests. Verified `assembleDebug` and `testDebugUnitTest` pass.
- **Acceptance Criteria:**
  - Home screen allows entering room name and configuring server URL (default meet.ffmuc.net)
  - Call screen features extra-large high-contrast touch targets for mic mute, leave call, and status
  - UI uses high contrast and Jetpack Navigation 3 architecture
- **StartTime:** 2026-11-08 18:25:31 CET

### Task_3_IntegrateAudioCallFlow: Wire Jitsi call engine state flow to Compose UI screens, handling active call state, mute toggles, runtime audio and Bluetooth permissions, and lifecycle transitions.
- **Status:** COMPLETED
- **Updates:** Rewrote `JitsiCallManager` around an embedded `JitsiMeetView` with low-bandwidth car-mode defaults (`startAudioOnly`, `startWithVideoMuted`, `startLastN=0`, `channelLastN=0`, prejoin/welcome disabled, Opus capped at 24 kbps). Added `JitsiConferenceView` (`AndroidView` behind the opaque Compose overlay) plus `BroadcastEvent` observation for connection state, mute state and participant count. Wired mute/hang-up through Jitsi external-API broadcasts. Added runtime permission flow (`RECORD_AUDIO`, `BLUETOOTH_CONNECT` on Android 12+, optional `POST_NOTIFICATIONS`) with an app-settings denial dialog. Repurposed `AudioCallService` as the microphone foreground service/notification owner with mute/end actions. Added `JitsiMeetActivityInterface` and lifecycle forwarding to `MainActivity`, config-change handling, back-stack call teardown, and Bluetooth speaker/helmet toggle. Verified `assembleDebug` and `testDebugUnitTest` (12 tests) pass. Runtime verification on a physical device is still pending because no emulator/device is connected.
- **Acceptance Criteria:**
  - App seamlessly joins audio-only Jitsi call from Home screen
  - In-call screen accurately reflects mute state, connection status, and handles disconnect
  - Audio permissions and Bluetooth headset connections handled gracefully
- **StartTime:** 2026-11-08 18:25:31 CET

### Task_4_RunAndVerify: Build project, verify app stability and functionality. Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Verified the full flow on the `Medium_Phone_API_37.0` emulator. Fixed a fatal crash (`IllegalStateException` from react-native-screens) by making `MainActivity` extend `FragmentActivity`, which Jitsi's embedded UI requires. Added a 30 s connection timeout so an unreachable/misconfigured server surfaces a Polish error instead of an endless "Łączenie…" state, and a default Jitsi display name (`Motocyklista`) so the hidden Jitsi UI does not open its "What's your name?" prompt/IME behind the custom overlay. Verified: app launch, runtime permission denial dialog, audio-only join on a custom server (`meet.ffmuc.net`), `CONFERENCE_JOINED` state, participant count, mute/unmute (UI + notification action), speaker/helmet toggle, rotation (adaptive list-detail), background foreground-service notification with mute/end actions, notification "Zakończ" teardown, and clean leave/back navigation. No crashes were observed in the final runs. `assembleDebug`, `testDebugUnitTest` (12 tests) and `lintDebug` pass (lint has warnings only). The default server was changed from `helmchat.eu` (which returns NXDOMAIN) to `meet.ffmuc.net`, which allows anonymous audio-only joins.
- **Acceptance Criteria:**
  - build pass
  - app does not crash
  - make sure all existing tests pass
  - Audio-only call flow and motorcycle UI verified
- **StartTime:** 2026-11-08 18:25:31 CET

