package com.spacebrowser.core.media

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped yt-dlp runtime based on Seal's proven initialization and
 * request flow. Work is not tied to the download sheet, so closing the UI no
 * longer cancels a running download.
 */
object MediaDownloader {

    enum class Status { DOWNLOADING, DONE, ERROR }

    data class MediaJob(
        val id: String,
        val title: String,
        val progress: Float,
        val etaSeconds: Long,
        val status: Status,
        val message: String? = null,
    )

    data class Choice(
        val label: String,
        val selector: String,
        val extractAudio: Boolean = false,
    )

    data class RequestContext(
        val userAgent: String? = null,
        val cookies: String? = null,
        val referer: String? = null,
    )

    val choices = listOf(
        Choice("Best video", "bv*+ba/b"),
        Choice(
            "Video up to 720p (smaller)",
            "bv*[height<=720]+ba/b[height<=720]/b",
        ),
        Choice("Audio only (m4a)", "ba[ext=m4a]/ba/b", extractAudio = true),
    )

    val jobs = MutableStateFlow<List<MediaJob>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initGate = Any()

    @Volatile private var initialized = false
    @Volatile private var aria2Ready = false

    /**
     * Extracts the bundled runtimes exactly once. yt-dlp and ffmpeg are
     * required; aria2c is opportunistic and downloads automatically retry with
     * yt-dlp's native downloader if it is unavailable or fails.
     */
    suspend fun ensureReady(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            synchronized(initGate) {
                if (!initialized) {
                    val app = context.applicationContext
                    YoutubeDL.init(app)
                    FFmpeg.init(app)
                    aria2Ready = runCatching {
                        Aria2c.init(app)
                        true
                    }.getOrDefault(false)
                    initialized = true
                }
            }
        }
    }

    suspend fun fetchInfo(
        context: Context,
        url: String,
        requestContext: RequestContext = RequestContext(),
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady(context).getOrThrow()
            requireOnline(context)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--socket-timeout", "20")
                addOption("--retries", "3")
                addOption("--extractor-retries", "3")
                addNetworkOptions(requestContext)
            }
            YoutubeDL.getInstance().getInfo(request)
        }
    }

    val hasActiveJob: Boolean
        get() = jobs.value.any { it.status == Status.DOWNLOADING }

    fun download(
        context: Context,
        url: String,
        title: String,
        choice: Choice,
        requestContext: RequestContext = RequestContext(),
        onRefused: () -> Unit,
    ) {
        if (hasActiveJob) {
            onRefused()
            return
        }

        val app = context.applicationContext
        val jobId = UUID.randomUUID().toString()
        update(MediaJob(jobId, title.ifBlank { "Media" }, -1f, 0, Status.DOWNLOADING))

        scope.launch {
            val outDir = File(
                app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.cacheDir,
                "SPACE/.jobs/$jobId",
            )
            try {
                ensureReady(app).getOrThrow()
                requireOnline(app)
                check(outDir.mkdirs() || outDir.isDirectory) {
                    "Couldn't prepare temporary download storage"
                }

                val execute: (Boolean) -> Unit = { useAria2 ->
                    val request = buildRequest(
                        url = url,
                        choice = choice,
                        outDir = outDir,
                        requestContext = requestContext,
                        useAria2 = useAria2,
                    )
                    YoutubeDL.getInstance().execute(request, jobId) { progress, eta, _ ->
                        update(jobId) {
                            it.copy(
                                progress = progress.coerceIn(0f, 100f),
                                etaSeconds = eta.coerceAtLeast(0),
                            )
                        }
                    }
                }

                if (aria2Ready) {
                    runCatching { execute(true) }.getOrElse { error ->
                        if (isCancellation(error)) throw error
                        clearJobOutput(outDir)
                        execute(false)
                    }
                } else {
                    execute(false)
                }

                val produced = outDir.walkTopDown()
                    .filter { it.isFile }
                    .filterNot {
                        it.name.endsWith(".part") ||
                            it.name.endsWith(".ytdl") ||
                            it.name.endsWith(".temp")
                    }
                    .maxByOrNull { it.lastModified() }
                    ?: error("yt-dlp finished but did not produce a media file")

                val savedName = exportToPublicDownloads(app, produced)
                update(jobId) {
                    it.copy(
                        status = Status.DONE,
                        progress = 100f,
                        message = "Saved to Downloads/SPACE/$savedName",
                    )
                }
            } catch (error: Throwable) {
                val alreadyCancelled = jobs.value.firstOrNull { it.id == jobId }
                    ?.message == "Cancelled"
                if (!alreadyCancelled) {
                    update(jobId) {
                        it.copy(
                            status = Status.ERROR,
                            message = friendlyError(error),
                        )
                    }
                }
            } finally {
                outDir.deleteRecursively()
            }
        }
    }

    fun cancel(jobId: String) {
        runCatching { YoutubeDL.destroyProcessById(jobId) }
        update(jobId) { it.copy(status = Status.ERROR, message = "Cancelled") }
    }

    fun clearFinished() {
        jobs.value = jobs.value.filter { it.status == Status.DOWNLOADING }
    }

    private fun buildRequest(
        url: String,
        choice: Choice,
        outDir: File,
        requestContext: RequestContext,
        useAria2: Boolean,
    ) = YoutubeDLRequest(url).apply {
        addOption("-f", choice.selector)
        addOption("-P", outDir.absolutePath)
        addOption("-o", "%(title).120B [%(id)s].%(ext)s")
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--newline")
        addOption("--continue")
        addOption("--retries", "5")
        addOption("--fragment-retries", "5")
        addOption("--extractor-retries", "3")
        addOption("--socket-timeout", "20")
        addOption("--concurrent-fragments", "4")
        addOption("--trim-filenames", "160")
        addOption("--windows-filenames")
        if (choice.extractAudio) {
            addOption("-x")
            addOption("--audio-format", "m4a")
            addOption("--audio-quality", "0")
        }
        if (useAria2) {
            addOption("--downloader", "libaria2c.so")
            addOption("--downloader-args", "aria2c:-x8 -s8 -k1M")
        }
        addNetworkOptions(requestContext)
    }

    private fun YoutubeDLRequest.addNetworkOptions(context: RequestContext) {
        context.userAgent?.takeIf { it.isNotBlank() }?.let {
            addOption("--user-agent", it.take(500))
        }
        context.cookies?.takeIf { it.isNotBlank() }?.let {
            addOption("--add-header", "Cookie:${it.take(8_000)}")
        }
        context.referer?.takeIf {
            it.startsWith("https://") || it.startsWith("http://")
        }?.let {
            addOption("--referer", it)
        }
    }

    private fun requireOnline(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: error("No internet connection")
        val capabilities = manager.getNetworkCapabilities(network)
            ?: error("No internet connection")
        check(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "No internet connection"
        }
    }

    private fun clearJobOutput(directory: File) {
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }

    private fun friendlyError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = root.message?.lineSequence()
            ?.lastOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(300)
        return message ?: "Media download failed"
    }

    private fun isCancellation(error: Throwable): Boolean {
        val name = error::class.java.simpleName.lowercase()
        val message = error.message.orEmpty().lowercase()
        return "cancel" in name || "cancel" in message
    }

    private fun update(job: MediaJob) {
        jobs.value = listOf(job) + jobs.value.filter { it.id != job.id }
    }

    private fun update(id: String, transform: (MediaJob) -> MediaJob) {
        jobs.value = jobs.value.map { if (it.id == id) transform(it) else it }
    }

    private fun exportToPublicDownloads(context: Context, file: File): String {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        var savedName = file.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/SPACE",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create the download entry")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Couldn't write the downloaded media")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SPACE",
            ).apply { mkdirs() }
            var destination = File(directory, file.name)
            var suffix = 1
            while (destination.exists()) {
                destination = File(
                    directory,
                    "${file.nameWithoutExtension} ($suffix).${file.extension}",
                )
                suffix++
            }
            file.copyTo(destination)
            savedName = destination.name
        }
        return savedName
    }
}
