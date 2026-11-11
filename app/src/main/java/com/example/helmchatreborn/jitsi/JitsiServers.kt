package com.example.helmchatreborn.jitsi

import androidx.annotation.StringRes
import com.example.helmchatreborn.R

/**
 * A public Jitsi Meet server offered in the server picker on the home screen.
 *
 * @property url Base URL passed to the Jitsi SDK.
 * @property descriptionRes Short, translated hint about the server's strengths.
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

    val presets: List<JitsiServer> = listOf(
        JitsiServer(
            url = "https://freejitsi01.netcup.net",
            descriptionRes = R.string.server_netcup_description,
        ),
        JitsiServer(
            url = "https://meet.ffmuc.net",
            descriptionRes = R.string.server_ffmuc_description,
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
    )

    val default: JitsiServer = presets.first {
        it.url.equals(JitsiCallManager.DEFAULT_SERVER_URL, ignoreCase = true)
    }

    /** Finds the preset matching [url], ignoring case, a missing scheme and trailing slashes. */
    fun byUrl(url: String): JitsiServer? {
        val candidate = url.trim().trimEnd('/')
        return presets.firstOrNull { preset ->
            preset.url.equals(candidate, ignoreCase = true) ||
                "https://${preset.host}".equals(candidate, ignoreCase = true)
        }
    }
}
