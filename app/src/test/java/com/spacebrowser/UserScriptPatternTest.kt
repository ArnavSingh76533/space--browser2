package com.spacebrowser

import com.spacebrowser.core.extensions.UserScriptManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptPatternTest {

    @Test
    fun `all urls is limited to web pages`() {
        assertTrue(UserScriptManager.matches("<all_urls>", "https://example.com/"))
        assertFalse(UserScriptManager.matches("<all_urls>", "file:///sdcard/private.txt"))
        assertFalse(UserScriptManager.matches("<all_urls>", "content://settings"))
    }

    @Test
    fun `wildcard subdomain also matches the base host`() {
        val pattern = "*://*.example.com/*"
        assertTrue(UserScriptManager.matches(pattern, "https://example.com/page"))
        assertTrue(UserScriptManager.matches(pattern, "http://www.example.com/page?q=1"))
        assertFalse(UserScriptManager.matches(pattern, "https://notexample.com/page"))
    }

    @Test
    fun `scheme host and path constraints are enforced`() {
        val pattern = "https://docs.example.com/help/*"
        assertTrue(UserScriptManager.matches(pattern, "https://docs.example.com/help/start"))
        assertFalse(UserScriptManager.matches(pattern, "http://docs.example.com/help/start"))
        assertFalse(UserScriptManager.matches(pattern, "https://docs.example.com/account"))
        assertFalse(UserScriptManager.validPattern("javascript://example.com/*"))
    }
}
