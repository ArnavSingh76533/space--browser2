package com.spacebrowser

import com.spacebrowser.core.adblock.AdResourceType
import com.spacebrowser.core.adblock.FilterDecision
import com.spacebrowser.core.adblock.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    @Test
    fun `domain anchored third-party rule blocks matching requests`() {
        val engine = FilterEngine(listOf("||tracker.example^\$third-party"))
        assertEquals(
            FilterDecision.BLOCK,
            engine.decide(
                "https://cdn.tracker.example/pixel.js",
                "https://news.example/article",
                AdResourceType.SCRIPT,
            ),
        )
        assertEquals(
            FilterDecision.NONE,
            engine.decide(
                "https://cdn.tracker.example/app.js",
                "https://www.tracker.example/",
                AdResourceType.SCRIPT,
            ),
        )
    }

    @Test
    fun `exception wins over a normal blocking rule`() {
        val engine = FilterEngine(
            listOf(
                "||ads.example^",
                "@@||ads.example/allowed.js\$script",
            ),
        )
        assertEquals(
            FilterDecision.ALLOW,
            engine.decide(
                "https://ads.example/allowed.js",
                "https://site.example/",
                AdResourceType.SCRIPT,
            ),
        )
    }

    @Test
    fun `important block wins over exception`() {
        val engine = FilterEngine(
            listOf(
                "||ads.example^\$important",
                "@@||ads.example^",
            ),
        )
        assertEquals(
            FilterDecision.IMPORTANT_BLOCK,
            engine.decide(
                "https://ads.example/banner.png",
                "https://site.example/",
                AdResourceType.IMAGE,
            ),
        )
    }

    @Test
    fun `resource type constraints are applied`() {
        val engine = FilterEngine(listOf("||cdn.example^\$image,~script"))
        assertEquals(
            FilterDecision.BLOCK,
            engine.decide(
                "https://cdn.example/ad.png",
                "https://site.example/",
                AdResourceType.IMAGE,
            ),
        )
        assertEquals(
            FilterDecision.NONE,
            engine.decide(
                "https://cdn.example/app.js",
                "https://site.example/",
                AdResourceType.SCRIPT,
            ),
        )
    }

    @Test
    fun `cosmetic rules honor domain exceptions`() {
        val engine = FilterEngine(
            listOf(
                "##.advertisement",
                "docs.example#@#.advertisement",
                "news.example##.sponsored",
            ),
        )
        val docs = engine.cosmeticFor("docs.example")
        assertTrue(".advertisement" in docs.hide)
        assertTrue(".advertisement" in docs.exceptions)
        val news = engine.cosmeticFor("news.example")
        assertTrue(".sponsored" in news.hide)
        assertFalse(".sponsored" in news.exceptions)
    }
}
