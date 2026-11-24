package dev.communicator.helmchatrisen.jitsi

import androidx.annotation.StringRes
import dev.communicator.helmchatrisen.R

/**
 * A public Jitsi Meet server offered in the server picker on the home screen.
 *
 * @param url base URL passed to the Jitsi SDK.
 * @param descriptionRes short, translated hint about the server's strengths.
 */
data class JitsiServer(
    val url: String,
    @StringRes val descriptionRes: Int,
) {
    /** Host shown in the picker, e.g. `meet.ffmuc.net`. */
    val host: String
        get() = url
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
}

/**
 * Curated list of free public Jitsi servers. All of them allow anonymous, audio-only
 * participants, which is what HelmChat needs. [default] stays in sync with
 * [JitsiCallManager.DEFAULT_SERVER_URL] and is used when the rider has not picked another one.
 */
object JitsiServers {

    /** The selectable servers, in the order shown in the picker. */
    val presets: List<JitsiServer> = listOf(
        JitsiServer(
            url = "https://meet.ffmuc.net",
            descriptionRes = R.string.server_ffmuc_description,
        ),
        JitsiServer(
            url = "https://freejitsi01.netcup.net",
            descriptionRes = R.string.server_netcup_description,
        ),
        JitsiServer(
            url = "https://webex.muc.ccc.de",
            descriptionRes = R.string.server_ccc_description,
        ),
        JitsiServer(
            url = "https://fairmeeting.net",
            descriptionRes = R.string.server_fairmeeting_description,
        ),
        JitsiServer(
            url = "https://meet.systemli.org",
            descriptionRes = R.string.server_systemli_description,
        ),
        // Hidden until the meet.jit.si moderator sign-in flow is fixed; restore in the next release.
        // JitsiServer(
        //     url = "https://meet.jit.si",
        //     descriptionRes = R.string.server_jitsi_description,
        // ),
    )

    /** The preset matching [JitsiCallManager.DEFAULT_SERVER_URL], used when the rider picks none. */
    val default: JitsiServer = presets.first {
        it.url.equals(JitsiCallManager.DEFAULT_SERVER_URL, ignoreCase = true)
    }

    /**
     * Finds the preset matching [url], ignoring case, a missing scheme and trailing slashes.
     *
     * `http://` URLs never match: presets are advertised over `https` only. The join path
     * upgrades a known host to its canonical `https` URL instead, see
     * [JitsiCallManager.sanitizeServerUrl].
     *
     * @param url the server URL to match, as typed by the rider or loaded from a recent room.
     * @return the matching preset, or `null` when the URL is not a known preset.
     */
    fun byUrl(url: String): JitsiServer? {
        val candidate = normalizeServerUrl(url) ?: return null
        return presets.firstOrNull { preset ->
            preset.url.equals(candidate, ignoreCase = true)
        }
    }
}
