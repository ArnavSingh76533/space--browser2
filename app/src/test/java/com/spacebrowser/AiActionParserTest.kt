package com.spacebrowser

import com.spacebrowser.core.browser.MediaCommand
import com.spacebrowser.core.browser.WebStep
import com.spacebrowser.ui.ai.AiAction
import com.spacebrowser.ui.ai.parseAiAction
import com.spacebrowser.ui.ai.parseLocalBrowserCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionParserTest {

    @Test
    fun `play media is a first-class action`() {
        assertEquals(
            AiAction.PlayMedia("Titanium"),
            parseAiAction("""{"action":"PLAY_MEDIA","query":"Titanium"}"""),
        )
    }

    @Test
    fun `spoken seek target can be represented in seconds`() {
        val action = parseAiAction(
            """{"action":"MEDIA_CONTROL","command":"SEEK_TO","seconds":140}""",
        )
        assertTrue(action is AiAction.MediaControl)
        assertEquals(140.0, (action as AiAction.MediaControl).command.let {
            (it as MediaCommand.SeekTo).seconds
        }, 0.0)
    }

    @Test
    fun `safe multi-step web task is parsed in order`() {
        val action = parseAiAction(
            """
            {"action":"WEB_SEQUENCE","steps":[
              {"type":"OPEN_URL","url":"https://example.com"},
              {"type":"FILL_FIELD","field":"Search","value":"space"},
              {"type":"CLICK_TEXT","text":"Search"}
            ]}
            """.trimIndent(),
        ) as AiAction.WebActions
        assertTrue(action.steps[0] is WebStep.OpenUrl)
        assertTrue(action.steps[1] is WebStep.FillField)
        assertTrue(action.steps[2] is WebStep.ClickText)
    }

    @Test
    fun `common media phrases bypass model ambiguity`() {
        assertEquals(
            AiAction.PlayMedia("Titanium"),
            parseLocalBrowserCommand("Play Titanium"),
        )
        val seek = parseLocalBrowserCommand("Go to 2 minutes 20 seconds")
            as AiAction.MediaControl
        assertEquals(140.0, (seek.command as MediaCommand.SeekTo).seconds, 0.0)
    }
}
