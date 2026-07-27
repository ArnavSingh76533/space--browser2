package com.spacebrowser

import com.spacebrowser.core.adblock.HostMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMatcherTest {

    private val matcher = HostMatcher(listOf("doubleclick.net", "tracker.io"))

    @Test fun `exact host matches`() = assertTrue(matcher.matches("doubleclick.net"))

    @Test fun `subdomains match`() {
        assertTrue(matcher.matches("ads.doubleclick.net"))
        assertTrue(matcher.matches("a.b.c.tracker.io"))
    }

    @Test fun `lookalike hosts do not match`() {
        // "xdoubleclick.net" contains the rule as a substring but is a different host.
        assertFalse(matcher.matches("xdoubleclick.net"))
        assertFalse(matcher.matches("nottracker.io.example.com"))
    }

    @Test fun `matching is case-insensitive`() =
        assertTrue(matcher.matches("ADS.DoubleClick.NET"))

    @Test fun `unrelated hosts do not match`() = assertFalse(matcher.matches("example.com"))

    @Test fun `null host does not match`() = assertFalse(matcher.matches(null))

    @Test fun `wildcard and dot prefixes are normalized`() {
        val m = HostMatcher(listOf("*.ads.example", ".pixel.example"))
        assertTrue(m.matches("ads.example"))
        assertTrue(m.matches("x.ads.example"))
        assertTrue(m.matches("pixel.example"))
        assertTrue(m.matches("a.pixel.example"))
    }

    @Test fun `comments and blanks are ignored`() {
        val m = HostMatcher(listOf("# a comment", "", "  ", "real.example"))
        assertEquals(1, m.size)
        assertTrue(m.matches("real.example"))
    }
}
