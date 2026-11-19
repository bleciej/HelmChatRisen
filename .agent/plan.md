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

### Task_5_AddLauncherIcon: Replace the default Android launcher icon with the supplied `helmchat.svg` artwork.
- **Status:** COMPLETED
- **Updates:** Rendered `helmchat.svg` (embedded helmet PNG + diagonal red "REBORN" wordmark) at 2048 px, cropped to its content bounds, and generated density-specific transparent legacy icons (`ic_launcher.webp`, `ic_launcher_round.webp`), adaptive foreground layers (`ic_launcher_foreground.webp`, fitted to the 66×66 dp safe zone), and monochrome themed-icon layers (`ic_launcher_monochrome.webp`). Replaced the template green-robot adaptive icon with a transparent adaptive background and the new foreground/monochrome layers, preserving the artwork's transparent background and white outline. Also replaced the generic helmet icon in the home header with the new logo on a light circular badge and changed the header title to "HelmChat - Reborn" (`home_title`). Guarded call-leave/back navigation so the Navigation 3 back stack can never be emptied, fixing a `NavDisplay backstack cannot be empty` crash observed in the logs. Verified `assembleDebug`, `testDebugUnitTest` (12 tests) and `lintDebug` pass, and confirmed the icon/title render correctly in the system app-info header and home screen on the `Medium_Phone_API_37.0` emulator with no new crashes.
- **Acceptance Criteria:**
  - Supplied `helmchat.svg` artwork used for the launcher icon
  - Adaptive, legacy square and round icon variants generated for all densities
  - Build, tests and lint pass


### Task_6_RecentRoomsAndMeetJitSiAuth: Add recent-room selection on the home screen and practical handling for servers that require authentication.
- **Status:** COMPLETED
- **Updates:** Added a DataStore-backed `RecentRoomsRepository` that stores the last 8 rooms (name, server URL, optional display name, last-used timestamp) as JSON, deduplicates by room + server, and exposes them as a `Flow`. The home screen now shows a glove-friendly "Ostatnie pokoje" card with one-tap form pre-fill, per-room delete, and "Wyczyść"; rooms are recorded when a call is started and persist across restarts. Added optional JWT token support to `HomeUiState`, `CallRequest`, `CallRoute`, `JitsiCallManager.buildConferenceOptions` and `JitsiConferenceView` for self-hosted Jitsi servers that use token auth. For `meet.jit.si`, added a clear warning plus a browser button that opens the room in the system browser so the moderator can sign in with Google/GitHub/Facebook and start the meeting before joining from the app; the warning explains that password-protected rooms may not work in the embedded view. Added unit tests for recent-room merging, form pre-fill, JWT token passing and meet.jit.si detection. Verified `assembleDebug`, `testDebugUnitTest` (23 tests) and `lintDebug` pass, and verified on the `Medium_Phone_API_37.0` emulator that a joined room appears in recents, survives a force-stop/restart, pre-fills the form when tapped, can be deleted, the meet.jit.si warning/JWT field render, and the browser button opens Chrome; no crashes in the crash log.
- **Acceptance Criteria:**
  - Previously used rooms are selectable from the home screen and persist across restarts
  - Recent rooms can be deleted or cleared
  - Optional JWT token can be supplied for token-auth Jitsi servers
  - meet.jit.si limitation is clearly explained with a browser-based moderator login path
  - Build, tests and lint pass

### Task_7_AddEnglishTranslations: Add English translations for the complete Polish string catalog.
- **Status:** COMPLETED
- **Updates:** Added `app/src/main/res/values-en/strings.xml` with English translations for every UI string, including the home/join screen, recent rooms, JWT/meet.jit.si guidance, call controls, runtime permission dialog and ongoing-call notification. Placeholders (`%1$s`, `%1$d`) and the app name were preserved. Verified `assembleDebug` and `lintDebug` pass with no missing/extra-translation warnings, and confirmed the English resources render on the `Medium_Phone_API_37.0` emulator (`Voice intercom for motorcyclists`, `Audio-only mode`, `Room name`, etc.). The default resources remain Polish, so devices with a non-English locale still fall back to Polish.
- **Acceptance Criteria:**
  - All user-facing strings have English translations
  - Placeholders and formatting remain valid
  - Build and lint pass

### Task_8_EnglishAsDefaultLocale: Make English the default resource language and move Polish to `values-pl`.
- **Status:** COMPLETED
- **Updates:** Swapped the resource sets so `app/src/main/res/values/strings.xml` (default) now contains the English text and `app/src/main/res/values-pl/strings.xml` contains the Polish translation; removed `values-en`. All 50 string keys and every format placeholder (`%1$s`, `%1$d`) match between the two sets, and the app name is unchanged. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass with no missing/extra-translation warnings. Confirmed on the `Medium_Phone_API_37.0` emulator that the app renders English by default (`Voice intercom for motorcyclists`, `Audio-only mode`, `Room name`, `Join call`) and Polish when the app locale is forced to `pl-PL` (`Interkom głosowy dla motocyklistów`, `Tryb audio-only`, `Nazwa pokoju`, `Dołącz do rozmowy`).
- **Acceptance Criteria:**
  - Default resources are English
  - Polish is available via `values-pl`
  - No missing/extra translation or placeholder issues
  - Build, tests and lint pass

### Task_9_AddGermanFrenchSpanishItalianTranslations: Add German, French, Spanish and Italian translations.
- **Status:** COMPLETED
- **Updates:** Added `values-de`, `values-fr`, `values-es` and `values-it` string catalogs with all 50 strings each, translated from the English default catalog. Placeholders (`%1$s`, `%1$d`), the app name and the `HelmChat - Reborn` / `HelmChat — %1$s` branding were preserved; French and Italian apostrophes are escaped (`\'`) for aapt. Verified key sets and placeholders match the default catalog exactly, `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass with no missing/extra-translation warnings, and confirmed on the `Medium_Phone_API_37.0` emulator that `de-DE`, `fr-FR`, `es-ES` and `it-IT` each render their own strings ("Sprachintercom für Motorradfahrer", "Intercom vocal pour motards", "Intercomunicador de voz para motociclistas", "Intercom vocale per motociclisti").
- **Acceptance Criteria:**
  - German, French, Spanish and Italian catalogs exist with complete key coverage
  - Placeholders and formatting remain valid
  - Build, tests and lint pass

### Task_10_LanguageSwitcher: Let the rider switch the UI language, in-app and via system settings.
- **Status:** COMPLETED
- **Updates:** Added `data/AppLanguage.kt`, which lists the shipped locales (`en`, `pl`, `de`, `fr`, `es`, `it`), reads and writes the current choice, and exposes `wrapContext()` for Android 12L and lower. On Android 13+ the choice goes through the system per-app language API (`LocaleManager`), so it also appears in Settings → Apps → HelmChat → Language; on older versions the tag is persisted in `SharedPreferences` and applied by overriding `attachBaseContext` in `MainActivity` and `AudioCallService` (keeps the foreground notification localized too). Added `res/xml/locales_config.xml` plus `android:localeConfig` in the manifest so the system picker offers every translated language. Added a glove-friendly 56 dp translate button in the top-right of the home screen that opens a dialog listing “System default” plus each language written in its own name, with radio selection and 56 dp rows. Added `language_button`, `language_dialog_title`, `language_system` and `language_close` strings to all six catalogs (54 strings each, no gaps). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass (only the expected informational `UnusedAttribute` warning for `localeConfig` on minSdk 24). On the `Medium_Phone_API_37.0` emulator: the button is reachable via the “Change language” content description, selecting Deutsch switched the whole UI to German, `cmd locale get-app-locales` reported `[de]` (system integration), the choice survived a force-stop/restart, and selecting “Systemsprache” returned the app to English with an empty app-locale list.
- **Acceptance Criteria:**
  - Rider can switch between system default, English, Polish, German, French, Spanish and Italian
  - Choice persists across restarts
  - Android 13+ system per-app language settings stay in sync
  - Build, tests and lint pass

### Task_11_ServerDropdown: Replace the single default server with a curated drop-down of public Jitsi servers.
- **Status:** COMPLETED
- **Updates:** Added `jitsi/JitsiServers.kt` with six public presets (freejitsi01.netcup.net, meet.ffmuc.net, webex.muc.ccc.de, meet.golem.de, fairmeeting.net, meet.systemli.org), each with a short translated description, plus `byUrl()` matching that ignores scheme/case/trailing slash. `HomeUiState` now tracks `selectedServerUrl` and `useCustomServer` (renamed from `useDefaultServer`) and exposes `selectedServer`; `onPresetServerSelected()` switches back from custom mode and clears the custom URL/JWT, and `applyRecentRoom()` reselects a preset when the stored server matches one. The home screen now shows a 76 dp drop-down (host + description + chevron) with tall menu rows and a check mark on the active server, followed by a "Custom server" switch that reveals the existing URL/JWT fields. Removed the obsolete `home_server_default` string and added `home_server_custom_switch` plus `server_*_description` to all six catalogs (60 strings each, no gaps). Updated `HomeViewModelTest` for the renamed state and added tests for preset selection, preset reset of custom fields, recent-room preset matching, request URL and the preset list itself (28 unit tests). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass. On the `Medium_Phone_API_37.0` emulator: the drop-down lists all six servers with descriptions in Polish and English, selecting meet.systemli.org updates the card, enabling "Własny serwer" reveals the URL/JWT fields and flags an empty URL, picking meet.golem.de again disables custom mode and clears the fields, and a smoke join opened the call screen with `https://meet.golem.de` as the active server (that host currently returns NXDOMAIN, so the conference itself could not connect; the other five presets answer `config.js` with HTTP 200).
- **Acceptance Criteria:**
  - Default server row is a drop-down with the six requested public servers and their descriptions
  - Custom server entry still works, including JWT
  - Recent rooms reselect the right preset
  - Build, tests and lint pass

### Task_12_RemoveGolemServer: Drop the unreachable meet.golem.de preset.
- **Status:** COMPLETED
- **Updates:** Removed the `meet.golem.de` entry from `JitsiServers.presets` and deleted `server_golem_description` from all six string catalogs (59 strings each, key sets still identical). Updated the preset-list unit test accordingly. Recent rooms that still point at `meet.golem.de` keep working: `byUrl()` finds no preset and the form falls back to custom-server mode with that URL pre-filled. Verified `assembleDebug`, `testDebugUnitTest` (28 tests) and `lintDebug` pass, and confirmed on the `Medium_Phone_API_37.0` emulator that the drop-down now lists exactly five servers (netcup, ffmuc, ccc, fairmeeting, systemli) with no Golem.de row.
- **Acceptance Criteria:**
  - meet.golem.de is gone from the preset list and all translations
  - Old recents using that server still open as a custom server
  - Build, tests and lint pass

### Task_13_MoreTranslations: Add Czech, Slovak, Lithuanian, Latvian, Estonian, Ukrainian, Hungarian, Greek, Bulgarian, Swedish, Danish, Norwegian and Finnish.
- **Status:** COMPLETED
- **Updates:** Added 13 new `values-*` catalogs (`cs`, `sk`, `lt`, `lv`, `et`, `uk`, `hu`, `el`, `bg`, `sv`, `da`, `no`, `fi`), 59 strings each with the same keys and `%1$s`/`%1$d` placeholders as the English default. Extended `AppLanguage.supportedTags` and `nativeNames` (names in their own script) and `res/xml/locales_config.xml` so all 19 locales appear in both the in-app picker and the Android 13+ per-app language settings. Raised the language dialog scroll area from 380 dp to 460 dp so more of the longer list is visible without scrolling. Verified key/placeholder parity across all 18 translation catalogs, `assembleDebug`, `testDebugUnitTest` (28 tests) and `lintDebug` (only the expected `localeConfig` API-level info). On the `Medium_Phone_API_37.0` emulator the picker lists all 20 rows (system default + 19 languages), switching to Ελληνικά applied `[el]` and rendered the Greek UI, switching to Suomi applied `[fi]`, and the app locale was reset to system default afterwards with no crash-log entries.
- **Acceptance Criteria:**
  - All 13 requested languages ship complete catalogs
  - Language picker and system per-app language settings offer every locale
  - Build, tests and lint pass

### Task_14_SortLanguages: Sort the language picker alphabetically by native name.
- **Status:** COMPLETED
- **Updates:** Reordered `AppLanguage.supportedTags` and `nativeNames` alphabetically by the name shown in the picker (diacritics ignored: Čeština before Dansk; Latin, then Greek, then Cyrillic: Ελληνικά, Български, Українська at the end). Matched `res/xml/locales_config.xml` to the same order. Added `AppLanguageTest` (JVM) asserting that every tag has a native name and that `supportedTags` equals the collator-sorted native names, so the list cannot silently drift out of order. "System default" remains the first row. Verified `assembleDebug`, `testDebugUnitTest` (30 tests) and `lintDebug`; on the emulator the dialog shows Čeština → Dansk → Deutsch → Eesti → English → Español → Français → Italiano → Latviešu → Lietuvių → Magyar → Norsk → Polski → Slovenčina → Suomi → Svenska → Ελληνικά → Български → Українська.
- **Acceptance Criteria:**
  - Picker list is alphabetical by displayed language name
  - System default stays first
  - System per-app locale list follows the same order
  - Build, tests and lint pass

### Task_15_InviteFriends: Share a room invitation with new riders.
- **Status:** COMPLETED
- **Updates:** Added `jitsi/RoomLinks.kt` with `roomLink(serverUrl, roomName)` that trims inputs, strips a trailing slash and path-encodes the room (UTF-8, spaces as `%20`), so the shared link works in any browser. `HomeUiState.canInvite` enables the action as soon as a room name is typed. The join card gained a 76 dp "Invite" outlined button under "Join call", and the call screen a 64 dp share icon in the top-right corner; both call `shareRoomInvite()` in `HelmChatApp`, which opens the system share sheet (`ACTION_SEND`, `text/plain`) with the message `home_invite_message` (room name + link) and the room as subject. Recipients without the app can join straight from the browser. Added `home_invite` and `home_invite_message` to all 19 catalogs (61 keys each, placeholders verified) and tests: `RoomLinksTest` (4) and a `canInvite` case in `HomeViewModelTest`. Verified `assembleDebug`, `testDebugUnitTest` (35 tests) and `lintDebug` (no new warning types). On the `Medium_Phone_API_37.0` emulator, inviting for room "niedzielna test" opened the chooser with `Dołącz do mojego interkomu HelmChat!\nPokój: niedzielna test\nLink: https://meet.ffmuc.net/niedzielna%20test`; the in-call share icon produced the same message and the call ended cleanly.
- **Acceptance Criteria:**
  - Invite action is reachable from the join card and from the active call
  - Shared message contains the room name and a browser-openable link
  - Disabled until a room name is entered
  - All languages translated; build, tests and lint pass

### Task_16_PinLanguageButton: Pin the language action in a fixed top bar on the home screen.
- **Status:** COMPLETED
- **Updates:** Replaced the floating `TopEnd` overlay `IconButton` in `HomeScreen` with a non-scrolling 56 dp row above the scrollable form (same horizontal padding as the content, right-aligned), so the language action is pinned at the top of the app and the form scrolls underneath instead of behind it. Reduced the scroll content's top padding from 24 dp to 12 dp to compensate for the new bar. Verified `assembleDebug`, `testDebugUnitTest` (35 tests) and `lintDebug`; on the emulator the icon stays at the same coordinates while the form scrolls, the header no longer slides under the action, and the language dialog still opens from the pinned button.
- **Acceptance Criteria:**
  - Language icon is pinned at the top of the home screen, outside the scrolling content
  - Content scrolls below the bar without overlapping it
  - Language dialog still works
  - Build, tests and lint pass

### Task_17_LanguageFlags: Show a flag next to every language in the picker.
- **Status:** COMPLETED
- **Updates:** Added `AppLanguage.nativeFlags` (19 flag emoji keyed by the same tags as `nativeNames`) and `AppLanguage.SYSTEM_FLAG` (`🌐`) for the "follow the system language" row. `LanguageOption` now takes a `flag` and renders it in a 22 sp `Text` between the radio button and the language name, marked with `clearAndSetSemantics {}` so TalkBack still announces only the language name. Added `AppLanguageTest.supportedTags_haveFlagForEveryTag` (flags cover every tag and are unique). Verified `assembleDebug`, `testDebugUnitTest` (36 tests) and `lintDebug`. On the `Medium_Phone_API_37.0` emulator each of the 20 rows has a 24 dp glyph slot (`NotoColorEmojiFlags.ttf` is present), the dialog opens from the pinned top bar, switching to English relabels the whole UI, and switching back to system default applies `[]` app locales with no crash.
- **Acceptance Criteria:**
  - Every language row shows a flag; system default shows a globe
  - Flags are decorative for accessibility
  - Flags stay in sync with supported tags (guarded by tests)
  - Build, tests and lint pass

### Task_18_BatteryOptimizationAndJitsiFilters: Keep the connection alive in the background and force Jitsi audio filters.
- **Status:** COMPLETED
- **Updates:** Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission to the manifest and a one-time automatic request on the home screen (LaunchedEffect + SharedPreferences flag) so Android does not kill the app in the pocket; no extra UI option was added. Forced Jitsi's built-in WebRTC filters by adding `noiseSuppression`, `echoCancellation` and `autoGainControl` config overrides (in `buildConferenceOptions` and `configureDefaultOptions`); the already-present audio-only/car-mode/prejoin/notifications flags remain. Verified `assembleDebug` and `testDebugUnitTest` pass.
- **Acceptance Criteria:**
  - App requests battery-optimization exemption once on first launch
  - Jitsi conference options enable noise suppression, echo cancellation and auto gain control
  - Build and tests pass

### Task_19_HomeHeaderIconBackground: Darken the home-screen logo badge background.
- **Status:** COMPLETED
- **Updates:** Changed the `HomeHeader` logo badge background from white to the app background color (#090B0E) so it blends with the dark theme; kept the circular shape for consistency with the splash screen. Verified `assembleDebug` passes.
- **Acceptance Criteria:**
  - Home-screen logo badge no longer has a white background
  - Badge shape stays circular and consistent with the splash icon
  - Build passes

### Task_20_FixParticipantCountAndAppLabel: Fix the in-call participant count and the launcher label.
- **Status:** COMPLETED
- **Updates:** Fixed the participant count that was stuck at 1. `JitsiCallManager` read the participant id from the `id` extra, but Jitsi 10.2.0 flattens `participantToParticipantInfo` into the broadcast extras keyed `participantId` (the bundle transport also stringifies values), so every `PARTICIPANT_JOINED`/`PARTICIPANT_LEFT` was ignored and only the local fallback remained. Extracted `participantIdFrom()` (supports `participantId`, legacy `id`, rejects blank/`"null"`/`"undefined"`) and added a `ParticipantsService` snapshot request on `CONFERENCE_JOINED` that resyncs the authoritative participant list (guarded by a per-call session id and a strong callback reference, optional when the SDK service is not initialized). Changed `app_name` from `HelmChatRisen` to `HelmChat Risen` in the default and all 29 locale catalogs so the launcher label under the icon is correct. Added `participantIdFrom` cases to `JitsiCallManagerTest`. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.
- **Acceptance Criteria:**
  - Participant count reflects every rider in the audio-only room
  - Launcher label reads "HelmChat Risen"
  - Build and tests pass

### Task_21_ReviewFollowUp: Address the V1.4 review findings.
- **Status:** COMPLETED
- **Updates:** Made the participant snapshot merge order-safe: join/leave events received while a snapshot is in flight are buffered and replayed on top of it, guarded by both a per-call session id and a snapshot request id; the pure `applyParticipantSnapshot`, `isParticipantSnapshotCurrent` and `sanitizeParticipantIds` helpers are covered by JVM tests. Extracted the duplicated participant/session reset into `resetParticipantSession()`. Changed `participantIdFrom()` to validate each candidate so an invalid `participantId` falls through to the legacy `id`, and removed the unreachable nested-`participant` branch, its KDoc claim and its test. Bumped `versionCode` to 4 for the 1.4 release line (was 1). Removed the duplicated `app_name` from the 29 locale catalogs and marked the default entry `translatable="false"` so lint does not require translations. Ignored `/app/release/` in the root `.gitignore`. Verified `testDebugUnitTest` (71 tests), `assembleDebug`, `lintDebug` and `assembleRelease` pass.
- **Acceptance Criteria:**
  - Participant events received during a snapshot request are neither lost nor applied twice
  - Parser, snapshot merge and session-invalidation logic covered by JVM tests
  - `versionCode` 4 / `versionName` 1.4 and lint/debug/release builds pass

### Task_22_ReleaseNotesConsolidation: Publish a single 1.4 release-notes document.
- **Status:** COMPLETED
- **Updates:** Consolidated the V1.3 and 1.4 notes into one `RELEASE_NOTES.md` that presents 1.4 as the first public release. Merged the feature, permission, build, testing and limitation sections; dropped as history the 1.3-only framing ("maintenance release on top of 1.3", "changed in 1.4", the V1.3 `versionCode=1` caveat, the 1.3 upgrade path and the deprecated `versionCode` warning). Rewrote the upgrade section as installation notes for a first public release, keeping the package-ID / pre-release-build caveat. The V1.3 archive was moved out of the repository to `/tmp/opencode/RELEASE_NOTES_1.3.md.archived`.
- **Acceptance Criteria:**
  - A single `RELEASE_NOTES.md` describes 1.4 with no dangling 1.3-only references
  - All unique content from both files is preserved
  - Version, package, build and limitation facts still match the tree

### Task_23_ParticipantSnapshotHardening: Bound the participant snapshot and make the counter testable.
- **Status:** COMPLETED
- **Updates:** Moved the participant bookkeeping out of `JitsiCallManager` into `ParticipantTracker` (remote ids, pending-snapshot window, buffered events, local-rider presence, single publish step) with an injectable `SnapshotTimeoutScheduler`, so the state machine is covered by JVM tests instead of only the pure helpers. A snapshot request is now bounded by `PARTICIPANT_SNAPSHOT_TIMEOUT_MS` (5 s): an unanswered request is abandoned, its buffered joins/leaves are replayed and the strong callback reference is released. Request failures and duplicate `CONFERENCE_JOINED` events no longer discard buffered events (a second request is refused while one is in flight), the local rider is counted by presence instead of a seeded 1 that disagreed with the id set, `CONFERENCE_JOINED` after `hangUp()`/`onViewReleased()` is ignored and `hangUp()` unregisters the broadcast receiver, and snapshot ids share the broadcast validation (`"null"`/`"undefined"`, string-like extras only). Corrected Task_20's stale parser and test-count claims. Verified `testDebugUnitTest` (97 tests), `assembleDebug` and `lintDebug` pass.
- **Acceptance Criteria:**
  - An unanswered snapshot degrades to the broadcast stream within the deadline and never wedges the count
  - Buffered joins/leaves survive request failures and duplicate join events
  - The participant state machine is covered by JVM tests with a fake scheduler
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_24_DependencyUpdatesAndCleanup: Update dependencies to latest stable versions and remove unused ones.
- **Status:** COMPLETED
- **Updates:** Updated `libs.versions.toml` to latest stable versions compatible with Kotlin 2.3.21: core-ktx 1.19.1, androidx-junit 1.3.0, espresso 3.7.0, lifecycle 2.11.0, compose-bom 2026.09.00, compose-ui 1.12.1, navigation3 1.2.1, adaptive 1.3.0, datastore 1.3.0, jitsi-meet-sdk 10.3.0, Kotlin 2.3.21, KSP 2.3.14. Kept kotlinx-serialization 1.11.0 and coroutines 1.11.0 (compatible with Kotlin 2.3). Removed unused dependencies: camera, coil, retrofit/moshi/okhttp, accompanist, play-services-location, room, espresso, compose ui test junit4/manifest. Updated Jitsi reconnect patch strings for 10.3.0 bundle (pcId:h, F(...)). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.

### Task_25_UpgradeKotlinAndSerialization: Upgrade Kotlin to 2.4.21 and kotlinx-serialization to 1.13.0.
- **Status:** COMPLETED
- **Updates:** Bumped Kotlin 2.3.21 → 2.4.21, jetbrainsKotlinPluginSerialization 2.3.21 → 2.4.21, kotlinxSerializationCore 1.11.0 → 1.13.0. Removed unused KSP plugin (googleDevtoolsKsp) and its entries from root/app build.gradle.kts and toml. minSdk remains 24 (no change required). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.

### Task_26_ReplaceLogWithTimber: Migrate android.util.Log calls to Timber.
- **Status:** COMPLETED
- **Updates:** Added Timber 5.0.1 as explicit dependency, enabled buildConfig. Replaced ~40 Log.d/i/w/e calls in JitsiCallManager, BluetoothAudioRouter and AudioCallService with Timber equivalents and removed TAG constants. Planted Timber.DebugTree in MainActivity.onCreate (debug builds). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.

### Task_27_TranslateTimeoutMessages: Move CONNECT/RECONNECT timeout messages to string resources.
- **Status:** COMPLETED
- **Updates:** Added `call_error_connect_timeout` and `call_error_reconnect_timeout` to default + all locale catalogs. Changed `CallState.Error` to support `messageResId`; replaced hardcoded Polish constants with `R.string` references. Resolved message in HelmChatApp via `stringResource` and logged id in AudioCallService. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.

### Task_28_RenamePackage: Rename application package to dev.communicator.helmchatrisen.
- **Status:** COMPLETED
- **Updates:** Moved source/test directories from `com/example/helmChatRisen` to `dev/communicator/helmchatrisen`, updated namespace/applicationId and all package/import references. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.

### Task_29_FixSonarIssuesBluetoothAudioRouter: Clear all SonarQube/editor findings in BluetoothAudioRouter.
- **Status:** COMPLETED
- **Updates:** Cleared every editor/SonarQube finding in `BluetoothAudioRouter.kt`. Added `@SuppressLint("StaticFieldLeak")` to the application-context singleton (it never retains an Activity), extracted the API-31 communication-device routing into `@RequiresApi(S)` helpers (`applySpeakerphoneApi31Plus`, `routeToBuiltInSpeaker`, `routeToBluetoothOrEarpiece`, `routeAudioApi31Plus`) so all legacy SCO/speakerphone calls live in `@Suppress("DEPRECATION")` methods reached only below API 31, and guarded `isSpeakerphoneOn` behind `setSpeakerphoneFlag(on)` (a no-op on API 31+). Split `setSpeakerphone` into `applySpeakerphoneRouting` plus API-specific helpers to bring its cognitive complexity under the Sonar limit, removed the three unused public helpers (`isSpeakerphoneForced`, `toggleSpeakerphone`, `isBluetoothScoConnected`), typed the communication-device list as read-only `List<AudioDeviceInfo>`, replaced the raw `registerReceiver` with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`, and collapsed the redundant `if/else` in `hasBluetoothPermission`. Also fixed the resulting Kotlin style warnings (when trailing comma, named boolean arguments, clarifying parentheses). Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass and `studio_analyze_file` reports no issues.
- **Acceptance Criteria:**
  - No SonarQube/editor warnings remain in `BluetoothAudioRouter.kt`
  - API 31+ and legacy (< 31) audio-routing behaviour preserved
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_30_FixSonarIssuesAudioCallService: Clear all SonarQube/editor findings in AudioCallService.
- **Status:** COMPLETED
- **Updates:** Cleared every editor/SonarQube finding in `AudioCallService.kt`. Removed the unused `kotlinx.coroutines.flow.StateFlow` import; extracted the identical `CallState.Connected`/`CallState.Reconnecting` handling into `applySessionState(roomName, serverUrl)` so the two `when` branches are no longer duplicated; raised the typed `startForeground` guard from API 29 (`Q`) to API 30 (`R`) because `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` is API 30; dropped the always-true `SDK_INT >= N` check in `stopForegroundAndSelf()` (minSdk 24) together with its deprecated `stopForeground(true)` fallback; and referenced `NOTIFICATION_SERVICE` without the redundant `Context.` qualifier. Verified `studio_analyze_file` reports no issues, the lint report has no `AudioCallService` entries, and `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.
- **Acceptance Criteria:**
  - No SonarQube/editor warnings remain in `AudioCallService.kt`
  - Foreground-service typing behaviour preserved on API 30+ and below
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_31_DocumentAudioCallService: Add KDoc to every function in AudioCallService.
- **Status:** COMPLETED
- **Updates:** Documented every function in `AudioCallService.kt` with KDoc: the companion `startCall`/`stopCall` entry points, the `onCreate`/`onStartCommand`/`onBind`/`onDestroy` lifecycle overrides, and the private helpers `startCallInternal`, `endCallInternal`, `stopForegroundAndSelf`, `createNotificationChannel`, `buildNotification`, `applySessionState` (filling the empty block) and `updateNotification`. The comments describe the foreground-service/microphone contract, the helmet audio-route ownership and the notification actions. Follow-up: brought the comments into full Javadoc/KDoc format by adding `@param` tags (plus `@return` where a value is produced) to the documented functions that take parameters or return a value - `attachBaseContext`, `startCall`, `stopCall`, `onStartCommand`, `onBind`, `startCallInternal`, `buildNotification` and `applySessionState` - matching the `ServerUrlNormalizer`/`HomeViewModel` convention already used in the project. Re-verified `studio_analyze_file` reports no issues (no unresolved KDoc references) and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Every function in `AudioCallService.kt` has KDoc
  - No unresolved KDoc references
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_32_DocumentBluetoothAudioRouter: Add valid Javadoc/KDoc comments to BluetoothAudioRouter.
- **Status:** COMPLETED
- **Updates:** Documented the `AudioRoutingState` sealed hierarchy (each state object and data class, with `@param` for `BluetoothConnected.deviceName` and `Error.message`), `getInstance`, the anonymous `BroadcastReceiver.onReceive` override, and every function that had no comment: `applySpeakerphoneRouting`, `applySpeakerphoneApi31Plus`, `routeToBuiltInSpeaker`, `routeToBluetoothOrEarpiece`, `applySpeakerphoneLegacy`, `updateAudioRouting`, `routeAudioApi31Plus`, `routeAudioLegacy`, `stopBluetoothSco`, `clearPreferredDevice`, `setSpeakerphoneFlag`, `requestAudioFocus`, `abandonAudioFocus`, `registerReceivers`, `unregisterReceivers` and `hasBluetoothPermission`. The existing prose blocks (`startAudioRouting`, `stopAudioRouting`, `resetForNewCall`, `setSpeakerphone`) were extended with `@param`/`@return` block tags so every comment is in valid Javadoc/KDoc format, per the new Android Dev agent rule (item 11). `studio_analyze_file` reports no issues (no malformed documentation or unresolved KDoc references) and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Every class and function in `BluetoothAudioRouter.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_33_DocumentRecentRoom: Add valid Javadoc/KDoc comments to RecentRoom.
- **Status:** COMPLETED
- **Updates:** Brought every KDoc block in `RecentRoom.kt` into valid Javadoc/KDoc format. Added `@param` tags to the `RecentRoom` data class for its four primary-constructor properties (`roomName`, `serverUrl`, `displayName`, `lastUsedAt`), and added `@param`/`@return` tags to `canonicalServerKey` and `mergeRecentRoom`. `MAX_RECENT_ROOMS` keeps its existing single-line KDoc (a constant has no parameters or return value). `studio_analyze_file` reports no issues and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Every class and function in `RecentRoom.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_34_DocumentRecentRoomsRepository: Add valid Javadoc/KDoc comments to RecentRoomsRepository.
- **Status:** COMPLETED
- **Updates:** Brought `RecentRoomsRepository.kt` into valid Javadoc/KDoc format. Added `@param context` to the class KDoc, a KDoc for the public `recentRooms` flow, and `@param`/`@return` tags for `record`, `remove`, `clear`, `decode` and `encode`. `studio_analyze_file` reports no issues and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Every class and function in `RecentRoomsRepository.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_35_DocumentJitsiCallManager: Add valid Javadoc/KDoc comments to JitsiCallManager.
- **Status:** COMPLETED
- **Updates:** Brought `JitsiCallManager.kt` into valid Javadoc/KDoc format (50 KDoc blocks). Documented the `CallState` hierarchy (`Idle`, `Connecting`, `Connected`, `Reconnecting`, `Error` with `@param` tags), the public `DEFAULT_SERVER_URL` constant, the `callState`/`isMuted`/`isNetworkAvailable` flows and `getInstance`, and added `@param`/`@return`/`@throws` tags to the public API (`join` including `@throws IllegalArgumentException`, `toggleMute`, `setMuted`, `buildConferenceOptions`, `configureDefaultOptions`, `sanitizeServerUrl`). Documented every remaining private helper and callback override (`registerBroadcastReceiver` and its `onReceive`, `unregisterBroadcastReceiver`, `registerNetworkCallback`, `unregisterNetworkCallback`, `sendBroadcast`, the `ConnectivityManager.NetworkCallback` overrides, `cancelConnectTimeout`/`cancelReconnectTimeout`, `handleBroadcastEvent`, `booleanValue`, `participantId`, `asValidParticipantId`) and completed the block tags on the existing top-level helpers (`participantIdFrom`, `applyParticipantSnapshot`, `isParticipantSnapshotCurrent`, `sanitizeParticipantIds`, `isParticipantSessionLive`). `studio_analyze_file` reports no issues and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Every class and function in `JitsiCallManager.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_36_DocumentJitsiServers: Add valid Javadoc/KDoc comments to JitsiServers.
- **Status:** COMPLETED
- **Updates:** Brought `JitsiServers.kt` into valid Javadoc/KDoc format. Converted the `JitsiServer` data class tags from `@property` to `@param` (`url`, `descriptionRes`), added KDocs for the public `presets` and `default` properties, and added `@param`/`@return` tags to `byUrl`. The `host` getter keeps its existing single-line KDoc. Verified the change is comment-only with `git diff`. `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass. Two pre-existing IDE warnings remain and are unrelated to documentation: a "Open in browser" hint on the `https://` literal in `host`, and a "Use destructuring declaration" suggestion on `presets.first { ... }`; the same hint also appears in the untouched `ServerUrlNormalizer.kt`.
- **Acceptance Criteria:**
  - Every class and function in `JitsiServers.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_37_DocumentParticipantTracker: Add valid Javadoc/KDoc comments to ParticipantTracker.
- **Status:** COMPLETED
- **Updates:** Brought `ParticipantTracker.kt` into valid Javadoc/KDoc format. Added `@param` tags to the `ParticipantTracker` class (`scheduler`, `snapshotTimeoutMs`, `onSnapshotSettled`) and to `SnapshotRequest` (`session`, `requestId`, replacing the single-line prose doc), and completed the block tags on the existing prose docs of `onJoined`, `onLeft`, `onSnapshotRequested` (`@return`), `onSnapshotReceived`, `onSnapshotFailed` and `applySnapshot`. Added new KDocs with tags to the previously undocumented members: `ScheduledTimeout.cancel`, `SnapshotTimeoutScheduler.schedule` (`@param`/`@return`), and the private helpers `onSnapshotTimeout`, `settleRequest`, `isCurrent` (`@return`) and `publishCount`. `count`, `onConferenceJoined` and `reset` keep their existing KDocs (no parameters or return values). `studio_analyze_file` reports no issues and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass. The pre-existing non-documentation IDE hints on the `isLocal || id == localParticipantId` expression and the `if (localParticipantId != null)` block were left untouched.
- **Acceptance Criteria:**
  - Every class and function in `ParticipantTracker.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_38_DocumentMainActivity: Add valid Javadoc/KDoc comments to MainActivity.
- **Status:** COMPLETED
- **Updates:** Brought `MainActivity.kt` into valid Javadoc/KDoc format. Added `@param newBase` to the existing `attachBaseContext` KDoc, and added new KDocs to every previously undocumented member: `onCreate` (`@param savedInstanceState`), the private PiP helpers `observeCallForPip`, `canEnterPip` (`@return`), `enterPipIfPossible`, `updatePipParams` and `buildPipParams` (`@param autoEnter`/`@return`), and the lifecycle/interface overrides `onUserLeaveHint`, both `onPictureInPictureModeChanged` overloads, `onResume`, `onStop`, `onDestroy`, `onNewIntent`, `onActivityResult`, `onRequestPermissionsResult` and `requestPermissions`, each with `@param` tags in declaration order. The class KDoc and the `pipState` KDoc were already complete (the class has no primary-constructor parameters). `studio_analyze_file` reports no documentation issues and `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass. Four pre-existing non-documentation IDE hints remain and were left untouched (boolean-literal argument on `MutableStateFlow(false)`, lambda-out-of-parentheses on `onEnterPictureInPicture`, clarifying parentheses on the `state is CallState.Idle && pipState.value` expression, and a redundant `if` on `if (!isCallScreenVisible) return false`).
- **Acceptance Criteria:**
  - Every class and function in `MainActivity.kt` has KDoc in valid Javadoc format
  - No malformed-documentation or unresolved-reference warnings
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_39_ReviewCleartextHotspot: Document and review the SonarQube S5332 cleartext hotspot.
- **Status:** COMPLETED
- **Updates:** The `android:usesCleartextTraffic="true"` attribute in `AndroidManifest.xml` raises SonarQube security hotspot S5332 ("Using clear-text protocols is security-sensitive"). Cleartext is a deliberate product requirement: `JitsiCallManager.sanitizeServerUrl` upgrades the curated presets to https but keeps a rider-typed `http://` scheme for unknown (self-hosted) servers, so the attribute cannot be removed without dropping that feature. Decision (confirmed with the user): keep the behavior and clear the hotspot by reviewing it as **Safe** in SonarQube with a justification. To make the rationale discoverable in-repo, an XML comment explaining the decision was added above the `<application>` element (comment only, no behavior change). `assembleDebug`, `testDebugUnitTest` (97 tests) and `lintDebug` pass; the two remaining Android Lint warnings on the manifest (`PictureInPictureIssue`, `UnusedTranslation`) are pre-existing and unrelated.
- **Acceptance Criteria:**
  - The cleartext rationale is documented in the manifest
  - No code or behavior change
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: the S5332 hotspot is marked "Safe" in SonarQube/SonarLint with the recorded justification

### Task_40_FixBundleWithAbiSplits: Allow app-bundle builds while keeping per-ABI APK splits.
- **Status:** COMPLETED
- **Updates:** `:app:bundleRelease` failed with "Multiple shrunk-resources files found in … minifyReleaseWithR8" because the global `splits.abi` config produced three outputs (arm64-v8a, armeabi-v7a, universal), while bundle packaging needs exactly one. Added a computed `isBundleBuild` flag (true when any requested task name ends in `bundle*`) and set `splits.abi.isEnable = !isBundleBuild`, so bundle builds get a single universal output and APK builds keep the per-ABI splits. Verified `:app:bundleRelease` produces a single `HelmChatRisen-1.5-release.aab`, `:app:assembleRelease` still produces the arm64-v8a / armeabi-v7a / universal APKs (with only the matching native libs in each split), and `assembleDebug` + `testDebugUnitTest` (97 tests) + `lintDebug` pass. Note: alternating between APK and bundle builds makes R8/resource-shrinking re-run, since the two configurations produce different intermediates.
- **Acceptance Criteria:**
  - `./gradlew :app:bundleRelease` succeeds and emits a single `.aab`
  - `./gradlew :app:assembleRelease` still emits the per-ABI APKs plus the universal APK
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_41_UpgradeJitsiSdkTo1163: Upgrade Jitsi Meet SDK to 11.6.3.
- **Status:** COMPLETED
- **Updates:** Bumped `jitsiMeetSdk` from 10.3.0 to 11.6.3 in `libs.versions.toml` (version confirmed present in the configured Jitsi Maven repository). SDK 11.6.3 declares `minSdk 26`, so (after user approval) raised `minSdk` 24 → 26 in `app/build.gradle.kts` and removed the now-unreachable Android 7.x PiP fallbacks / deprecated single-argument `onPictureInPictureModeChanged` override from `MainActivity.kt`. The merged JS bundle changed its minified reconnect symbols (`M({reconnecting:!0,pcId:l})}),3e4` instead of `F(...,h)`), so the build-time reconnect patch now uses structural regex patterns for both the reload timeout and the websocket retry; this also tolerates future minifier renames and stays idempotent when the merged asset is reused. Updated `README.md`, `RELEASE_NOTES.md` and the Android agent instructions to the new SDK/minSdk values. Verified `assembleDebug`, `testDebugUnitTest` (97 tests), `lintDebug` and `assembleRelease` (per-ABI arm64-v8a/armeabi-v7a plus universal APK) pass. Device/emulator verification of an actual call is still pending (none connected).
- **Acceptance Criteria:**
  - `gradle/libs.versions.toml` pins Jitsi Meet SDK 11.6.3 and the app builds with it
  - `minSdk` is 26 with no stale API 24/25 code paths left
  - The Jitsi reconnect patch applies to the 11.6.3 bundle without hardcoded minified identifiers
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` pass
  - Manual: smoke-test an audio-only call on a device/emulator

### Task_42_ResetMuteStateBetweenCalls: Start every call with a live microphone.
- **Status:** COMPLETED
- **Updates:** Reproduced the reported "microphone looks off after joining" on the emulator: ending a call while muted left `JitsiCallManager._isMuted` set to `true`; the next `join()` forwarded that stale value as `startWithAudioMuted`, so Jitsi reported `CONFERENCE_JOINED ... "isAudioMuted":true` while audio playback still worked. Added `resetMuteState()` and call it from both `hangUp()` and `onViewReleased()`, so every new conference starts unmuted unless the rider explicitly mutes it during that call. Added a JVM test covering the reset and made test setup clear singleton state between cases. Verified on `emulator-5554` with a fresh debug install: mute → end call → rejoin now logs `"isAudioMuted":false`, the app shows "Mute microphone" and Jitsi's own control shows "Mute microphone"; normal mute/unmute inside a call still works. `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.
- **Acceptance Criteria:**
  - A mute chosen in one call is not carried into the next call
  - Jitsi and the app both show an unmuted microphone on a fresh join
  - In-call mute/unmute behaviour is unchanged
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_43_FixLandscapeControlsAndCallTeardown: Keep call controls reachable and fully release the conference on hang-up.
- **Status:** COMPLETED
- **Updates:** Locked `MainActivity` to portrait in `AndroidManifest.xml` as requested by the rider, so the large glove-friendly call controls can never be clipped by a landscape detail pane (PiP remains available). Reworked Jitsi teardown: `JitsiCallManager.hangUp()` now mutes first, sends HANG_UP, resets the React props through `JitsiMeetView.abort()`, keeps its broadcast receiver registered for the SDK acknowledgement, and publishes a new `CallState.Ending` instead of immediate `Idle`. The call screen stays mounted until Jitsi emits `CONFERENCE_TERMINATED` / `READY_TO_CLOSE`, with a two-stage fallback timeout that aborts the view and finally releases the UI if the SDK never replies. Stale join/mute/participant events are ignored while leaving. `AudioCallService`, `HelmChatApp`, PiP and the call UI now understand `Ending`; in the final Task_45 design the leave button keeps the foreground service and audio route alive until `CallState.Idle`, then the state observer stops the service and navigates home. Added Polish/English "ending" status strings and an ending-state preview. Task_45 also replaced the weak view reference with owned view cleanup and added short-window scrolling; the portrait lock from this task remains. Added JVM coverage for hang-up acknowledgement classification and for `Ending` not counting as a live participant session. Verified on `emulator-5554`: forcing system rotation leaves the activity in portrait; join → connected → "End call" produced `READY_TO_CLOSE`, `Jitsi confirmed the hang-up`, returned home, audio mode `MODE_NORMAL` and no active capture track. `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass.
- **Acceptance Criteria:**
  - Landscape orientation cannot hide the call controls (activity stays portrait)
  - "Zakończ połączenie" leaves the Jitsi conference before the embedded view is disposed
  - Microphone capture, foreground service/notification and audio routing stop after hang-up
  - The UI returns home automatically once Jitsi confirms the leave
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_44_ApplicationInfoDialog: Add an in-app Application info dialog.
- **Status:** COMPLETED
- **Updates:** Centralized the 1.5 release metadata in `app/build.gradle.kts` (version name/code, `V1.5` tag, `2026-11-17` release date, minSdk/targetSdk/compileSdk display strings) and exposed it through `BuildConfig` fields so the dialog cannot drift from the build. Added `ui/home/AppInfoDialog.kt`, a Material 3 dialog listing application name, version, version code, tag, release date, minimum Android and target/compile SDK; all labels are formatted string resources (`app_info_*`) in the default catalog with Polish translations; other locales deliberately fall back to the default English strings, and lint is explicitly told to ignore those missing translations. Added a 56 dp info icon pinned to the physical left edge of the `HomeScreen` top bar and the language action to the physical right edge via `AbsoluteAlignment`, so their positions stay correct in RTL; the dialog content scrolls on small displays. Added previews and `AppInfoMetadataTest`, which asserts relationships between generated metadata instead of hardcoding release facts. Verified `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` (per-ABI + universal APKs) pass, and on `Medium_Phone_API_37.0` the icon sits at x=53–200 with the language button at x=880–1027, taps open a dialog showing all seven values, and "Close"/back dismisses it.
- **Acceptance Criteria:**
  - Info icon in the top-left opens a dialog with all seven requested values
  - Values come from `BuildConfig`/release metadata; labels come from string resources with a Polish translation and documented English fallback
  - Build, unit tests and lint pass

### Task_45_FixCallTeardownReviewFindings: Harden Jitsi teardown and review follow-ups.
- **Status:** COMPLETED
- **Updates:** Fixed the review findings in the uncommitted diff. `JitsiCallManager` now owns the `JitsiMeetView` strongly and releases it exactly once with `abort()`/`dispose()`, uses session/teardown generation tokens so stale timers cannot finalize a newer call, ignores pre-join termination events from the previous conference, publishes `CallState.Ending` before HANG_UP, blocks mute changes during teardown, and exposes `canStartNewCall`/`canJoinConference` guards. `hangUp()` is idempotent and supports `immediate = true` only for real Activity destruction. `AudioCallService` now treats `Ending` as a live foreground state and stops only on `Idle`; the ending notification keeps its status but drops mute/end actions. `HelmChatApp` keeps the exact joined `CallRoute` mounted until `Idle`, no longer calls `stopCall()` directly, and blocks new joins while ending. `JitsiConferenceView` no longer disposes the view itself and skips joins during teardown. Full-screen, PiP and host-setup controls are disabled during `Ending`, with scrollable compact-height layout and new ending previews. `MainActivity` guards one-shot Jitsi host destruction and disables PiP remote actions while ending. Home top-bar icons use absolute edges for RTL, the app-info dialog uses `R.string.app_name` and scrolls, `app_info_*` duplicates were removed from non-Polish catalogs with explicit lint annotations, `values-iw` was renamed to `values-he`, and the stray IDE hardware-input file was removed and ignored. `BuildConfig` now exposes numeric SDK fields and metadata tests assert relationships rather than fixed release facts. Added JVM coverage for stale termination filtering, teardown generations and mute guards. The Jitsi SDK lint advisory (11.6.3 → 13.3.x) is deliberately deferred: 13.x is a major React Native/Fabric change that would require reworking the custom minified-bundle reconnect patch and a dedicated device-verified upgrade.
- **Acceptance Criteria:**
  - Hang-up keeps the service, notification and audio route alive until Jitsi reaches a terminal state
  - Jitsi views are released once, stale callbacks/timers cannot affect newer calls, and duplicate End taps are inert
  - New calls and PiP/host/full-screen controls are blocked while ending
  - RTL top-bar placement, app-info metadata/labels and locale catalogs are consistent
  - `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (107 tests) and `lintDebug` pass; no ADB device/emulator is available for instrumented or manual audio verification
