---
name: Jitsi SDK
description: Working with the Jitsi Meet Android SDK in Kotlin apps - audio-only VoIP integration, SDK dependency upgrades, conference configuration, permissions and foreground service constraints.
---

# Jitsi Meet Android SDK

## Documentation

- Android SDK developer guide: https://jitsi.github.io/handbook/docs/dev-guide/dev-guide-android-sdk
- React Native SDK developer guide: https://jitsi.github.io/handbook/docs/dev-guide/dev-guide-react-native-sdk
- Configuration reference: https://jitsi.github.io/handbook/docs/dev-guide/dev-guide-configuration

Always use the latest stable SDK version available. Verify the currently used
version in the project's version catalog (`gradle/libs.versions.toml`, artifact
`org.jitsi.react:jitsi-meet-sdk`) before adding or upgrading the dependency.

## Workflow

1. Check the SDK version in `gradle/libs.versions.toml` before any dependency change.
2. Fetch the developer guide before using an SDK API for the first time
   (`JitsiMeetActivity`, `JitsiMeetView`, `JitsiMeetConferenceOptions`,
   broadcast/receiver integration).
3. Pass configuration flags via `JitsiMeetConferenceOptions.Builder` and verify
   flag names against the configuration reference (feature flags differ between
   SDK releases).
4. Re-check the handbook after a version upgrade - flags and APIs may have changed.

## Audio-only rules

- Keep video disabled (audio-only mode) and bandwidth conservative; never enable
  video capture or unnecessary high-bitrate audio for audio-only apps.
- Audio routing through Android `AudioManager` and Bluetooth/HFP headset
  profiles must handle lifecycle changes correctly (route switches when the
  headset connects/disconnects, call start/stop).
- Manifest permissions to review when touching call code: `RECORD_AUDIO`,
  `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT` (API 31+), plus any foreground
  service declaration - be careful with foreground service constraints and
  explain any additions.

## Version-specific notes (HelmChatRisen)

- This project pins the FLOSS source build of **13.1.1** from
  `scripts/build-jitsi-libre-sdk.sh` (`LIBRE_BUILD=true`), not a prebuilt AAR.
  Keep the audio-only path: 13.1.1 still has
  `JitsiMeetConferenceOptions.Builder.setAudioOnly()`; 13.2.0+ renamed it to
  `setLowBandwidthMode()`, so an SDK bump needs a source change.
- 13.1.1 starts the bridgeless ReactHost from `JitsiInitializer` (an
  `androidx.startup` provider) during Application startup, before any app code.
  The host theme MUST descend from `Theme.AppCompat` or React Native aborts with
  "The style on this component requires your app theme to be Theme.AppCompat".
  The app uses `Theme.AppCompat.NoActionBar` and an instrumented smoke test
  constructs an AppCompat widget with the app context to guard this.
- `JitsiMeetActivityDelegate.onHostResume()` is null-guarded while the host is
  not started, so forwarding it from both the Activity and a Compose-hosted
  `JitsiMeetView` is safe, but keep only the calls the lifecycle actually needs.
- `JitsiMeet.setDefaultConferenceOptions()` in 13.1.1 only stores a field; it
  does not start React Native. Startup work happens in `ReactHostHolder`.
