package com.spacebrowser.core.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Tiny per-host favicon cache on internal storage. Icons are captured from the
 * pages the user visits (never fetched from a third-party icon service, which
 * would leak browsing hosts). Private tabs never write here.
 */
object FaviconStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "favicons").apply { mkdirs() }

    private fun safeName(host: String): String =
        host.lowercase().replace(Regex("[^a-z0-9.-]"), "_").take(120)

    fun file(context: Context, host: String): File =
        File(dir(context), safeName(host) + ".png")

    fun save(context: Context, host: String, icon: Bitmap) {
        try {
            file(context, host).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 90, it) }
        } catch (_: Exception) {
        }
    }

    fun load(context: Context, host: String): Bitmap? = try {
        file(context, host).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            dir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }
}
