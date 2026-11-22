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

### Task_46_UpgradeJitsiSdkTo1215: Upgrade Jitsi Meet SDK to 12.1.5.
- **Status:** COMPLETED
- **Updates:** Bumped `jitsiMeetSdk` from 11.6.3 to 12.1.5 in `gradle/libs.versions.toml`. The originally requested 12.1.0 could not be used: its published POM declares three dependencies with `unspecified` versions (`android:amplitude-analytics-react-native`, `android:giphy-react-native-sdk`, `android:sayem314-react-native-keep-awake`) and no matching artifacts exist, and Jitsi's changelog skips 12.1.0 (12.0.0 → 12.1.1). After confirming with the user, the latest patched 12.1.x release was used instead. 12.1.x keeps React Native 0.79.7 on the old architecture (the Fabric/Bridgeless switch is 13.x), minSdk 26, and the same `JitsiMeetView`/broadcast/config-override/feature-flag APIs, so no Kotlin source changes were needed; generalized one stale "Jitsi 10.x" comment in `JitsiCallManagerTest`. The build-time reconnect patch in `app/build.gradle.kts` matched the 12.1.5 minified bundle without any regex change. Refreshed the SDK version in `README.md` and `RELEASE_NOTES.md`. Verified `assembleDebug`, `testDebugUnitTest` (107 tests), `lintDebug` (only the informational 13.3.0-available advisory, deliberately deferred) and `assembleRelease` (per-ABI arm64-v8a/armeabi-v7a plus universal APKs) pass. Device/emulator verification of an actual call is still pending (none connected).
- **Acceptance Criteria:**
  - `gradle/libs.versions.toml` pins Jitsi Meet SDK 12.1.5 and the app builds with it
  - Audio-only config, broadcast events and feature flags remain valid with no source changes
  - The Jitsi reconnect patch applies to the 12.1.5 bundle without changes
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` pass
  - Manual: smoke-test an audio-only call on a device/emulator

### Task_47_RefreshAndroidxDependencies: Refresh Lifecycle, Compose and Navigation 3 libraries.
- **Status:** COMPLETED
- **Updates:** Bumped `lifecycleRuntimeKtx`, `lifecycleViewmodelCompose` and `lifecycleRuntimeCompose` from 2.11.0 to 2.11.1, `composeBom` from 2026.09.00 to 2026.11.01 (Compose 1.13.0) and `navigation3Ui`/`navigation3Runtime` from 1.2.1 to 1.2.2 in `gradle/libs.versions.toml`. The new Compose artifacts require compileSdk 37.1+, so `app/build.gradle.kts` now sets `minorApiLevel = 2` on the existing compileSdk 37 while leaving `minSdk` 26 and `targetSdk` 37 unchanged; Android SDK platform 37.2 was installed locally because no 37.1+ platform and no `sdkmanager` were available. Updated the SDK-level lines in `README.md` and `RELEASE_NOTES.md`. No Kotlin/Compose source changes were needed; Navigation 3 1.2.2 keeps the same back-stack and `NavDisplay` APIs. Verified clean `assembleDebug`, `testDebugUnitTest` (107 tests), `lintDebug` (0 errors; only the pre-existing informational Jitsi 13.3.0 advisory) and `assembleRelease` (per-ABI arm64-v8a/armeabi-v7a plus universal APKs) pass, with both configuration cache disabled and enabled.
- **Acceptance Criteria:**
  - `gradle/libs.versions.toml` pins Lifecycle 2.11.1, Compose BOM 2026.11.01 and Navigation 3 1.2.2
  - `compileSdk` uses minor level 37.2 without changing minSdk/targetSdk or app metadata behavior
  - Navigation 3 and Compose sources compile unchanged against the new versions
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` pass
  - Manual: smoke-test navigation and the in-app SDK info label on a device/emulator

### Task_48_BumpVersionTo16: Bump application version to 1.6 (code 6).
- **Status:** COMPLETED
- **Updates:** Changed `appVersionName` to `1.6` and `appVersionCode` to `6` in `app/build.gradle.kts`; the derived `appReleaseTag` is now `V1.6`. Synchronized the version, code, tag, build bullet, output names and checkout snippet in `RELEASE_NOTES.md`. `appReleaseDate` was intentionally left at `2026-11-17` because no new release date was specified. `AppInfoMetadataTest` needs no change because it asserts relationships rather than fixed release numbers. Verified clean `assembleDebug`, `testDebugUnitTest` (107 tests), `lintDebug` (0 errors; 30 pre-existing warnings) and `assembleRelease` pass; release outputs are `HelmChatRisen-1.6-release-arm64-v8a.apk`, `HelmChatRisen-1.6-release-armeabi-v7a.apk` and the universal `HelmChatRisen-1.6-release.apk`. The verification run needed a larger Gradle daemon heap and fewer workers because the default 2 GiB heap hit OOM in D8/R8/lint.
- **Acceptance Criteria:**
  - `app/build.gradle.kts` declares versionName `1.6` and versionCode `6`
  - `BuildConfig` exposes the new version/code/tag and release artifacts use the `1.6` names
  - `RELEASE_NOTES.md` matches the declared release metadata and output names
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `assembleRelease` pass

### Task_49_FixAudioRouteToggleWhenNoHeadset: Lock the call audio-route button to the speaker without a headset.
- **Status:** COMPLETED
- **Updates:** The call-screen speaker/headset button used an independent local `isSpeakerOn` boolean, so with no headset connected it incorrectly showed "Interkom / słuchawki", stayed tappable and toggled the actual audio route. `BluetoothAudioRouter` now publishes `isHeadsetConnected`, detecting Bluetooth SCO/A2DP, wired/USB headsets and SCO-capable headsets whose channel is not open yet, and re-evaluating it on call start, SCO state changes, Bluetooth/wired headset plug events and noisy-audio. With no headset the router routes call audio to the built-in speaker (all API levels) and ignores toggle requests. Added `AudioRouteControl.kt` with a pure `resolveAudioRouteControl` helper: no headset → speaker shown and switching disabled; headset connected → button mirrors the active route and toggles between headset and speaker. `HelmChatApp` derives the button state and label from these flows instead of local mutable state, and `CallScreenState.canSwitchAudioRoute` disables the control while no headset is connected (or during teardown). Added `AudioRouteControlTest` (7 JVM tests) and a no-headset preview. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass; no device/emulator is available for manual audio-routing verification.
- **Acceptance Criteria:**
  - With no headset connected the button shows "Głośnik telefonu", uses the speaker icon and cannot be toggled
  - With a headset connected the button toggles audio between the helmet/headset and the phone speaker
  - Headset connect/disconnect during a call updates the button's enabled state
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: verify on a device with and without a Bluetooth helmet intercom connected

### Task_50_SkipIntercomConnectingWithoutHeadset: Use the phone speaker immediately when no headset is attached.
- **Status:** COMPLETED
- **Updates:** Device testing showed "Łączenie z interkomem" even with no headset connected. Two causes were removed from `BluetoothAudioRouter`: headset detection no longer uses `AudioManager.isBluetoothScoAvailableOffCall` (some devices report SCO as available with nothing attached), it now checks actually connected HFP/A2DP devices through `BluetoothManager.getConnectedDevices` plus wired/USB audio outputs; and SCO broadcasts are handled by `handleScoStateChanged`, which never publishes `ConnectingBluetooth`/`Error` unless a headset is genuinely connected, otherwise it immediately re-routes to the phone speaker. Added a JVM test proving that a pending SCO connection with no headset still resolves to the speaker. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass; physical re-verification is still needed.
- **Acceptance Criteria:**
  - Without a connected headset the call screen shows "Głośnik telefonu" immediately and never "Łączenie z interkomem"
  - A connected Bluetooth headset is detected through active HFP/A2DP links, not a spurious SCO-available flag
  - SCO error/connecting broadcasts without a headset keep the phone-speaker route
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: re-test on the device that reproduced the "connecting" state

### Task_51_FixBluetoothProfileCrashOnCallStart: Prevent call-start crash on devices without HFP/A2DP profiles.
- **Status:** COMPLETED
- **Updates:** Reproduced the reported crash on `emulator-5554`: tapping "Dołącz do rozmowy" threw `java.lang.IllegalArgumentException: Profile not supported: 1` from `BluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET)` inside `BluetoothAudioRouter.hasConnectedBluetoothHeadset()`, during `resetForNewCall()` before Jitsi could connect. Extracted a testable `hasConnectedBluetoothHeadset(connectedDevices)` helper that queries HFP and A2DP through a supplied lookup and treats any failed/unavailable profile query (including `IllegalArgumentException`, `SecurityException` from a permission race, and `null` results) as "no headset" instead of crashing. `BluetoothAudioRouter` now calls it via `manager::getConnectedDevices` behind the existing permission guard. Added `BluetoothHeadsetDetectionTest` with 5 JVM tests covering unsupported profiles, revoked permission, null/empty results and real HFP/A2DP links. Reinstalled the rebuilt universal debug APK on the emulator and repeated the join flow: no FATAL crash, app process stays alive, and the only remaining logs are caught, logged warnings for unsupported profiles. Verified `assembleDebug`, `testDebugUnitTest` (120 tests) and `lintDebug` pass.
- **Acceptance Criteria:**
  - Tapping join on a device/emulator that does not support Bluetooth HFP/A2DP no longer crashes
  - A failed profile lookup cannot hide a headset reported by the other supported profile
  - Permission revocation or a null lookup result degrades to the phone-speaker route
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: reproduced and re-verified on `emulator-5554`

### Task_52_DetectHeadsetConnectionImmediately: Enable the audio-route button as soon as a headset connects.
- **Status:** COMPLETED
- **Updates:** Headset detection still relied on re-querying `AudioManager`/`BluetoothManager`, which can lag behind the actual connection broadcast, so a newly connected headset did not immediately enable the in-call route button while a disconnect did. The receiver now reads the connection state straight from the event: `Intent.ACTION_HEADSET_PLUG` uses its `state` extra (`1` plugged, `0` unplugged) and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` uses `BluetoothProfile.EXTRA_STATE` (`STATE_CONNECTED` enables, other states fall back to re-detection so another headset is not hidden). `updateAudioRouting` accepts an optional forced headset flag from those broadcasts. `handleScoStateChanged` also treats `SCO_AUDIO_STATE_CONNECTED` as proof of a headset instead of letting a stale device query reset it. `registerReceivers` now feeds the sticky `ACTION_HEADSET_PLUG` intent into the receiver, so a headset connected before the call is picked up at call start. Added `wiredHeadsetConnectionState` and `isBluetoothHeadsetConnected` helpers plus 2 JVM tests. Verified `assembleDebug`, `testDebugUnitTest` (122 tests) and `lintDebug` pass. A full connection/disconnection cycle still needs confirmation on a physical phone with a real helmet intercom; the emulator cannot send the protected `HEADSET_PLUG` broadcast from adb shell.
- **Acceptance Criteria:**
  - Connecting a wired, USB or Bluetooth headset during a call enables the audio-route button without waiting for a disconnect
  - Disconnects still disable the button unless another headset remains connected
  - A headset connected before joining a call is detected from the sticky plug broadcast
  - SCO connect/disconnect broadcasts cannot overwrite a verified headset connection with a stale query
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_53_ReworkInCallAudioRouting: Make helmet/headset routes reliable during an active conference.
- **Status:** COMPLETED
- **Updates:** Real-phone testing of issues 1–3 exposed three separate routing faults. (1) Bluetooth detection accepted A2DP, which Android cannot use for two-way calls (`setCommunicationDevice()` rejects it); HFP/SCO is now the only Bluetooth route tracked, in `AudioDeviceSelection.kt`, `AudioRouteControl.kt` and `hasConnectedBluetoothHeadset()`. (2) Profile/plug broadcasts can arrive before the audio device is enumerated, so `BluetoothAudioRouter` now registers an `AudioDeviceCallback`, tracks a `HeadsetKind` (NONE/BLUETOOTH/WIRED) and runs bounded, generation-guarded retries (250 ms … 3 s) with `startBluetoothSco()` for HFP; exhausted retries fall back to the phone speaker instead of leaving "Łączenie z interkomem" on screen. (3) A headset disconnect now clears `isSpeakerphoneForced` and resets SCO state, so reconnecting mid-call becomes the active route without restarting the conference; wired/USB devices (USB > wired > headphones) are selected via `setCommunicationDevice()` and never show the SCO "connecting" state. Jitsi keeps `audio-focus.disabled=true` (verified in SDK 12.1.5: this leaves mode/focus/SCO entirely to the app) and now also `audio-device-button.enabled=false` so its hidden UI cannot offer a route the native layer does not own. Added `AudioDeviceSelectionTest` (7) and updated `BluetoothHeadsetDetectionTest` (8) and `AudioRouteControlTest` (8) for HFP-only behavior. Verified `assembleDebug`, `testDebugUnitTest` (132 tests after Task_54) and `lintDebug` (0 errors) pass and all three ABI debug APKs build. A smoke join on `emulator-5554` reached `CONFERENCE_JOINED` with no crash; physical headset connect/reconnect/USB verification is still required.
- **Acceptance Criteria:**
  - Bluetooth HFP headset connected during a call becomes the active route; A2DP-only outputs are ignored
  - Bluetooth disconnect/reconnect mid-call restores the headset route without rejoining the conference
  - Wired/USB headset connects without a stuck "Łączenie z interkomem" state
  - Retry exhaustion always returns to the phone speaker; a no-headset state keeps the speaker route disabled
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: verify on the physical phone that reproduced issues 1–3

### Task_54_HardenJitsiReconnectTiming: Verify and fix the delay before Jitsi rejoins the room.
- **Status:** COMPLETED
- **Updates:** Audited Jitsi SDK 12.1.5's minified bundle: the patched reload dialog (`_timeoutSeconds=5`) now fires about 6.5 s after an error (was 11.5–31.5 s), while XMPP ping detection takes 15–30 s and the conference-request backoff can reach 120 s, so the real worst case is longer than the dialog alone. The previous second patch only shortened WatchRTC telemetry, not call recovery, and was removed. Kotlin-side faults were the main cause of intermittent failures: `CONFERENCE_WILL_JOIN` cancelled the watchdog without re-arming, the watchdog accepted only `Reconnecting` (not Jitsi's `Connecting` during reload), its 45 s deadline was shorter than Jitsi's own backoff, offline time counted against it, and `join()` released the old view while still subscribed to its broadcasts. `JitsiCallManager` now uses `RECONNECT_RELOAD_TIMEOUT_MS = 60 s` after termination and `REJOIN_COMPLETION_TIMEOUT_MS = 150 s` after `WILL_JOIN`, tracks `pendingReconnectRoom`, pauses/resumes the countdown across network loss (including `onUnavailable`), accepts both transient states via `isReconnectWatchdogState`, and unregisters the old receiver before releasing the view. Added 2 JVM tests (132 total) and a build-time assertion that the reload patch produced `_timeoutSeconds=5`. Verified `assembleDebug`, `testDebugUnitTest` and `lintDebug` (0 errors) pass and all three ABI debug APKs build; real network-loss rejoin still needs device verification.
- **Acceptance Criteria:**
  - Jitsi reload dialog delay measured at ~6.5 s; XMPP/backoff delays documented, not patched blind
  - Reconnect watchdog covers `Connecting` and `Reconnecting` and outlasts Jitsi's 120 s backoff
  - Offline periods pause the watchdog and resume it when the network returns
  - A stale termination from a previous view cannot put a new join into `Reconnecting`
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: toggle airplane mode mid-call on the physical phone and confirm the room rejoins

### Task_55_KeepSpeakerChoiceWithConnectedUsbHeadset: Stop USB speaker toggles from bouncing back to the headset.
- **Status:** COMPLETED
- **Updates:** Real-phone testing confirmed USB/BT connect/disconnect and USB→headset switching, but selecting the phone speaker while a USB headset stayed connected immediately returned to the headset. Three causes were removed from `BluetoothAudioRouter`: (1) `updateAudioRouting()` cleared `isSpeakerphoneForced` whenever a transient re-detection returned `HeadsetKind.NONE`, so a late device callback undid the rider's choice; the flag now resets only on a confirmed plug/profile/device-removal/noisy disconnect. (2) `handleScoStateChanged()` and every routing re-evaluation now go through the new `resolveRouteTarget()` helper, so a forced speaker outranks a connected headset and a late SCO connected/disconnected broadcast cannot undo it. (3) `routeToBuiltInSpeaker()` no longer ignores a failed `setCommunicationDevice()`; it clears any explicit communication device and enables the legacy speakerphone flag as a fallback, which is required on devices that reject the built-in speaker while a USB headset is enumerated. `AudioDeviceCallback` now forwards newly added headset families and real headset removals directly, and general API methods use `setSpeakerphoneFlag(false)` before selecting a headset so no legacy override fights the selected device. Added 4 JVM tests covering route precedence and callback-derived headset families (136 total). Verified `assembleDebug`, `testDebugUnitTest` (136 tests, 0 failures) and `lintDebug` (0 errors) pass and all three ABI debug APKs build; final USB→speaker retention still needs a physical-phone retest.
- **Acceptance Criteria:**
  - With a USB headset connected, tapping "Głośnik telefonu" keeps the speaker route until the rider taps back or a real disconnect occurs
  - Late audio-device, SCO and plug callbacks cannot override the rider's explicit speaker choice
  - A real headset disconnect still clears the forced speaker so the next connection becomes active without rejoining
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
  - Manual: on the physical phone, USB connected → switch to phone speaker → confirm it stays there, then switch back to the headset and verify BT/USB connect/disconnect again

### Task_56_ApplyAudioRoutingAndReconnectReviewFixes: Apply five scoped review follow-ups.
- **Status:** COMPLETED
- **Updates:** Applied the five requested fixes to the uncommitted review changeset. (1) `BluetoothAudioRouter.resetForNewCall()` now publishes headset availability only while routing is active and otherwise keeps `NONE`/`false`, and the duplicate reset call was removed from `HelmChatApp.startCall()` (the service still resets in `startCallInternal()`). (2) `AudioDeviceCallback.onAudioDevicesAdded()` re-detects the full headset state instead of deriving it only from newly added devices, so plugging in USB/wired audio cannot override an active Bluetooth helmet route; the removed-devices callback still uses `headsetKindFromAudioDevices`. (3) `registerReceivers()` no longer replays a sticky broadcast, leaving initial detection to `startAudioRouting()`. (4) `updateAudioRouting()` clears the rider's forced-speaker choice and stops SCO only when a disconnect event leaves no headset at all, so disconnecting a non-active device preserves the route. (5) `JitsiCallManager` cancels the 30 s connect timeout when `CONFERENCE_WILL_JOIN` arms the 150 s rejoin watchdog, with a testable `handsOverJoinDeadlineToRejoin` predicate. Added JVM tests for Bluetooth-over-wired family priority and timeout ownership hand-over. Verified `testDebugUnitTest` (138 tests), `lintDebug` (0 errors) and `assembleDebug` pass. Device checks remain for sticky-broadcast removal with a headset already connected, partial headset disconnects and Bluetooth/USB coexistence.
- **Acceptance Criteria:**
  - No stale headset state is published after routing stops, and reset happens once per call start
  - Adding a wired/USB device cannot hijack an active Bluetooth intercom route
  - Sticky broadcasts are no longer replayed before routing becomes active
  - A partial headset disconnect preserves the rider's speaker choice and live SCO channel
  - WILL_JOIN hands the join deadline from the connect timeout to the rejoin watchdog
  - `testDebugUnitTest`, `lintDebug` and `assembleDebug` pass

### Task_57_FixRoutingStateMachineAndJoinDeadlineReviewFindings: Harden audio-route transitions and initial-join timeout ownership.
- **Status:** COMPLETED
- **Updates:** Applied the five review findings to the uncommitted audio/Jitsi changeset. (1) `updateAudioRouting()` now reconciles broadcast hints with a full detection through `resolveBroadcastHeadsetKind()`, so `ACTION_HEADSET_PLUG` can enable a wired route immediately without downgrading an active Bluetooth SCO intercom. (2) `handleScoStateChanged()` clears the forced-speaker flag and SCO state when a SCO event leaves no headset, via the shared `shouldClearSpeakerOverrideOnDisconnect()` predicate. (3) API 31+ wired/USB routes no longer enter the Bluetooth SCO retry loop; `shouldScheduleBluetoothRouteRetry()` restricts retries and the speaker fallback to active Bluetooth routes. (4) `JitsiCallManager` tracks `hasConnectedConference` so `CONFERENCE_WILL_JOIN` only hands the 30 s connect timeout over to the 150 s rejoin watchdog after a real `CONFERENCE_JOINED`; first joins keep the short deadline. (5) `AudioDeviceCallback.onAudioDevicesAdded()` ignores non-headset/wired-only batches during Bluetooth calls and preserves the retry attempt index (`resetRouteRetryAttempt = false`) so repeated enumeration cannot starve the timeout. Added JVM tests for all new predicates and updated the timeout hand-over test. Verified `testDebugUnitTest` (143 tests, 0 failures), `lintDebug` (0 errors) and `assembleDebug` (arm64-v8a, armeabi-v7a and universal APKs) pass; IDE analysis reports no errors on the changed Kotlin files. Manual device checks remain for wired/BT coexistence, SCO-disconnect headset swaps and real network-loss rejoin.
- **Acceptance Criteria:**
  - A wired plug event cannot replace a live Bluetooth intercom route
  - A SCO disconnect with no remaining headset resets stale speaker override state
  - Wired/USB routes never fall back to the speaker after a Bluetooth retry timeout
  - First joins retain the 30 s connect timeout; only rejoins use the 150 s watchdog
  - Audio-device callbacks cannot restart the Bluetooth retry sequence
  - `testDebugUnitTest`, `lintDebug` and `assembleDebug` pass

### Task_58_FixAudioRoutingAndWatchdogReviewFindings: Apply the remaining review findings.
- **Status:** COMPLETED
- **Updates:** Fixed the latest review findings in `BluetoothAudioRouter`, `JitsiCallManager` and their helpers. (1) The rider's speaker choice is now held by a stateful `RoutePreference` and latched after a Bluetooth route timeout, so the SCO DISCONNECTED broadcast cannot restart the retry loop; stateful JVM tests cover the latch, full-disconnect reset and rider toggle. (2) Switching to wired/USB uses `shouldReleaseScoForHeadsetKind()` to close SCO, so a later return to Bluetooth starts a fresh HFP session. (3) `resolveHeadsetKind()` treats an open SCO channel as proof of a headset and keeps Bluetooth priority over wired/USB; pure tests cover all branches. (4) `ReconnectDeadline` tracks elapsed-real-time remaining time with generation-free pause/resume; `JitsiCallManager` resumes the watchdog with the leftover duration instead of restarting the full 60/150 s timer, covered by fake-clock tests. (5) `bluetoothReceiver` ignores broadcasts while routing is inactive and `isRoutingActive` flips before receiver registration and before cleanup. (7) Bluetooth profile lookups run on a daemon thread with a cached, generation-guarded result applied on the main thread; connected broadcasts update the cache directly while disconnects trigger a fresh lookup. (8) Every `clearCommunicationDevice()` call goes through the API-31-guarded `clearPreferredDevice()` and speakerphone-flag writes are exception-guarded. Finding #6 was confirmed intentional: no-headset audio uses the built-in speaker, and `AudioRoutingState.EarpieceConnected` is now documented as an idle/reset state rather than an active output. Verified `assembleDebug` (all three ABI APKs), `testDebugUnitTest` (156 tests, 0 failures) and `lintDebug` (0 errors, 30 pre-existing warnings); device/emulator audio verification is still pending.
- **Acceptance Criteria:**
  - Bluetooth route timeout stays on the speaker until the rider explicitly retries the headset
  - Bluetooth → wired → Bluetooth handovers restart SCO; an open SCO channel is detected as a headset
  - Reconnect watchdog resumes with the time left before the outage, not a full new deadline
  - Broadcasts during call teardown cannot mutate routing state, and profile lookups never block the main thread
  - All communication-device/speakerphone writes are guarded and no-headset speaker-only behavior is documented
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_59_ClearQualityFindingsInAudioAndJitsiChanges: Remove Sonar/IDE smells without behavior changes.
- **Status:** COMPLETED
- **Updates:** Cleared the local SonarLint/IDE findings introduced by the uncommitted audio/Jitsi changeset. `BluetoothAudioRouter.kt` now uses read-only sequences for platform device arrays, splits SCO-state handling into documented helpers, and keeps legacy audio-focus branches out of min-SDK-26 code. `JitsiCallManager.kt` splits broadcast handling into helpers to lower cognitive complexity, uses a trailing lambda for participant callbacks, and formats multi-branch `when` entries per IDE rules. `HelmChatApp.kt` extracts route UI state/actions and navigation/permission helpers, uses destructuring and named arguments, and keeps route labels in a small composable helper. `CallScreen.kt` replaces chained `if/else` with `when` and fixes trailing commas. `AudioDeviceSelection.kt` uses sequences/destructuring and clarifying parentheses. IDE analysis now reports no errors or warnings in the changed Kotlin files; the only earlier non-actionable hint was the IDE's "open URL in browser" gutter action on configured Jitsi URLs. No behavior changes were intended, so no new tests were required. detekt is not configured in this project and was not run. Verified `./gradlew assembleDebug testDebugUnitTest lintDebug`: build successful, arm64-v8a/armeabi-v7a/universal debug APKs produced, 156 unit tests passed (0 failures/errors), lint 0 errors and 27 pre-existing warnings in unrelated files, and `git diff --check` clean. `./gradlew connectedAndroidTest` also passed on the available emulator (1 instrumented test, 0 failures); physical helmet-intercom audio and real network-loss verification remains pending.
- **Acceptance Criteria:**
  - IDE/Sonar analysis reports no actionable findings in changed Kotlin files
  - Static cleanup preserves audio-only routing and reconnect behavior
  - `assembleDebug`, `testDebugUnitTest`, `connectedAndroidTest`, `lintDebug` and `git diff --check` pass

### Task_60_StopRequestingAudioFocusDuringCalls: Keep background radio/music audible during an intercom call.
- **Status:** COMPLETED
- **Updates:** The router requested `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for `USAGE_VOICE_COMMUNICATION`, which by Android definition tells other apps to keep playing at a reduced level; on some OEM audio stacks the ducking is strong enough to sound like poor quality or temporary disappearance. `BluetoothAudioRouter` no longer imports `AudioAttributes`/`AudioFocusRequest`, no longer holds `audioFocusRequest` or an `OnAudioFocusChangeListener`, and no longer calls `requestAudioFocus()`/`abandonAudioFocusRequest()`. Jitsi's own focus handling remains disabled (`audio-focus.disabled=true`) and the router still owns `MODE_IN_COMMUNICATION`, HFP/SCO/wired/USB route selection and speaker fallback, so call routing and WebRTC voice processing are unchanged. KDoc/comments were updated to state that focus is left to radio/music apps and that Android/OEM communication policies may still lower `STREAM_MUSIC` independently. No new JVM tests were needed because no pure routing logic changed; this is platform audio-policy behavior. Verified `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` passed, 156 unit tests passed (0 failures/errors), lint reported 0 errors, arm64-v8a/armeabi-v7a/universal debug APKs were produced and `git diff --check` is clean. Physical-phone retest is still required; the emulator cannot reproduce real OEM ducking.
- **Acceptance Criteria:**
  - Neither Jitsi nor `BluetoothAudioRouter` requests or abandons audio focus for the call
  - Background radio/music keeps its own focus and is not explicitly ducked or paused by this app
  - Communication mode, headset routing, SCO retries and reconnect behavior remain unchanged
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and `git diff --check` pass
  - Manual: play radio/music, join a call and confirm the background level stays audible and does not disappear; retest USB speaker persistence and BT/USB connect/disconnect as well
  - Device verification for the "music disappears" symptom: start music/radio and note its level; capture `adb logcat` with `AudioFocusManager|AudioPolicyManager|BluetoothAudioRouter` tags while joining; run `adb shell dumpsys audio` before and during the call to confirm this app has no voice-communication focus entry and that `MODE_IN_COMMUNICATION` is active; compare the media level on the affected phone. If media is still attenuated, the cause is communication-mode ducking that focus removal cannot fix, and that finding must be recorded instead of claiming the symptom is resolved.

### Task_61_ApplyAudioFocusReviewFixes: Harden mode recovery, documentation and lint scoping.
- **Status:** COMPLETED
- **Updates:** Applied three review fixes to the uncommitted audio-focus changeset. (1) `BluetoothAudioRouter` class and `startAudioRouting()` KDoc now state precisely that the app takes no focus and does not explicitly duck or pause other apps, while `MODE_IN_COMMUNICATION` can still trigger platform-level attenuation of `STREAM_MUSIC`; the concrete device verification procedure for the reported symptom was added to Task_60. (2) Added the testable `shouldReassertCommunicationMode` predicate in `AudioDeviceSelection.kt` and a private `ensureCommunicationMode()` in `BluetoothAudioRouter` that restores communication mode from `startAudioRouting()`, `updateAudioRouting()` and `handleScoStateChanged()` whenever routing is active; teardown clears `isRoutingActive` first so no callback can resurrect the mode. Added a JVM test covering active/wrong-mode, active/correct-mode and inactive cases. No audio focus is reintroduced. (3) Scoped `tools:ignore="PictureInPictureIssue"` from `<application>` to the `MainActivity` element, alongside the existing portrait-lock suppression, with an XML comment explaining the deliberate portrait + compact-PiP combination. Verified `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` passes: 157 JVM tests pass (0 failures/errors), lint reports 0 errors, and arm64-v8a/armeabi-v7a debug APKs are produced. IDE analysis found no errors in the changed files (two pre-existing style warnings elsewhere). Lint still reports 26 pre-existing warnings, including `PictureInPictureIssue`: this lint version reports that check on the `<application>` element, so the activity-level suppression documents the intent but cannot silence it; the application-level suppression was not restored because it would contradict the requested scoping. Physical-phone verification of background-media level and mid-call mode recovery is still required.
- **Acceptance Criteria:**
  - Comments no longer claim that focus removal guarantees an audible background stream
  - Communication mode is restored focus-independently while a call is active and never after teardown
  - The PiP lint suppression applies only to `MainActivity` and is documented
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and new predicate tests pass

### Task_62_RouteToSpeakerAfterBluetoothDisconnect: Drop stale Bluetooth signals after a confirmed HFP loss.
- **Status:** COMPLETED
- **Updates:** After a helmet HFP disconnect the fresh profile lookup could report no headset while the legacy SCO switch and cached profile still looked connected, so `updateAudioRouting()` kept resolving `HeadsetKind.BLUETOOTH`, left the rider's speaker choice intact and published `ConnectingBluetooth` instead of switching to the phone speaker. Added `shouldIgnoreStaleBluetoothSignals(...)` and an `includeBluetooth` option to `resolveHeadsetKind(...)`; `applyBluetoothProfileCache()` now ignores SCO/cached-profile signals only when the completed lookup proves there is no HFP headset, while a still-connected HFP device or a remaining wired/USB headset keeps the previous route. `updateAudioRouting()` passes that decision into detection, so a confirmed Bluetooth-only disconnect clears the speaker latch, stops SCO, routes to the built-in speaker and publishes `SpeakerConnected`; `_isHeadsetConnected` becomes false, making the route button show the speaker and stay non-switchable. Added JVM tests for the predicate, stale-SCO fallback to speaker and wired/USB preservation. Verified `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` passes: 160 JVM tests pass (0 failures/errors), lint reports 0 errors and the same 26 pre-existing warnings, IDE analysis found no errors in the changed files, and `git diff --check` is clean. Physical-device retest is still required to confirm OEM disconnect timing and that a second HFP or wired/USB headset handover remains correct.
- **Acceptance Criteria:**
  - A confirmed Bluetooth disconnect no longer leaves the app in `ConnectingBluetooth` when no other headset remains
  - A remaining HFP, wired or USB headset still wins over the phone speaker
  - The route button follows published headset availability without a manual toggle
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and the new tests pass

### Task_63_KeepHeadsetAfterSpeakerToggle: Distinguish app-initiated SCO teardown from a real disconnect.
- **Status:** COMPLETED
- **Updates:** Toggling the built-in speaker called `stopBluetoothSco()`, whose `ACTION_SCO_AUDIO_STATE_UPDATED`/DISCONNECTED broadcast was handled as a physical headset removal. `refreshProfileAfterScoDisconnect()` then refreshed HFP with `headsetDisconnected=true`; an empty lookup fed `shouldIgnoreStaleBluetoothSignals`, which resolved `HeadsetKind.NONE`, cleared the rider's speaker choice and set `_isHeadsetConnected=false`. The headset stayed invisible until the rider restarted Bluetooth. Added the testable `isExpectedScoDisconnect(...)` predicate and a `isScoStopIntentional` flag set immediately before the router calls `stopBluetoothSco()` (speaker toggle, wired handover). `handleScoStateChanged()` now skips the profile refresh and speaker-latch reset for those self-inflicted broadcasts and preserves the last known headset family, so toggling back restores the helmet route. Also excluded `AudioManager.MODE_IN_CALL` from `shouldReassertCommunicationMode(...)` so an incoming phone call keeps its telephony-owned audio mode. Added JVM tests for the new predicate and the telephony mode case. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon` passes (26 tests in `AudioDeviceSelectionTest`, 0 failures/errors; lint 0 errors; build successful). Physical-device retest of speaker → helmet toggling is still required.
- **Acceptance Criteria:**
  - Speaker toggle and wired handover no longer detach a still-connected headset from the app
  - A genuine headset disconnect still clears availability and routes to the phone speaker
  - Telephony `MODE_IN_CALL` is never overwritten by communication-mode recovery
  - `assembleDebug`, `testDebugUnitTest`, `lintDebug` and the new predicate tests pass

### Task_64_HardenAudioRoutingReviewFixes: Apply five scoped routing and lint follow-ups.
- **Status:** COMPLETED
- **Updates:** Applied the five requested review fixes to the uncommitted audio changeset, using simple names and imports throughout. (1) `BluetoothAudioRouter` now persists the confirmed HFP-disconnect decision in `ignoreStaleBluetoothSignals` instead of forwarding a one-shot argument: `applyBluetoothProfileCache()` advances the latch through `advanceStaleBluetoothSignalSuppression()`, `detectHeadsetKind()` reads the field (the one-shot `ignoreStaleBluetoothSignals` parameter was removed from `updateAudioRouting()` and `includeBluetooth` from `detectHeadsetKind()`), and the latch clears on a fresh positive HFP lookup, profile/SCO CONNECTED broadcast or new call. A stale SCO DISCONNECTED broadcast can no longer resurrect the intercom route. (2) `isExpectedScoDisconnect()` now takes only the SCO state and the local-stop token; the forced-speaker preference is no longer evidence of an app-initiated stop, so a physical disconnect during speaker playback is detected correctly. (3) `shouldReassertCommunicationMode()` also excludes `AudioManager.MODE_RINGTONE`, so an incoming GSM call keeps its telephony-owned mode. (4) Added a bounded `MODE_RECOVERY_INTERVAL_MS` watchdog (`scheduleModeRecovery()`/`cancelModeRecovery()`, guarded by `shouldContinueModeRecovery(isRoutingActive)`) that restores communication mode without any headset event, is armed in `startAudioRouting()` and cancelled in `stopAudioRouting()` before teardown changes audio state. (5) Moved `tools:ignore="PictureInPictureIssue"` from the activity to the `<application>` element where lint reports it and updated the adjacent XML comment; the portrait lock and PiP implementation are unchanged. Added JVM tests for the persistent decision, stale-SCO fallback, local-stop authority, ringing mode and watchdog guard. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` passes: 164 tests (0 failures/errors), lint 0 errors / 25 warnings with `PictureInPictureIssue` gone from `lint-results-debug.txt`, all three ABI debug APKs produced and `git diff --check` clean. Physical-helmet verification is still required.
- **Acceptance Criteria:**
  - A confirmed HFP disconnect keeps Bluetooth signals suppressed until a fresh positive connection
  - A forced speaker no longer masks a genuine SCO disconnect
  - `MODE_RINGTONE` is never overwritten with `MODE_IN_COMMUNICATION`
  - Communication mode recovers during an active call without headset events and never after teardown
  - `PictureInPictureIssue` no longer appears in the lint report
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_65_StabilizeIntercomRouteWithBackgroundA2dpMusic: Keep the named headset label during transient SCO errors.
- **Status:** COMPLETED
- **Updates:** Background music playing over the same helmet's A2DP profile causes `ACTION_AUDIO_BECOMING_NOISY` when A2DP is suspended for the SCO call, and transient SCO errors published `AudioRoutingState.Error`, which the UI rendered as the generic `Interkom / słuchawki`. The fix keeps the existing noisy-audio disconnect handling intact (restored in `Task_66`) and only stabilizes the published state after a transient SCO error. Added the pure `resolveScoErrorRoutingState(kind, lastConnectedName)` decision: a transient error while a Bluetooth or wired headset remains attached keeps the last named route (or `ConnectingBluetooth` until a name is known), while no headset still reports `Error`. `BluetoothAudioRouter` now remembers the name through `publishHeadsetRoute(...)`, reuses it for all headset route assignments, and clears it at call teardown/reset boundaries so it cannot leak to a new call. The bounded `scheduleRouteRetry(kind)` retry remains unchanged, so a real failure is still retried. Added JVM tests for named Bluetooth/wired retention, unnamed fallback and the no-headset error case. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` passes (167 tests, 0 failures/errors; lint 0 errors / 25 warnings) and `git diff --check` is clean. Physical-helmet retest with A2DP music is still required; if the label still flaps, capture the SCO/noisy-audio logs before changing the watchdog.
- **Acceptance Criteria:**
  - `ACTION_AUDIO_BECOMING_NOISY` keeps its original disconnect hint for route detection (corrected in `Task_66`)
  - A transient SCO error with a headset still attached keeps the named `Interkom: nazwa` label
  - A real failure with no headset still reports an error and retries the Bluetooth route
  - The remembered device name cannot survive call teardown/reset
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_66_RestoreNoisyAudioDisconnectHint: Fix the Bluetooth routing regression from Task_65.
- **Status:** COMPLETED
- **Updates:** The user reported that after `Task_65` the intercom label stayed visible but call audio went to the phone earpiece/speaker instead of Bluetooth, with additional speaker dropouts. The only routing-affecting change in `Task_65` was treating `ACTION_AUDIO_BECOMING_NOISY` as a pure re-detection hint. That broadcast can be the platform's real headset-removal signal, so `BluetoothAudioRouter` again calls `updateAudioRouting(headsetDisconnected = true)` and keeps the original route-detection semantics. The state-only `resolveScoErrorRoutingState()` fix remains: it prevents a transient SCO error from replacing the named intercom label, without changing the physical route or the retry sequence. Also replaced the new `if (name != null)` in `publishHeadsetRoute()` with `name?.let { ... }` to clear an IDE style warning. Re-verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` (167 tests, 0 failures/errors; lint 0 errors / 25 warnings), `git diff --check` clean, and IDE analysis shows no issue in the changed Kotlin code. The remaining IDE warning at `BluetoothAudioRouter` line 358 predates this task. Physical-helmet retest is still required; speaker dropouts may involve the communication-mode watchdog from `Task_64` and need device logs if they persist.
- **Acceptance Criteria:**
  - `ACTION_AUDIO_BECOMING_NOISY` again passes the disconnect hint to route detection
  - Transient SCO errors still keep the named intercom label
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_67_FixScoDeviceMisclassifiedAsWired: Restore Bluetooth routing and reconnection.
- **Status:** COMPLETED
- **Updates:** The user reported that Bluetooth still never routed to the helmet after `Task_66`: the control showed the generic `Interkom / słuchawki`, audio went to the phone, and reconnecting after an interruption failed. Root cause: when `ignoreStaleBluetoothSignals` was latched by `Task_64`, `detectHeadsetKind()` still counted `TYPE_BLUETOOTH_SCO` as a wired headset because `isHeadsetDeviceType()` includes SCO. That resolved to `HeadsetKind.WIRED`, whose route calls `stopBluetoothSco()`, clears the communication device and publishes `HeadsetConnected(null)` — exactly the observed label and phone output. Added the pure `hasNonBluetoothHeadsetDevice()` helper and used it in `detectHeadsetKind()`, so suppressed Bluetooth SCO devices no longer create a false wired route. Also tightened `shouldIgnoreStaleBluetoothSignals()` to require an explicit disconnect event plus a negative HFP lookup; a lone negative lookup can be a transient permission/profile race and must not block reconnection. Added JVM tests for the SCO-not-wired helper and updated the suppression tests. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`: 168 tests, 0 failures/errors; lint 0 errors / 26 warnings (no findings in changed files, one extra dependency-version warning); `git diff --check` clean. IDE analysis tooling is unavailable in this session. Physical-helmet retest is still required, especially reconnect after an interruption.
- **Acceptance Criteria:**
  - A suppressed SCO device is no longer treated as a wired headset
  - A transient negative HFP lookup no longer latches Bluetooth suppression
  - A confirmed disconnect still suppresses stale SCO signals and uses the phone speaker
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_68_IgnoreFailedHfpLookupOnDisconnect: Keep the helmet route when the HFP query fails.
- **Status:** COMPLETED
- **Updates:** The S25 logcat captured in `.agent/helmchat-logcat.txt` showed `Bluetooth profile 1 is unavailable: Profile not supported: 1` on every profile query, followed by `SCO audio state updated: 0` and an immediate second failed query. `hasConnectedBluetoothHeadset()` caught that exception and returned `false`, so the SCO disconnect broadcast plus a "negative" lookup latched `ignoreStaleBluetoothSignals` even though the `TYPE_BLUETOOTH_SCO` device was still enumerated. Renamed the pure helper to `queryConnectedBluetoothHeadset()` and made it return `Boolean?`, where `null` means the HFP query failed or the profile is unavailable. The router now passes that result through `applyBluetoothProfileLookup()`, which ignores `null` and leaves route detection to the enumerated SCO device and the real platform disconnect broadcasts. A genuinely empty HFP result still updates the cache and can confirm a disconnect. Updated the JVM lookup tests for the tri-state result. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`: 168 tests, 0 failures/errors; lint 0 errors / 26 warnings (no findings in changed files); `git diff --check` clean. IDE analysis tooling is unavailable in this session. Physical S25 retest with the helmet intercom is still required.
- **Acceptance Criteria:**
  - A failed/unsupported HFP lookup is no longer treated as proof that the headset disconnected
  - A successful empty HFP lookup still confirms a disconnect and suppresses stale SCO signals
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_69_RestoreRouteAfterReconnect: Fix stale HFP cache and timeout latch on reconnect.
- **Status:** COMPLETED
- **Updates:** The second S25 logcat (`.agent/helmchat-logcat-2.txt`) confirmed `Task_68` works (`HFP lookup unavailable; keeping the enumerated headset route`), but exposed two remaining application bugs. First, after the real HFP audio disconnect the query still failed, so `cachedBluetoothHeadsetConnected` stayed `true` from the earlier SCO connection; device removal and `ACTION_AUDIO_BECOMING_NOISY` then resolved to `HeadsetKind.BLUETOOTH`, so the rider's speaker latch was never cleared and the router kept believing the helmet was attached. Added the pure `hasBluetoothScoDevice()` and `shouldConfirmBluetoothDisconnectFromDevices()` helpers; the router now clears the cached HFP state and latches stale-signal suppression when the last SCO output is removed or when noisy audio arrives with no SCO output. Second, `RoutePreference.latchSpeakerAfterTimeout()` created a speaker preference that survived a genuine headset reconnect. Added `isTimeoutLatch` and `clearTimeoutLatchOnHeadsetConnected()` so a real plug/profile/SCO/device-added connection restores automatic headset routing, while an explicit rider toggle is untouched. Added JVM tests for both rules. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`: 174 tests, 0 failures/errors; lint 0 errors / 26 warnings (no findings in changed files); `git diff --check` clean. IDE analysis tooling is unavailable in this session. Physical S25 retest with the helmet intercom is still required.
- **Acceptance Criteria:**
  - Removing the last SCO output clears the stale HFP cache even when the OEM HFP query fails
  - A headset reconnect after a route timeout restores automatic headset routing
  - An explicit rider speaker choice still survives headset connection events
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_70_UpdateInviteCopyAndPipPosition: Apply the requested UI changes.
- **Status:** COMPLETED
- **Updates:** Changed `home_invite_message` in all 30 locale resource files. `values-pl/strings.xml` now contains exactly `Dołącz do mojej rozmowy w HelmChat!\nPokój: %1$s\nLink: %2$s`; the English fallback and the other translations now use "conversation/rozmowa" wording instead of "intercom". In `CallScreen.kt`, the Picture-in-Picture `IconButton` moved from the combined top-end row to its own `Alignment.TopStart` overlay, while the invite button stays at `Alignment.TopEnd`. The 64 dp touch target and 8 dp margin were preserved, and the `CallContent` KDoc now mentions the opposite top-corner actions. No routing, Jitsi, permission or PiP behavior changed. Verified `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`: 174 tests, 0 failures/errors; lint 0 errors / 26 warnings (only pre-existing JWT typo warnings on unchanged string lines); all 30 invite resources keep both format arguments; `git diff --check` clean. Physical S25 retest with the helmet intercom is still required.
- **Acceptance Criteria:**
  - Polish invite text matches the requested string exactly
  - PiP icon is displayed at the top-left and invite at the top-right of the call screen
  - `assembleDebug`, `testDebugUnitTest` and `lintDebug` pass

### Task_71_UpdateReleaseNotesForV16: Document the V1.6 maintenance changes.
- **Status:** COMPLETED
- **Updates:** `RELEASE_NOTES.md` now records a `Last updated` date (now 2026-11-22), a Highlights pointer, and a new `V1.6 maintenance updates` section. It covers audio-focus removal, communication-mode recovery/watchdog, SCO/HFP disconnect and reconnect fixes, stable named intercom labels, the invite-copy and PiP-position UI changes, and project hygiene. Testing was updated with the current JVM/lint/APK results and Known limitations now states that physical S25 retesting with the actual helmet intercom is still pending. No application code or version metadata changed; `git diff --check` is clean.
- **Acceptance Criteria:**
  - Release notes describe the implemented V1.6 maintenance changes
  - Version metadata and original release date remain unchanged
  - The Highlights link resolves to the new section

### Task_72_RestructureReleaseNotesByRelease: Make the release history coherent.
- **Status:** COMPLETED
- **Updates:** Rewrote `RELEASE_NOTES.md` as chronological release history: 1.4 → 1.5 → 1.6. Added a release matrix with version code, tag, date, application ID and SDK levels for each release, then used the same Summary/Highlights/Changes/Verification-or-caveats layout for every release. Corrected per-release attribution using the `V1.4`, `V1.5` and `V1.6` tag ranges; separated committed `V1.6` changes from current working-tree follow-ups; and moved shared feature/build/upgrade information into dedicated reference sections. Verified all three table-of-contents anchors resolve and `git diff --check` is clean. No application code changed.
- **Acceptance Criteria:**
  - Release notes describe each release separately in chronological order
  - 1.4, 1.5 and 1.6 metadata and changes are attributed to the correct release
  - Table-of-contents links resolve and `git diff --check` passes

### Task_73_RestoreLatestReleaseMetadataTable: Put the current release summary back at the top.
- **Status:** COMPLETED
- **Updates:** Restored the original two-column metadata table at the top of `RELEASE_NOTES.md` for the latest release: version, version code, tag, release date, last updated, minimum Android, target/compile SDK and application ID. The chronological release history and release matrix remain below it. `git diff --check` is clean; no application code changed.
- **Acceptance Criteria:**
  - The first table in `RELEASE_NOTES.md` describes the latest 1.6 release
  - The per-release history remains intact
  - `git diff --check` passes

### Task_74_CorrectReleaseDocumentationForV16: Align README and release notes with the final tree.
- **Status:** COMPLETED
- **Updates:** Corrected the README and `RELEASE_NOTES.md` to match the shipped 1.6 tree: release date now `2026-11-22`, compileSdk 37.2, Gradle 9.6.0 / AGP 9.5.2 with no KSP, JDK 21 daemon, the `dev.communicator.helmchatrisen` source path, the Jitsi patch scope (reload countdown only), 30 locales, and the exact AAB/per-ABI APK names. Documented the launcher label `HelmChat Risen`, in-app title `HelmChat - Risen`, the 1.4 → 1.5 separate-app upgrade and unchanged-ID 1.5 → 1.6 in-place upgrade, all four required signing properties, the unsigned-output warning, `apksigner verify` checks and the separate `bundleRelease` invocation. Clarified that `V1.6` is a branch and the annotated tag must still be created from the final commit. Added `.agent/*logcat*.txt` to `.gitignore` so raw device logs cannot be committed. No application behavior changed.
- **Acceptance Criteria:**
  - README and release notes match version, package, SDK, build, locale, output-name and upgrade facts in the tree
  - Signing requirements, unsigned-output warning and artifact verification are explicit
  - Raw logcat captures are ignored by Git

### Task_75_FixFailedCallLifecycle: Release call resources immediately on `CallState.Error`.
- **Status:** COMPLETED
- **Updates:** `JitsiCallManager` now routes join exceptions and both timeout paths through `failCall()`, which cancels all call timers, unregisters the network callback, mutes and hangs up Jitsi, releases the embedded view and finalizes the session before publishing `CallState.Error`. AudioCallService stops the microphone foreground service as soon as the error arrives; the manager keeps the message visible for `ERROR_DISPLAY_TIMEOUT_MS = 4 s` and then publishes `Idle` so Navigation 3 removes the call route. New terminal-state filtering prevents late Jitsi broadcasts and termination events from reviving a failed/finished call. Added JVM tests for terminal-state classification and the bounded error display timeout.
- **Acceptance Criteria:**
  - A failed call releases Jitsi, routing and the foreground service instead of lingering until teardown
  - The localized error stays readable briefly before the call route is dismissed
  - Late SDK events cannot revive `Idle`, `Ending` or `Error`
  - New and existing JVM tests pass

### Task_76_HardenBluetoothReconnectAndTelephonyTeardown: Clear stale suppression and respect GSM modes.
- **Status:** COMPLETED
- **Updates:** Added `shouldClearBluetoothSuppressionOnReconnect(forcedKind, hasScoDevice)` so an `AudioDeviceCallback` that re-enumerates a Bluetooth SCO output clears a confirmed-disconnect suppression latch, allowing a returning helmet to be routed automatically. Added `shouldRestoreNormalAudioMode(currentMode)` so intercom teardown leaves `MODE_IN_CALL` and `MODE_RINGTONE` untouched for an incoming/active GSM call, along with its SCO, communication-device and speakerphone state. Added JVM tests for the reconnect latch and telephony-owned teardown modes.
- **Acceptance Criteria:**
  - A re-enumerated SCO device clears stale Bluetooth suppression without waiting for a fresh HFP query
  - Telephony-owned audio modes survive intercom teardown
  - New and existing JVM tests pass

### Task_77_VerifyV16ReleaseCandidate: Run final build, test, lint and packaging checks.
- **Status:** COMPLETED
- **Updates:** Final verification on branch `V1.6` ran `git diff --check`, `./gradlew testDebugUnitTest assembleDebug lintDebug --console=plain`, `./gradlew assembleRelease --console=plain` and `./gradlew bundleRelease --console=plain`. All passed: 178 JVM tests with 0 failures/errors/skips, lint 0 errors / 25 warnings, three debug APKs, three release APKs and one AAB. Current release outputs are unsigned because no signing properties were supplied, so they are not publishable; `apksigner verify --print-certs` must be rerun after a signed rebuild. No device/emulator was attached for the final pass, so instrumented and physical helmet/PiP checks remain manual. Changes are still uncommitted and the `V1.6` tag still needs to be created from the final release commit. Superseded by `Task_79` after the background-audio regression fix; the final tree has 177 tests.
- **Acceptance Criteria:**
  - Final tree passes diff check, JVM tests, debug build, lint, release APK build and AAB build
  - Artifact names and counts match the release documentation
  - Unsigned-output status, missing tag and manual device checks are recorded as release blockers/steps

### Task_78_UpgradeAgpTo952: Pin Android Gradle Plugin 9.5.2.
- **Status:** COMPLETED
- **Updates:** Bumped the `agp` version catalog entry from 9.5.1 to 9.5.2 and aligned the current-build references in `README.md`, `RELEASE_NOTES.md` and the project plan. The historical 1.4 release-notes entry keeps its original AGP 9.5.1 fact. Verified `./gradlew help`, then `testDebugUnitTest assembleDebug lintDebug`, `assembleRelease` and `bundleRelease` in separate invocations with `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`: 178 JVM tests pass (0 failures/errors/skips), lint reports 0 errors / 25 warnings, and all three debug APKs, three release APKs and the AAB are produced. The default worker/configuration-cache attempt hit local AAPT2 daemon/JdkImageTransform startup limits; the constrained command used previously in this project succeeds reliably. Superseded by `Task_79` for the final regression-fix verification (177 tests).
- **Acceptance Criteria:**
  - `gradle/libs.versions.toml` pins AGP 9.5.2 and current docs match
  - Debug unit tests, debug build, lint, release APK build and AAB build pass with AGP 9.5.2

### Task_79_RestoreBackgroundAudioDuringIntercom: Remove repeated communication-mode transitions.
- **Status:** COMPLETED
- **Updates:** The user reported that the review changes made background player/radio audio disappear again. Root cause: `d4b90ce` added a 1 s `MODE_IN_COMMUNICATION` watchdog and reasserted the mode from routing/SCO callbacks; repeated transitions tell OEM audio policies to duck or pause `STREAM_MUSIC`. Replaced `shouldReassertCommunicationMode`/`shouldContinueModeRecovery` with `shouldEnterCommunicationMode`, removed `MODE_RECOVERY_INTERVAL_MS`, the recovery runnable and all mid-call `ensureCommunicationMode()` calls, and kept one mode entry in `startAudioRouting()`. Teardown still leaves `MODE_IN_CALL`/`MODE_RINGTONE` telephony-owned. Updated the JVM test to cover one-shot entry and removed the obsolete watchdog test, giving a final count of 177. Re-verified `git diff --check`, `testDebugUnitTest assembleDebug lintDebug`, `assembleRelease` and `bundleRelease` with the reliable constrained Gradle command: 177 JVM tests pass (0 failures/errors/skips), lint reports 0 errors / 25 warnings, and all three debug APKs, three release APKs and the AAB are rebuilt. Updated `RELEASE_NOTES.md` with the regression fix, the one-shot guard test and the device retest requirement with player/radio running. No device/emulator was attached, so the physical audio check remains manual; the release outputs are still unsigned and the `V1.6` tag is still to be created after the fix is committed.
- **Acceptance Criteria:**
  - Communication mode is entered only once at call start and no watchdog or routing callback reasserts it
  - Telephony-owned modes and teardown behavior remain unchanged
  - JVM tests, debug build, lint, release APK build and AAB build pass after the regression fix

### Task_80_UpdateV16ReleaseDate: Change the 1.6 release date to 2026-11-22.
- **Status:** COMPLETED
- **Updates:** Updated `appReleaseDate` in `app/build.gradle.kts`, the release/last-updated fields, history link, release matrix and 1.6 heading in `RELEASE_NOTES.md`, and the current-date references in `.agent/plan.md` from 2026-11-21 to 2026-11-22. Historical 1.4/1.5 dates and raw logcat files were left untouched. Rebuilt `BuildConfig.RELEASE_DATE`, debug APKs, release APKs and the AAB with the reliable constrained Gradle command: 177 JVM tests pass (0 failures/errors/skips), lint reports 0 errors / 25 warnings, and generated debug/release BuildConfig both contain `2026-11-22`.
- **Acceptance Criteria:**
  - All current 1.6 metadata and documentation show release date 2026-11-22
  - Compiled `RELEASE_DATE` and all rebuilt artifacts match the documented date

### Task_81_FixSpeakerToggleAudioRouting: Keep the rider's speaker choice authoritative across SCO events.
- **Status:** COMPLETED
- **Updates:** Reviewed all changes since `Task_74_CorrectReleaseDocumentationForV16` and the new `.agent/helmchat-logcat.txt` timeline. The log confirms the `00:26:01` tap was a route toggle to the phone speaker (SCO stopped, `setSpeakerphoneOn(true)`, preference `speaker`), not a hang-up, and that no audio-focus request or ducking event from this app appears in the capture. The concrete code defect was that the local SCO stop's device-removal/noisy side effects and intermediate SCO states could still clear the rider's explicit choice, after which SCO re-enumeration routed intercom audio back to the helmet. Added testable helpers for SCO disconnect state, local-stop token retirement, expected SCO removal and local noisy events. `BluetoothAudioRouter` now ignores SCO-only device removals/noisy broadcasts while its own stop token is pending, keeps that token through CONNECTING until DISCONNECTED/CONNECTED, clears it on authoritative plug/profile/SCO-device events, clears speaker override only on a genuine SCO disconnect, re-detects the headset when toggling back, and guards timeout/suppression resets during local stops. `stopAudioRouting()` and `resetForNewCall()` now both leave telephony-owned route state untouched, including the pending SCO flag. Added 4 JVM tests (181 total). Updated `RELEASE_NOTES.md` with the fix and an explicit limitation: the button changes call audio only; Android may restore A2DP so background radio/music can still play in the helmet, and rerouting another app's media is not attempted. Verified `git diff --check`, `testDebugUnitTest assembleDebug lintDebug` (181 tests, 0 failures/errors/skips; lint 0 errors / 25 warnings), `assembleRelease` and `bundleRelease` with the reliable constrained Gradle command; all three debug APKs, three release APKs and the AAB rebuilt. The first `connectedAndroidTest` attempt on attached `R5CX9339VAJ` was blocked by the existing package signature (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). After the user approved removing that install, the run completed with 1 instrumented test passing (0 failures, 0 skipped); `adb uninstall` still reported `DELETE_FAILED_INTERNAL_ERROR`, but the Gradle runner replaced the app and executed the test. Release outputs remain unsigned and the `V1.6` tag is still pending. Final IDE analysis is clean on all four changed Kotlin files after the clarifying-parentheses and named-argument cleanups. `assembleRelease` was rerun separately after `bundleRelease` because combining both tasks disables ABI splits for the whole invocation; the three documented release APKs and the AAB are present.
- **Acceptance Criteria:**
  - Intercom call audio stays on the phone speaker after a toggle until the rider switches back or a genuine disconnect occurs
  - Local SCO-stop side effects cannot clear rider or timeout route state; telephony-owned routes survive teardown/reset unchanged
  - Background-media limitation is documented and physical verification with a radio/player and real helmet remains a manual release step
  - JVM tests, debug build, lint, release APK build and AAB build pass after the fix

### Task_82_MoveMediaWithCallRouteForSingleRider: Keep route parity with multi-rider calls.
- **Status:** COMPLETED
- **Updates:** The rider confirmed that the speaker toggle moves both intercom and player/radio audio when two or more people are in the room, but with only the initiating rider it moves only the call audio. Jitsi opens a `USAGE_VOICE_COMMUNICATION` playback track only after another participant joins, and OEM policies use that active track to move `STREAM_MUSIC` with the call route. Added a silent, streaming voice-communication `AudioTrack` that starts with routing and is released on teardown/new-call reset, including when telephony owns the platform mode. It carries only zero samples, takes no audio focus and does not duck other apps. Added `silentCommunicationBufferSize()` plus a JVM test for whole PCM frames. Updated `BluetoothAudioRouter` and release-note KDocs/docs to describe the new route-parity behavior. Verification: `git diff --check` clean; `testDebugUnitTest assembleDebug lintDebug` passed with 182 JVM tests (0 failures/errors/skips) and lint 0 errors / 25 warnings; `bundleRelease` and `assembleRelease` passed in separate invocations; `connectedAndroidTest` passed on `R5CX9339VAJ` with 1 test after stale incompatible test packages were removed. Release outputs are unsigned because the four signing properties were not supplied. Physical S25 retest with a real helmet intercom and running player/radio remains a manual release step.
- **Acceptance Criteria:**
  - A lone rider's speaker toggle uses the same platform call-route policy as a multi-rider call
  - The silent keep-alive starts once per call, releases on teardown/reset, and never takes audio focus
  - Telephony-owned audio mode/route handling remains unchanged
  - JVM tests, debug build, lint, release APK build and AAB build pass after the change
  - Instrumented test passes on the connected S25; physical route-parity retest with a real helmet intercom and running player/radio remains manual
