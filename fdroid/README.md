# F-Droid recipe (proposal)

[`dev.communicator.helmchatrisen.yml`](dev.communicator.helmchatrisen.yml) is a
**proposed** `fdroiddata` metadata file for HelmChatRisen 1.7 (tag `V1.7`). It is kept in this
repository so the build can be reviewed together with the source; it is not a
substitute for the authoritative metadata in `fdroiddata/metadata/`.

## What the recipe does

1. `sudo` runs [`scripts/fdroid-install-deps.sh`](../scripts/fdroid-install-deps.sh)
   to install build tools plus JDK 17 (Jitsi's Gradle toolchain) and JDK 21
   (the app's Gradle 9.6 / AGP 9.5.2 build).
2. `prebuild` rewrites `app/build.gradle.kts` so the version code comes from
   F-Droid's `$$VERCODE$$`. This keeps the APK version code in sync with the
   metadata's per-ABI `VercodeOperation` values (701 for `armeabi-v7a`, 702 for
   `arm64-v8a`, derived from upstream code 7) without changing the public source.
3. `build` runs [`scripts/fdroid-build.sh`](../scripts/fdroid-build.sh), which:
   - downloads Node.js 24.22.3 from nodejs.org and verifies its SHA-256
     (the F-Droid base image does not provide Node 24),
   - calls `scripts/build-jitsi-libre-sdk.sh` to build and publish Jitsi Meet
     SDK 13.1.1 from the pinned `mobile-sdk-13.1.1` commit with
     `LIBRE_BUILD=true`,
   - assembles the release APKs against that local SDK.
4. `output` selects the ABI-specific APK from the three produced by
   `assembleRelease`.

No prebuilt Jitsi AAR is fetched or committed. The remote Jitsi Maven
repository is absent from `settings.gradle.kts`; `mavenLocal()` is first in the
repository list.

## Before opening the fdroiddata merge request

- [ ] Merge `V1.7-rc` into `master`, set the annotated `V1.7` tag on the final
      `master` commit and push both. Both `commit:` fields currently pin the
      provisional candidate `03572db` and **must be updated** to that final
      hash before submitting — the `AppInfoDialog` metadata change landed after
      the candidate commit.
- [ ] Review the upstream short/full descriptions already present under
      `fastlane/metadata/android/en-US/` (they supply F-Droid's Summary and
      Description, so do not duplicate those fields in the `.yml`), add
      screenshots if desired, and verify changelog filenames match the final
      version codes (`7.txt`, `701.txt`, `702.txt` are present).
- [ ] Confirm the F-Droid runner has enough disk: Jitsi's `node_modules` is
      about 6 GiB and the toolchain adds roughly 0.5 GiB.
- [ ] Validate the metadata in an `fdroidserver` checkout:

      ```bash
      fdroid readmeta dev.communicator.helmchatrisen
      fdroid rewritemeta dev.communicator.helmchatrisen
      fdroid lint dev.communicator.helmchatrisen
      fdroid build -v dev.communicator.helmchatrisen:701
      fdroid build -v dev.communicator.helmchatrisen:702
      ```

- [ ] Re-run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
      on the tagged commit and confirm no Play Services, Firebase, GMS,
      install-referrer, Amplitude, Giphy or Google Sign-In dependency appears.
- [ ] Document the source-built SDK prerequisite in the merge request and in
      `MaintainerNotes`; expect reviewers to ask why Node/JDK are bootstrapped.

## Known risks to raise with maintainers

- The Node tarball is a pinned, checksum-verified build-tool download. If the
  build server blocks downloads outside the trusted Maven lists, replace it
  with an F-Droid-provided Node 24 `srclib`.
- `npm ci` fetches ~2100 packages from the npm registry using Jitsi's committed
  `package-lock.json`. The Jitsi git commit is pinned in
  `scripts/build-jitsi-libre-sdk.sh`, but the npm registry itself is not
  mirrored by F-Droid.
- `assembleRelease` builds all three APKs for each block and then selects one;
  this costs build time but avoids patching the ABI configuration.
- Physical call/intercom verification with SDK 13.1.1 has not been done (no
  device was available); this does not block a source build but should be
  disclosed.
