package com.wifivault.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlin.math.max

/** 视频/图片缩略图生成 + 内存缓存，供网页端网格视图使用。 */
object Thumbs {

    private const val TARGET = 400

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(context: Context, uri: Uri, mime: String, isVideo: Boolean): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        val bmp = if (isVideo) videoFrame(context, uri) else image(context, uri)
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun image(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val buffered = input.buffered()
            buffered.mark(Int.MAX_VALUE)
            BitmapFactory.decodeStream(buffered, null, opts)
            buffered.reset()
            opts.inSampleSize = computeSample(opts.outWidth, opts.outHeight)
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactory.decodeStream(buffered, null, opts)
        }
    } catch (e: Exception) {
        null
    }

    private fun videoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let { scale(it) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scale(src: Bitmap): Bitmap {
        val ratio = TARGET.toFloat() / max(src.width, src.height)
        return if (ratio < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
        } else {
            src
        }
    }

    private fun computeSample(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= TARGET && h / 2 >= TARGET) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return if (sample <= 0) 1 else sample
    }
}
