package com.spacebrowser

import com.spacebrowser.core.util.UrlUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilTest {

    private val template = "https://search.example/?q=%s"

    // isLikelyUrl ---------------------------------------------------------------

    @Test fun `bare domain is a url`() = assertTrue(UrlUtil.isLikelyUrl("example.com"))

    @Test fun `domain with path and query is a url`() =
        assertTrue(UrlUtil.isLikelyUrl("sub.domain.co.uk/path?q=1"))

    @Test fun `explicit scheme is a url`() =
        assertTrue(UrlUtil.isLikelyUrl("https://kotlinlang.org/docs"))

    @Test fun `non-http scheme is a url`() = assertTrue(UrlUtil.isLikelyUrl("mailto:me@example.com"))

    @Test fun `localhost with port is a url`() = assertTrue(UrlUtil.isLikelyUrl("localhost:8080"))

    @Test fun `ipv4 with path is a url`() = assertTrue(UrlUtil.isLikelyUrl("192.168.0.1/admin"))

    @Test fun `plain words are a search`() = assertFalse(UrlUtil.isLikelyUrl("what is kotlin"))

    @Test fun `single word without dot is a search`() = assertFalse(UrlUtil.isLikelyUrl("example"))

    @Test fun `inner whitespace forces search`() = assertFalse(UrlUtil.isLikelyUrl("example .com"))

    @Test fun `empty input is not a url`() = assertFalse(UrlUtil.isLikelyUrl("   "))

    // toLoadable ----------------------------------------------------------------

    @Test fun `schemeless url gets https`() =
        assertEquals("https://example.com", UrlUtil.toLoadable("example.com", template))

    @Test fun `existing scheme is preserved`() =
        assertEquals("http://example.com", UrlUtil.toLoadable("http://example.com", template))

    @Test fun `search terms are url-encoded into the template`() =
        assertEquals(
            "https://search.example/?q=hello+world",
            UrlUtil.toLoadable("hello world", template),
        )

    @Test fun `special characters are escaped in searches`() =
        assertEquals(
            "https://search.example/?q=a%26b%3Dc",
            UrlUtil.toLoadable("a&b=c", template),
        )

    @Test fun `input is trimmed before deciding`() =
        assertEquals("https://example.com", UrlUtil.toLoadable("  example.com  ", template))

    // prettyHost ----------------------------------------------------------------

    @Test fun `pretty host strips scheme www path query and fragment`() =
        assertEquals("example.com", UrlUtil.prettyHost("https://www.example.com/a/b?x=1#frag"))

    @Test fun `pretty host of null is empty`() = assertEquals("", UrlUtil.prettyHost(null))

    @Test fun `pretty host works without scheme`() =
        assertEquals("example.com", UrlUtil.prettyHost("example.com/foo"))

    // hostOf --------------------------------------------------------------------

    @Test fun `hostOf lowercases and strips port but keeps www`() =
        assertEquals("www.example.com", UrlUtil.hostOf("https://WWW.Example.COM:8443/p"))

    @Test fun `hostOf requires a scheme`() = assertNull(UrlUtil.hostOf("example.com"))

    @Test fun `hostOf of null is null`() = assertNull(UrlUtil.hostOf(null))
}
