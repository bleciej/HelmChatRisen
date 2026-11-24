package dev.communicator.helmchatrisen.ui.home

import dev.communicator.helmchatrisen.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the internal consistency of the build metadata shown in [AppInfoDialog].
 *
 * The tests deliberately avoid pinning release numbers or SDK levels so ordinary version bumps do
 * not require editing tests; only relationships between the generated fields are asserted.
 */
class AppInfoMetadataTest {

    /** The tag must follow the version name so a bump cannot leave a stale tag. */
    @Test
    fun releaseTag_tracksVersionName() {
        assertEquals("V${BuildConfig.VERSION_NAME}", BuildConfig.RELEASE_TAG)
    }

    /** The release date must stay machine-readable for the in-app dialog. */
    @Test
    fun releaseDate_isIsoDate() {
        assertTrue(
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(BuildConfig.RELEASE_DATE),
        )
    }

    /** The minimum-Android label must be derived from the same minSdk used by Gradle. */
    @Test
    fun minAndroid_matchesMinSdk() {
        assertTrue(
            BuildConfig.MIN_ANDROID.contains("(API ${BuildConfig.MIN_SDK})"),
        )
    }

    /** The SDK summary must follow the target/compile rule used by the build script. */
    @Test
    fun sdkLevels_matchTargetAndCompile() {
        val compileSdk = if (BuildConfig.COMPILE_SDK_MINOR == 0) {
            BuildConfig.COMPILE_SDK.toString()
        } else {
            "${BuildConfig.COMPILE_SDK}.${BuildConfig.COMPILE_SDK_MINOR}"
        }
        val expected = if (
            BuildConfig.TARGET_SDK == BuildConfig.COMPILE_SDK &&
            BuildConfig.COMPILE_SDK_MINOR == 0
        ) {
            BuildConfig.TARGET_SDK.toString()
        } else {
            "${BuildConfig.TARGET_SDK} / $compileSdk"
        }

        assertEquals(expected, BuildConfig.SDK_LEVELS)
    }

    /** Version fields must carry usable values even though their exact numbers may change. */
    @Test
    fun versionMetadata_isPresent() {
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE > 0)
    }

    /** The Jitsi SDK line must expose a semantic version from the version catalog. */
    @Test
    fun jitsiSdkVersion_isPresent() {
        assertTrue(
            Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(BuildConfig.JITSI_SDK_VERSION),
        )
    }
}
