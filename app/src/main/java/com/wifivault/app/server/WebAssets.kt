package com.wifivault.app.server

import android.content.Context
import java.io.ByteArrayOutputStream

/**
 * 网页端资源全部打包在 APK 的 assets/web 目录中，
 * 因此观看端在完全断网（飞行模式 / 纯局域网）的情况下也能正常加载界面与播放器。
 */
object WebAssets {

    fun read(context: Context, name: String): ByteArray? = try {
        context.assets.open("web/$name").use { input ->
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(8192)
            var read: Int
            while (input.read(tmp).also { read = it } > 0) buffer.write(tmp, 0, read)
            buffer.toByteArray()
        }
    } catch (e: Exception) {
        null
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    fun mimeOfFile(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            "avi" -> "video/x-msvideo"
            "ts" -> "video/mp2t"
            "flv" -> "video/x-flv"
            "pdf" -> "application/pdf"
            "svg" -> "image/svg+xml"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt", "log", "md", "json", "srt", "vtt", "lrc", "nfo", "csv", "xml",
            "yml", "yaml", "ini", "conf", "properties", "html", "htm" -> "text/plain; charset=utf-8"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    /** 前端据此决定用什么方式预览；html 一律按纯文本处理，避免共享的网页脚本被执行。 */
    private val TEXT_LIKE = setOf(
        "txt", "log", "md", "json", "srt", "vtt", "lrc", "nfo", "csv", "xml",
        "yml", "yaml", "ini", "conf", "properties", "html", "htm",
        "kt", "java", "py", "js", "ts", "jsx", "tsx", "c", "h", "cpp", "hpp",
        "cs", "go", "rs", "rb", "php", "sh", "bat", "ps1", "sql", "gradle"
    )

    private val OFFICE_EXT = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")

    fun kindOf(name: String, isDirectory: Boolean, mime: String): String {
        if (isDirectory) return "dir"
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "svg") -> "image"
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp", "avi", "ts", "flv") -> "video"
            ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus") -> "audio"
            ext in TEXT_LIKE -> "text"
            ext in OFFICE_EXT -> "office"
            ext == "pdf" -> "pdf"
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            mime.startsWith("text/") -> "text"
            else -> "other"
        }
    }
}
