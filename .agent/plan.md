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
