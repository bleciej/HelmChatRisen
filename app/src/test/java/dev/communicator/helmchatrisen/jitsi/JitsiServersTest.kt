package dev.communicator.helmchatrisen.jitsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JitsiServersTest {

    @Test
    fun byUrl_matchesFullHttpsUrl() {
        val server = JitsiServers.byUrl("https://meet.ffmuc.net")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    @Test
    fun byUrl_matchesBareHost() {
        val server = JitsiServers.byUrl("meet.ffmuc.net")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    @Test
    fun byUrl_ignoresTrailingSlash() {
        val server = JitsiServers.byUrl("https://meet.ffmuc.net/")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    @Test
    fun byUrl_isCaseInsensitive() {
        val server = JitsiServers.byUrl("HTTPS://MEET.FFMUC.NET")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    /** Verifies URL matching trims surrounding whitespace. */
    @Test
    fun byUrl_trimsWhitespace() {
        val server = JitsiServers.byUrl("  https://freejitsi01.netcup.net  ")
        assertEquals("https://freejitsi01.netcup.net", server?.url)
    }

    @Test
    fun byUrl_returnsNullForEmptyInput() {
        assertNull(JitsiServers.byUrl(""))
        assertNull(JitsiServers.byUrl("   "))
    }

    @Test
    fun byUrl_returnsNullForUnknownServer() {
        assertNull(JitsiServers.byUrl("https://example.com"))
    }

    /** Verifies that the removed Fairmeeting URL is no longer resolved as a preset. */
    @Test
    fun byUrl_returnsNullForRemovedFairmeetingPreset() {
        assertNull(JitsiServers.byUrl("https://fairmeeting.net"))
        assertNull(JitsiServers.byUrl("fairmeeting.net"))
    }

    @Test
    fun byUrl_matchesBareHostWithTrailingSlash() {
        val server = JitsiServers.byUrl("meet.ffmuc.net/")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    @Test
    fun byUrl_trimsWhitespaceAndTrailingSlash() {
        val server = JitsiServers.byUrl("  meet.ffmuc.net/  ")
        assertEquals("https://meet.ffmuc.net", server?.url)
    }

    @Test
    fun byUrl_returnsNullForUrlWithPath() {
        assertNull(JitsiServers.byUrl("https://meet.ffmuc.net/SomeRoom/"))
    }

    @Test
    fun byUrl_returnsNullForSchemeOnlyInput() {
        assertNull(JitsiServers.byUrl("https://"))
        assertNull(JitsiServers.byUrl("http://"))
        assertNull(JitsiServers.byUrl("https:///"))
    }

    @Test
    fun normalizeServerUrl_returnsNullForSchemeOnlyInput() {
        assertNull(normalizeServerUrl("https://"))
        assertNull(normalizeServerUrl("http://"))
        assertNull(normalizeServerUrl("https:///"))
    }

    @Test
    fun normalizeServerUrl_stripsWhitespaceAndTrailingSlash() {
        assertEquals("https://meet.ffmuc.net", normalizeServerUrl("  https://meet.ffmuc.net/  "))
    }

    @Test
    fun byUrl_doesNotMatchHttpUrlToHttpsPreset() {
        assertNull(JitsiServers.byUrl("http://meet.ffmuc.net"))
    }

    /** Verifies that plain HTTP never resolves to an HTTPS preset. */
    @Test
    fun byUrl_doesNotMatchHttpUrlOfOtherPreset() {
        assertNull(JitsiServers.byUrl("http://meet.systemli.org"))
        assertNull(JitsiServers.byUrl("http://freejitsi01.netcup.net/"))
    }
}
