---
name: F-Droid
description: Auditing and preparing Android apps for F-Droid - FLOSS licensing, non-free dependencies, anti-features, build metadata, versioning, reproducible builds, and the fdroid import/submission workflow.
---

# F-Droid readiness

## Documentation

- Overview: https://f-droid.org/en/docs/
- FAQ for app developers: https://f-droid.org/en/docs/FAQ_-_App_Developers/
- Importing applications: https://f-droid.org/en/docs/Importing_Applications/
- Inclusion policy (authoritative rules): https://f-droid.org/en/docs/Inclusion_Policy/
- Anti-features: https://f-droid.org/en/docs/Anti-Features/
- Build metadata reference: https://f-droid.org/en/docs/Build_Metadata_Reference/
- Reproducible builds: https://f-droid.org/en/docs/Reproducible_Builds/
- Quick start: https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/

Fetch the current pages before an audit or submission: the policy and the list of trusted repositories change over time.

## Audit checklist

### 1. Licence and source
- Application, bundled assets and every dependency must be FLOSS (DFSG/FSF/GNU/OSI approved). Keep LICENSE/COPYING at the repository root and document asset copyrights in README.
- Source must be public, complete and buildable. Publish a release tag for every version and keep the tag scheme stable; F-Droid builds from tags, not random repository heads.
- The application ID must be unique and preferably based on a domain owned by the developer.

### 2. Dependencies (hard blockers)
- No Play Services, Firebase, Crashlytics, AdMob, GCM or other proprietary libraries. Check the transitive graph, not only direct declarations:
  ./gradlew :app:dependencies --configuration releaseRuntimeClasspath
- Only trusted Maven repositories are accepted by default: Maven Central, Google Maven, OSS Sonatype, OSS JFrog, JitPack.io and Clojars. Anything else requires a source-built srclib/prebuild or prior maintainer approval.
- Analytics, crash reporting and update checks must be opt-in and OFF by default; otherwise expect the Tracking anti-feature or outright rejection.

### 3. Versioning and build
- Keep versionName and versionCode static in the build file. No time-based or computed versions: F-Droid scrapes files and does not execute build logic.
- Commit the Gradle wrapper (properties AND jar), pin toolchains, and avoid build steps that download JDKs or binaries on demand.
- F-Droid runs assembleRelease. ABI splits produce several APKs: either select one output in metadata or define per-ABI build blocks with VercodeOperation.
- Build from a clean tree at the exact tag. AGP 8.3+ embeds VCS info in the APK; disable it only deliberately.

### 4. Metadata
- In an fdroiddata checkout, run `fdroid import --url=<repo>` (the docs also show `fdroid import` after cloning the app repo) to generate a template, then complete License, SourceCode, Repo, RepoType, Summary, Description, Categories, Builds and UpdateCheckMode.
- When version fields are indirect or the tag scheme is unusual, set UpdateCheckData/AutoUpdateMode explicitly instead of relying on RepoManifest.
- `fdroid lint <package>` and `fdroid build -v <package>:<versionCode>` validate the recipe; `fdroid rewritemeta` normalises it.
- Upstream fastlane/Triple-T metadata under fastlane/metadata/<locale>/ can supply name, summary and description.

### 5. Anti-features
- Known labels: Ads, DisabledAlgorithm, KnownVuln, NonFreeAdd, NonFreeAssets, NonFreeDep, NonFreeNet, NoSourceSince, TetheredNet, Tracking.
- Self-hostable alternatives and user-selectable servers avoid TetheredNet/NonFreeNet. Undisclosed anti-features cause rejection; declare them in metadata and in the app description.

## Verification

- Print the runtime dependency graph and search it for play-services, firebase, gms, analytics and ad SDKs.
- Build release APKs from the clean tagged commit and check them with `apksigner verify --print-certs`.
- If upstream-signed APKs should be published, add Binaries/AllowedAPKSigningKeys and check reproducibility with diffoscope (see Reproducible Builds).

## Project notes (HelmChatRisen)

- Already OK: MIT license with LICENSE at the root, public GitHub source, static version values, committed Gradle wrapper, no app-level ads/Firebase/Play Services, self-hostable Jitsi servers.
- Jitsi Meet SDK **13.1.1** is built from source with `LIBRE_BUILD=true` by
  `scripts/build-jitsi-libre-sdk.sh` (pinned `mobile-sdk-13.1.1` commit
  `1a308a6873098c038cf264f3d8f5378bb975601b`). No AAR is committed and no
  remote Jitsi Maven repository is declared: `settings.gradle.kts` puts
  `mavenLocal()` first, ahead of Google Maven and Maven Central.
- The blockers from the old prebuilt AAR are gone. A checked
  `releaseRuntimeClasspath` contains no Play Services, Firebase,
  install-referrer, Amplitude, Giphy or Google Sign-In. The Jitsi-specific
  `org.jitsi:webrtc:124.0.0`, `com.facebook.react:react-android:0.85.2` and
  React Native modules resolve from Maven Central; the `-jitsi-*` React Native
  forks are published into the local Maven repo by Jitsi's own `publish` task
  in the same build. Verify with
  `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --refresh-dependencies --info`.
- Buildserver prerequisites: Node.js 24.22.3 is downloaded with a pinned
  SHA-256 by `scripts/fdroid-build.sh` (base image has no Node 24), JDK 17 for
  Jitsi's Gradle 8.14 build and JDK 21 for the app's Gradle 9.6 / AGP 9.5.2.
  Both are installed via `sudo: ./scripts/fdroid-install-deps.sh`. The npm
  install needs several GiB of disk.
- Candidate recipe: `fdroid/dev.communicator.helmchatrisen.yml` with per-ABI
  codes 701 (`armeabi-v7a`) and 702 (`arm64-v8a`) derived from upstream code 7;
  `CurrentVersionCode: 702`, `UpdateCheckMode: Tags V*`. Description and summary
  come from `fastlane/metadata/android/en-US/` - do NOT duplicate `Summary` or
  `Description` in the `.yml`, F-Droid docs say the upstream fastlane text wins
  and the metadata field would override it.
- Both `commit:` values are still placeholders. Push the `V1.7` tag, then
  replace them with that tag's full commit hash. Validate with
  `fdroid readmeta` / `rewritemeta` / `lint` / `build -v ...:701` and `:702`
  in an `fdroidserver` environment (not installed locally).
- Local release outputs are signed (APK Signature Scheme v2) with a developer
  self-signed certificate from the gitignored repository-root `keystore.jks`,
  configured via `releaseStoreFile` (absolute path) and the other three
  properties in `~/.gradle/gradle.properties`. Official F-Droid builds are
  signed by the repository itself, so this key is not used by the inclusion MR;
  never commit the keystore or passwords.
