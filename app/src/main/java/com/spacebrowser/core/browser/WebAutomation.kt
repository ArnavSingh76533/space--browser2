package com.spacebrowser.core.browser

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Media operations shared by SPACE AI and the background playback service. */
sealed class MediaCommand(val label: String) {
    data object Play : MediaCommand("Play")
    data object Pause : MediaCommand("Pause")
    data object Next : MediaCommand("Next")
    data object Previous : MediaCommand("Previous")
    data object Mute : MediaCommand("Mute")
    data object Unmute : MediaCommand("Unmute")
    data class SeekTo(val seconds: Double) : MediaCommand("Seek to ${seconds.toInt()} seconds")
    data class SeekBy(val seconds: Double) : MediaCommand("Seek by ${seconds.toInt()} seconds")
    data class SetVolume(val percent: Int) :
        MediaCommand("Set volume to ${percent.coerceIn(0, 100)}%")
}

/** Fixed, allowlisted webpage operations. The model never supplies executable JavaScript. */
sealed class WebStep(val label: String) {
    data class OpenUrl(val url: String) : WebStep("Open $url")
    data class ClickText(val text: String) : WebStep("Click “$text”")
    data class FillField(val field: String, val value: String) : WebStep("Fill “$field”")
    data class FindText(val text: String) : WebStep("Find “$text”")
    data class Scroll(val direction: String) : WebStep("Scroll ${direction.lowercase()}")
    data class Wait(val millis: Long) : WebStep("Wait ${millis.coerceAtLeast(0)} ms")
}

object WebAutomation {

    private const val MEDIA_ELEMENT =
        "document.querySelector('video:not([aria-hidden=\"true\"]),audio:not([aria-hidden=\"true\"])')"

    suspend fun media(webView: WebView, command: MediaCommand): Result<String> {
        val operation = when (command) {
            MediaCommand.Play -> "m.play();"
            MediaCommand.Pause -> "m.pause();"
            MediaCommand.Mute -> "m.muted=true;"
            MediaCommand.Unmute -> "m.muted=false;"
            is MediaCommand.SeekTo -> "m.currentTime=${command.seconds.coerceAtLeast(0.0)};"
            is MediaCommand.SeekBy ->
                "m.currentTime=Math.max(0,m.currentTime+${command.seconds});"
            is MediaCommand.SetVolume ->
                "m.volume=${command.percent.coerceIn(0, 100) / 100.0};m.muted=false;"
            MediaCommand.Next -> """
                const n=document.querySelector('.ytp-next-button,[aria-label^="Next"],[data-testid="next"]');
                if(n){n.click();}else if(Number.isFinite(m.duration)){m.currentTime=m.duration;}
            """.trimIndent()
            MediaCommand.Previous -> """
                const p=document.querySelector('.ytp-prev-button,[aria-label^="Previous"],[data-testid="previous"]');
                if(p){p.click();}else{m.currentTime=0;}
            """.trimIndent()
        }
        return evaluate(
            webView,
            """
            (function(){
              try {
                const m=$MEDIA_ELEMENT;
                if(!m) return JSON.stringify({ok:false,message:"No playable media on this page"});
                $operation
                return JSON.stringify({
                  ok:true,
                  paused:m.paused,
                  currentTime:Number.isFinite(m.currentTime)?m.currentTime:0,
                  duration:Number.isFinite(m.duration)?m.duration:0,
                  volume:m.volume,
                  muted:m.muted
                });
              } catch(e) {
                return JSON.stringify({ok:false,message:String(e)});
              }
            })()
            """.trimIndent(),
        )
    }

    suspend fun playbackState(webView: WebView): Result<String> = evaluate(
        webView,
        """
        (function(){
          const m=$MEDIA_ELEMENT;
          return JSON.stringify({
            found:!!m,
            playing:!!m && !m.paused && !m.ended,
            title:document.title||"",
            currentTime:m&&Number.isFinite(m.currentTime)?m.currentTime:0
          });
        })()
        """.trimIndent(),
    )

    suspend fun playFirstYouTubeResult(webView: WebView): Result<String> = evaluate(
        webView,
        """
        (function(){
          const selectors=[
            'ytd-video-renderer a#video-title',
            'ytd-video-renderer a#thumbnail',
            'ytm-video-with-context-renderer a',
            'a[href^="/watch?v="]'
          ];
          for(const selector of selectors){
            const item=document.querySelector(selector);
            if(item){
              item.click();
              return JSON.stringify({ok:true,message:"Opening the first video"});
            }
          }
          return JSON.stringify({ok:false,message:"No video result was found"});
        })()
        """.trimIndent(),
    )

    suspend fun execute(webView: WebView, step: WebStep): Result<String> {
        val result = when (step) {
            is WebStep.OpenUrl -> Result.failure(
                IllegalArgumentException("OpenUrl is handled by TabManager"),
            )
            is WebStep.Wait -> Result.success("Wait")
            is WebStep.ClickText -> clickText(webView, step.text)
            is WebStep.FillField -> fillField(webView, step.field, step.value)
            is WebStep.FindText -> findText(webView, step.text)
            is WebStep.Scroll -> scroll(webView, step.direction)
        }
        return result.mapCatching { raw ->
            val response = JSONObject(raw)
            if (!response.optBoolean("ok")) {
                error(response.optString("message").ifBlank { "The page action could not be completed" })
            }
            response.optString("message").ifBlank { raw }
        }
    }

    private suspend fun clickText(webView: WebView, target: String): Result<String> {
        val quoted = JSONObject.quote(target.take(160))
        return evaluate(
            webView,
            """
            (function(){
              const target=$quoted.trim().toLowerCase();
              if(!target) return JSON.stringify({ok:false,message:"Missing click target"});
              const nodes=[...document.querySelectorAll(
                'button,a,[role="button"],input[type="button"],input[type="submit"],summary'
              )];
              const visible=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);
              const text=e=>((e.innerText||e.value||e.getAttribute('aria-label')||'')
                .replace(/\s+/g,' ').trim().toLowerCase());
              const exact=nodes.find(e=>visible(e)&&text(e)===target);
              const match=exact||nodes.find(e=>visible(e)&&text(e).includes(target));
              if(!match) return JSON.stringify({ok:false,message:"No matching button or link"});
              match.scrollIntoView({block:'center',behavior:'smooth'});
              match.click();
              return JSON.stringify({ok:true,message:"Clicked",text:text(match)});
            })()
            """.trimIndent(),
        )
    }

    private suspend fun fillField(webView: WebView, field: String, value: String): Result<String> {
        val fieldQuoted = JSONObject.quote(field.take(120))
        val valueQuoted = JSONObject.quote(value.take(2000))
        return evaluate(
            webView,
            """
            (function(){
              const wanted=$fieldQuoted.trim().toLowerCase();
              const value=$valueQuoted;
              const sensitive=/(password|passcode|credit|card|cvv|cvc|otp|one.?time|ssn|social security)/i;
              if(!wanted||sensitive.test(wanted))
                return JSON.stringify({ok:false,message:"Sensitive fields are not filled by AI"});
              const fields=[...document.querySelectorAll('input,textarea,[contenteditable="true"]')];
              const labelFor=e=>{
                const explicit=e.id&&document.querySelector('label[for="'+CSS.escape(e.id)+'"]');
                const wrapping=e.closest('label');
                return [
                  explicit&&explicit.innerText,
                  wrapping&&wrapping.innerText,
                  e.getAttribute('aria-label'),
                  e.getAttribute('placeholder'),
                  e.getAttribute('name')
                ].filter(Boolean).join(' ').toLowerCase();
              };
              const allowed=e=>{
                const type=(e.getAttribute('type')||'text').toLowerCase();
                return !['password','hidden','file','checkbox','radio','submit','button'].includes(type)
                  && !sensitive.test(labelFor(e));
              };
              const target=fields.find(e=>allowed(e)&&labelFor(e).includes(wanted));
              if(!target) return JSON.stringify({ok:false,message:"No safe matching field"});
              target.focus();
              if(target.isContentEditable) target.textContent=value; else target.value=value;
              target.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));
              target.dispatchEvent(new Event('change',{bubbles:true}));
              return JSON.stringify({ok:true,message:"Field filled"});
            })()
            """.trimIndent(),
        )
    }

    private suspend fun findText(webView: WebView, text: String): Result<String> {
        val quoted = JSONObject.quote(text.take(200))
        return evaluate(
            webView,
            """
            (function(){
              const text=$quoted;
              if(!text) return JSON.stringify({ok:false,message:"Missing text"});
              const found=window.find(text,false,false,true,false,false,false);
              return JSON.stringify({ok:found,message:found?"Text found":"Text not found"});
            })()
            """.trimIndent(),
        )
    }

    private suspend fun scroll(webView: WebView, direction: String): Result<String> {
        val amount = when (direction.trim().lowercase()) {
            "up" -> "-Math.max(320,window.innerHeight*0.8)"
            "top" -> "-document.documentElement.scrollHeight"
            "bottom" -> "document.documentElement.scrollHeight"
            else -> "Math.max(320,window.innerHeight*0.8)"
        }
        return evaluate(
            webView,
            """
            (function(){
              window.scrollBy({top:$amount,left:0,behavior:'smooth'});
              return JSON.stringify({ok:true,message:"Scrolled"});
            })()
            """.trimIndent(),
        )
    }

    private suspend fun evaluate(webView: WebView, script: String): Result<String> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    webView.evaluateJavascript(script) { raw ->
                        val decoded = runCatching {
                            when (val value = JSONTokener(raw).nextValue()) {
                                is String -> value
                                else -> value?.toString().orEmpty()
                            }
                        }.getOrDefault(raw.orEmpty())
                        if (continuation.isActive) continuation.resume(Result.success(decoded))
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
            }
        }
}
