package com.spacebrowser

import com.spacebrowser.core.browser.CookieTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieTransferTest {

    @Test
    fun `cookie header round trips through scoped SPACE export`() {
        val exported = CookieTransfer.export(
            "https://example.com",
            "session=abc=123; theme=dark",
        )
        val imported = CookieTransfer.import(exported)
        assertEquals(2, imported.size)
        assertEquals("abc=123", imported.first { it.name == "session" }.value)
        assertTrue(imported.all { it.secure })
        assertFalse(exported.contains("Domain", ignoreCase = true))
    }

    @Test
    fun `cookie editor and Netscape formats are accepted`() {
        val editor = CookieTransfer.import(
            """[{"domain":".other.example","name":"token","value":"one","path":"/"}]""",
        )
        assertEquals("token=one; Path=/; SameSite=Lax", editor.single().setCookieHeader(false))

        val netscape = CookieTransfer.import(
            "#HttpOnly_.example.com\tTRUE\t/\tTRUE\t0\tsession\ttwo",
        )
        assertEquals("session", netscape.single().name)
        assertTrue(netscape.single().httpOnly)
        assertTrue(netscape.single().secure)
    }

    @Test
    fun `invalid cookie names are discarded`() {
        val imported = CookieTransfer.import(
            """[{"name":"valid","value":"1"},{"name":"bad name","value":"2"}]""",
        )
        assertEquals(listOf("valid"), imported.map { it.name })
    }
}
