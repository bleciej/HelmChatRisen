# F-Droid recipe (proposal)

[`dev.communicator.helmchatrisen.yml`](dev.communicator.helmchatrisen.yml) is a
**proposed** `fdroiddata` metadata file for HelmChatRisen 1.7 (tag `V1.7`). It is kept in this
repository so the build can be reviewed together with the source; it is not a
substitute for the authoritative metadata in `fdroiddata/metadata/`.

Only the `arm64-v8a` APK is submitted to F-Droid. Upstream still builds 32-bit and
universal APKs, but the F-Droid recipe has a single build block with version code
`700`; the 32-bit code `701` is deliberately not published.

`scripts/fdroid-build-local.sh` refuses to run unless the local `V1.7` tag and
both the candidate recipe and the `fdroiddata` copy pin commit
`62b57523121fe33ea79c4598431c7b276bafe611`.

## What the recipe does

1. `sudo` runs [`scripts/fdroid-install-deps.sh`](../scripts/fdroid-install-deps.sh)
   to install build tools, JDK 17 (Jitsi's Gradle toolchain), JDK 21 (the app's
   Gradle 9.6 / AGP 9.5.2 build) and a checksum-pinned Gradle 9.6 distribution.
   The script links that distribution to `/usr/local/bin/gradle`.
2. `prebuild` rewrites `app/build.gradle.kts` so the version code comes from
   F-Droid's `$$VERCODE$$`. This keeps the APK version code in sync with the
   metadata's `VercodeOperation` value (700 for `arm64-v8a`, derived from upstream
   code 7) without changing the public source. It also replaces `./gradlew` with
   `gradle`, because F-Droid removes the wrapper and the pinned Gradle is on
   `PATH` after the `sudo` step.
3. `build` runs [`scripts/fdroid-build.sh`](../scripts/fdroid-build.sh), which:
   - downloads Node.js 24.22.3 from nodejs.org and verifies its SHA-256
     (the F-Droid base image does not provide Node 24),
   - calls `scripts/build-jitsi-libre-sdk.sh` to build and publish Jitsi Meet
     SDK 13.1.1 from the pinned `mobile-sdk-13.1.1` commit with
     `LIBRE_BUILD=true`,
   - assembles the release APKs against that local SDK.
4. `output` selects the `arm64-v8a` APK from the release outputs of
   `assembleRelease`.

No prebuilt Jitsi AAR is fetched or committed. The remote Jitsi Maven
repository is absent from `settings.gradle.kts`; `mavenLocal()` is first in the
repository list.

## Before opening the fdroiddata merge request

- [x] Merge `V1.7-rc` into `master` and move the local annotated `V1.7` tag to
      the final `master` commit `62b57523121fe33ea79c4598431c7b276bafe611`.
      The `commit:` field pins that hash; the earlier pins `03572db` and
      `3e2f332` are obsolete.
- [x] Force-push the local `V1.7` tag to `github`. Verified `2026-11-27`:
      `refs/tags/V1.7` = `62b57523121fe33ea79c4598431c7b276bafe611`. F-Droid
      builds from GitHub, whose tag matches the pinned commit, so the MR is not
      blocked by the tag.
- [x] Review the upstream short/full descriptions under
      `fastlane/metadata/android/en-US/` (they supply F-Droid's Summary and
      Description, so do not duplicate those fields in the `.yml`). Changelogs
      `7.txt` and `700.txt` are present; `701.txt` and `702.txt` were removed
      with the unpublished 32-bit/702 packages. Screenshots remain optional.
- [x] Confirm the F-Droid runner has enough disk: Jitsi's `node_modules` is
      about 6 GiB and the toolchain adds roughly 0.5 GiB. The local build
      succeeded with 22 GiB free.
- [x] Re-validate the metadata in an `fdroidserver` checkout after adding the
      pinned Gradle install:

      ```bash
      fdroid readmeta -W ignore
      fdroid rewritemeta dev.communicator.helmchatrisen
      fdroid lint -f dev.communicator.helmchatrisen
      fdroid build -v dev.communicator.helmchatrisen:700
      ```

      Completed `2026-11-27` with fdroidserver 2.2.1. Plain `fdroid readmeta`
      still fails on unrelated pre-existing `ac.ella.peerpop` metadata, hence
      `-W ignore`. The full build succeeded from `62b575…` and produced the
      arm64-v8a APK.
- [x] Re-run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
      on the tagged commit and confirm no Play Services, Firebase, GMS,
      install-referrer, Amplitude, Giphy or Google Sign-In dependency appears.
- [x] Document the source-built SDK prerequisite in the merge request and in
      `MaintainerNotes`; expect reviewers to ask why Node/JDK/Gradle are
      bootstrapped.

## Known risks to raise with maintainers

- The Node and Gradle archives are pinned, checksum-verified build-tool
  downloads. If the build server blocks downloads outside the trusted Maven
  lists, replace them with F-Droid-provided Node 24/Gradle 9.6 `srclib`s or an
  approved toolchain image.
- `npm ci` fetches ~2100 packages from the npm registry using Jitsi's committed
  `package-lock.json`. The Jitsi git commit is pinned in
  `scripts/build-jitsi-libre-sdk.sh`, but the npm registry itself is not
  mirrored by F-Droid.
- `assembleRelease` builds 32-bit, 64-bit and universal APKs before `output`
  selects the 64-bit one. This keeps the tagged source unchanged but repeats
  work; a later recipe could patch the ABI split and the Jitsi SDK's own ABI
  filters to build only `arm64-v8a`.
- Physical call/intercom verification with SDK 13.1.1 has not been done (no
  device was available); this does not block a source build but should be
  disclosed.
