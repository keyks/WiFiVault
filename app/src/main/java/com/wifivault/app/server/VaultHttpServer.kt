package com.wifivault.app.server

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wifivault.app.data.Library
import com.wifivault.app.data.Settings
import com.wifivault.app.data.VaultRepository
import com.wifivault.app.util.Thumbs
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 极简 HTTP/1.1 服务器：零第三方依赖，支持断点续传（视频拖动必需）。
 * 所有网页资源来自 APK 内置 assets，观看端无需联网。
 */
class VaultHttpServer(
    private val context: Context,
    private val settings: Settings,
    private val repository: VaultRepository
) {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var pool: ExecutorService? = null
    private val stopped = AtomicBoolean(false)

    @Volatile
    var running = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun start(port: Int): Boolean {
        if (running) return true
        stopped.set(false)
        lastError = null
        return try {
            val socket = ServerSocket(port, 64, java.net.InetAddress.getByName("0.0.0.0"))
            socket.reuseAddress = true
            serverSocket = socket
            pool = Executors.newFixedThreadPool(16)
            running = true
            acceptThread = Thread({
                while (!stopped.get()) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        if (!stopped.get()) Log.w(TAG, "accept 失败", e)
                        null
                    } ?: break
                    client.soTimeout = 60_000
                    client.tcpNoDelay = true
                    pool?.execute { handle(client) }
                }
            }, "wifivault-accept").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "启动失败", e)
            running = false
            false
        }
    }

    fun stop() {
        stopped.set(true)
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
        acceptThread?.interrupt()
        acceptThread = null
    }

    // ---------------------------------------------------------------- 连接处理

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream(), 8192)
        val out = BufferedOutputStream(socket.getOutputStream(), 8192)
        try {
            while (true) {
                val requestLine = readLine(input) ?: return
                if (requestLine.isBlank()) continue
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.US)
                val target = parts[1]
                val headers = readHeaders(input)
                val len = headers["content-length"]?.toLongOrNull()
                val body = if (len != null && len > 0) readBody(input, len) else null
                val keep = dispatch(method, target, headers, body, out)
                out.flush()
                if (!keep) return
            }
        } catch (e: Exception) {
            // 客户端断开等，忽略
        } finally {
            runCatching { out.flush() }
            runCatching { socket.close() }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c = input.read()
        if (c == -1) return null
        while (c != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isBlank()) break
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            headers[line.substring(0, idx).trim().lowercase(Locale.US)] = line.substring(idx + 1).trim()
        }
        return headers
    }

    private fun readBody(input: InputStream, length: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var remaining = length
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buf, 0, min(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- 路由

    private fun dispatch(
        method: String,
        target: String,
        headers: Map<String, String>,
        body: ByteArray?,
        out: OutputStream
    ): Boolean {
        val uri = Uri.parse("http://localhost$target")
        val path = uri.path ?: "/"
        val query = uri

        if (path == "/api/login" && method == "POST") return login(body, out)

        if (!authorized(headers)) {
            val wantsHtml = (headers["accept"] ?: "").contains("text/html")
            if (wantsHtml && path != "/api/ping") {
                val page = WebAssets.read(context, "lock.html")
                    ?: "<h1>需要访问密码</h1>".toByteArray()
                respondBytes(out, 200, "OK", page, "text/html; charset=utf-8")
            } else {
                respondBytes(
                    out, 401, "Unauthorized",
                    JSONObject().put("error", "unauthorized").toString().toByteArray(),
                    "application/json; charset=utf-8"
                )
            }
            return false
        }

        return when {
            path == "/" || path == "/index.html" ->
                serveAsset(out, "index.html", method)

            path == "/favicon.ico" || path == "/favicon.svg" ->
                serveAsset(out, "favicon.svg", method, cacheable = true)

            path.startsWith("/assets/") ->
                serveAsset(out, path.removePrefix("/assets/"), method, cacheable = true)

            path == "/api/ping" -> {
                respondBytes(out, 200, "OK", JSONObject().put("ok", true).toString().toByteArray(), jsonType)
                false
            }

            path == "/api/libraries" -> libraries(out)

            path == "/api/config" -> config(out)

            path == "/api/list" -> list(out, query.getQueryParameter("lib"), query.getQueryParameter("path"))

            path == "/api/videos" -> videos(out, query.getQueryParameter("lib"), query.getQueryParameter("path"))

            path == "/api/raw" -> raw(out, method, headers, query.getQueryParameter("lib"), query.getQueryParameter("path"), false)

            path == "/api/download" -> raw(out, method, headers, query.getQueryParameter("lib"), query.getQueryParameter("path"), true)

            path == "/api/thumb" -> thumb(out, query.getQueryParameter("lib"), query.getQueryParameter("path"))

            path == "/api/subtitle" -> subtitle(out, query.getQueryParameter("lib"), query.getQueryParameter("path"))

            else -> {
                val name = path.trimStart('/')
                val nested = name.startsWith("vendor/plyr/")
                val isStatic = name.isNotEmpty() &&
                    !name.contains("..") &&
                    (!name.contains('/') || nested) &&
                    name.substringAfterLast('.') in STATIC_EXT
                if (isStatic) serveAsset(out, name, method, cacheable = true) else notFound(out)
            }
        }
    }

    private fun authorized(headers: Map<String, String>): Boolean {
        val pin = settings.pin
        if (pin.isEmpty()) return true
        val expected = token()
        val cookie = headers["cookie"] ?: return false
        return cookie.split(';').any { it.trim() == "$COOKIE=$expected" }
    }

    private fun token(): String = try {
        val digest = MessageDigest.getInstance("SHA-256").digest(settings.pin.toByteArray())
        digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
    } catch (e: Exception) {
        settings.pin.hashCode().toString()
    }

    private fun login(body: ByteArray?, out: OutputStream): Boolean {
        val pin = body?.toString(Charsets.UTF_8)?.let {
            runCatching { JSONObject(it).optString("pin") }.getOrNull()
        } ?: ""
        return if (pin == settings.pin) {
            val extra = listOf("Set-Cookie" to "$COOKIE=${token()}; Path=/; Max-Age=31536000; SameSite=Lax")
            respondBytes(out, 200, "OK", b("""{"ok":true}"""), jsonType, extra = extra)
            false
        } else {
            respondBytes(out, 403, "Forbidden", b("""{"ok":false,"error":"bad pin"}"""), jsonType)
            false
        }
    }

    private fun libraries(out: OutputStream): Boolean {
        val array = JSONArray()
        settings.libraries().forEach { lib ->
            array.put(JSONObject().put("id", lib.id).put("name", lib.name))
        }
        val json = JSONObject()
            .put("name", "WiFiVault")
            .put("libraries", array)
        respondBytes(out, 200, "OK", json.toString().toByteArray(), jsonType, noStore = true)
        return false
    }

    /** 播放器等观看端行为配置，改动后观看端刷新页面即可生效。 */
    private fun config(out: OutputStream): Boolean {
        val json = JSONObject()
            .put("seekStep", settings.seekStep)
            .put("longPressRate", settings.longPressRate.toDouble())
            .put("autoPlayNext", settings.autoPlayNext)
        respondBytes(out, 200, "OK", json.toString().toByteArray(), jsonType, noStore = true)
        return false
    }

    private fun list(out: OutputStream, libId: String?, path: String?): Boolean {
        val lib = findLib(libId)
        if (lib == null) {
            respondBytes(out, 404, "Not Found", b("""{"error":"no library"}"""), jsonType)
            return false
        }
        val rel = VaultRepository.normalize(path).joinToString("/")
        val items = JSONArray()
        try {
            repository.listDir(lib, rel).forEach { node ->
                val itemPath = VaultRepository.join(rel, node.name)
                items.put(
                    JSONObject()
                        .put("name", node.name)
                        .put("path", itemPath)
                        .put("type", WebAssets.kindOf(node.name, node.isDirectory, node.mimeType))
                        .put("size", node.size)
                        .put("mtime", node.lastModified)
                        .put("mime", node.mimeType)
                )
            }
        } catch (e: Exception) {
            respondBytes(out, 500, "Server Error", b("""{"error":"${e.message ?: "read error"}"}"""), jsonType)
            return false
        }
        val json = JSONObject()
            .put("lib", lib.id)
            .put("libName", lib.name)
            .put("path", rel)
            .put("items", items)
        respondBytes(out, 200, "OK", json.toString().toByteArray(), jsonType, noStore = true)
        return false
    }

    /**
     * 播放目录：返回当前目录下的视频，以及一层子目录里的视频。
     * 观看端用它做「跨目录连播」与切换列表，主目录与子目录的视频都可以在播放器里直接切换。
     */
    private fun videos(out: OutputStream, libId: String?, path: String?): Boolean {
        val lib = findLib(libId)
        if (lib == null) {
            respondBytes(out, 404, "Not Found", b("""{"error":"no library"}"""), jsonType)
            return false
        }
        val rel = VaultRepository.normalize(path).joinToString("/")

        fun videosIn(dirPath: String): JSONArray {
            val arr = JSONArray()
            val nodes = try {
                repository.listDir(lib, dirPath)
            } catch (e: Exception) {
                return arr
            }
            nodes.forEach { node ->
                if (node.isDirectory) return@forEach
                if (WebAssets.kindOf(node.name, false, node.mimeType) != "video") return@forEach
                if (arr.length() >= MAX_VIDEOS_PER_DIR) return@forEach
                arr.put(
                    JSONObject()
                        .put("name", node.name)
                        .put("path", VaultRepository.join(dirPath, node.name))
                        .put("size", node.size)
                )
            }
            return arr
        }

        val groups = JSONArray()
        groups.put(
            JSONObject()
                .put("name", if (rel.isEmpty()) lib.name else rel.substringAfterLast('/'))
                .put("path", rel)
                .put("items", videosIn(rel))
        )

        val subDirs = try {
            repository.listDir(lib, rel).filter { it.isDirectory }
        } catch (e: Exception) {
            emptyList()
        }
        subDirs.take(MAX_SUB_DIRS).forEach { dir ->
            val dirPath = VaultRepository.join(rel, dir.name)
            val items = videosIn(dirPath)
            if (items.length() > 0) {
                groups.put(JSONObject().put("name", dir.name).put("path", dirPath).put("items", items))
            }
        }

        val json = JSONObject()
            .put("lib", lib.id)
            .put("path", rel)
            .put("groups", groups)
        respondBytes(out, 200, "OK", json.toString().toByteArray(), jsonType, noStore = true)
        return false
    }

    private fun raw(
        out: OutputStream,
        method: String,
        headers: Map<String, String>,
        libId: String?,
        path: String?,
        asAttachment: Boolean
    ): Boolean {
        val lib = findLib(libId)
        val node = lib?.let { repository.resolve(it, path.orEmpty()) }
        if (lib == null || node == null || node.isDirectory) {
            respondBytes(out, 404, "Not Found", b("Not Found"), "text/plain; charset=utf-8")
            return false
        }
        val total = lengthOf(node)
        val range = parseRange(headers["range"], total)
        val start: Long
        val end: Long
        if (range != null && range.first < total) {
            start = range.first
            end = min(range.second, total - 1)
        } else if (range != null) {
            respondBytes(
                out, 416, "Range Not Satisfiable",
                b(""), "text/plain",
                extra = listOf("Content-Range" to "bytes */$total")
            )
            return false
        } else {
            start = 0
            end = total - 1
        }

        val mime = WebAssets.mimeOfFile(node.name)
        val extra = ArrayList<Pair<String, String>>()
        extra += "Accept-Ranges" to "bytes"
        extra += "Content-Type" to mime
        extra += "Cache-Control" to "public, max-age=86400"
        if (asAttachment) {
            val encoded = Uri.encode(node.name)
            extra += "Content-Disposition" to "attachment; filename*=UTF-8''$encoded"
        }
        if (range != null) {
            extra += "Content-Range" to "bytes $start-$end/$total"
        }

        if (total > 0) {
            val length = end - start + 1
            writeHead(out, if (range != null) 206 else 200, if (range != null) "Partial Content" else "OK",
                extra + ("Content-Length" to length.toString()))
            if (method != "HEAD") {
                try {
                    repository.openStream(node, start).use { input ->
                        copy(out, input, length)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "传输中断: ${node.name}", e)
                }
            }
        } else {
            writeHead(out, 200, "OK", extra)
            if (method != "HEAD") {
                try {
                    repository.openStream(node, start).use { input -> copy(out, input, Long.MAX_VALUE) }
                } catch (e: Exception) {
                    Log.w(TAG, "传输中断: ${node.name}", e)
                }
            }
        }
        return false
    }

    private fun thumb(out: OutputStream, libId: String?, path: String?): Boolean {
        val lib = findLib(libId)
        val node = lib?.let { repository.resolve(it, path.orEmpty()) }
        val kind = node?.let { WebAssets.kindOf(it.name, false, it.mimeType) }
        val bitmap = if (node != null && (kind == "image" || kind == "video")) {
            Thumbs.get(context, node.uri, node.mimeType, kind == "video")
        } else null
        if (bitmap == null) {
            respondBytes(out, 404, "Not Found", ByteArray(0), "image/png")
            return false
        }
        val bytes = ByteArrayOutputStream().use { bos ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
            bos.toByteArray()
        }
        respondBytes(out, 200, "OK", bytes, "image/jpeg", extra = listOf("Cache-Control" to "public, max-age=86400"))
        return false
    }

    private fun subtitle(out: OutputStream, libId: String?, path: String?): Boolean {
        val lib = findLib(libId) ?: return notFound(out)
        val rel = VaultRepository.normalize(path)
        if (rel.isEmpty()) return notFound(out)
        val baseName = rel.last().substringBeforeLast('.')
        val parent = rel.dropLast(1).joinToString("/")
        val candidates = listOf("srt", "vtt")
        val nodes = repository.listDir(lib, parent)
        for (ext in candidates) {
            val match = nodes.firstOrNull {
                !it.isDirectory && it.name.equals("$baseName.$ext", ignoreCase = true)
            } ?: continue
            val text = try {
                context.contentResolver.openInputStream(match.uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            } ?: continue
            val vtt = if (ext == "srt") srtToVtt(text) else text
            respondBytes(out, 200, "OK", vtt.toByteArray(), "text/vtt; charset=utf-8", noStore = true)
            return false
        }
        return notFound(out)
    }

    private fun srtToVtt(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val converted = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})").replace(normalized) {
            "${it.groupValues[1]}:${it.groupValues[2]}:${it.groupValues[3]}.${it.groupValues[4]}"
        }
        return "WEBVTT\n\n$converted"
    }

    private fun serveAsset(
        out: OutputStream,
        name: String,
        method: String,
        cacheable: Boolean = false
    ): Boolean {
        val safeName = name.replace("..", "").trimStart('/')
        val bytes = WebAssets.read(context, safeName)
            ?: WebAssets.read(context, "index.html")
            ?: return notFound(out)
        val extra = when {
            // 页面本身不缓存，保证升级 App 后浏览器能拿到新的 viewport / 资源版本
            name.endsWith(".html") -> listOf("Cache-Control" to "no-cache")
            cacheable -> listOf("Cache-Control" to "public, max-age=86400")
            else -> listOf("Cache-Control" to "no-cache")
        }
        if (method == "HEAD") {
            respondBytes(out, 200, "OK", ByteArray(0), WebAssets.mimeOf(safeName), extra = extra)
        } else {
            respondBytes(out, 200, "OK", bytes, WebAssets.mimeOf(safeName), extra = extra)
        }
        return false
    }

    private fun notFound(out: OutputStream): Boolean {
        respondBytes(out, 404, "Not Found", b("Not Found"), "text/plain; charset=utf-8")
        return false
    }

    private fun findLib(id: String?): Library? = settings.libraries().firstOrNull { it.id == id }

    private fun lengthOf(node: VaultRepository.Node): Long {
        if (node.size > 0) return node.size
        return try {
            context.contentResolver.openFileDescriptor(node.uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header == null || total <= 0) return null
        val value = header.removePrefix("bytes=").substringBefore(',').trim()
        if (value.isEmpty()) return null
        val parts = value.split('-', limit = 2)
        val startStr = parts[0].trim()
        val endStr = parts.getOrNull(1)?.trim().orEmpty()
        return when {
            startStr.isEmpty() && endStr.isEmpty() -> null
            startStr.isEmpty() -> {
                val suffix = endStr.toLongOrNull() ?: return null
                val start = (total - suffix).coerceAtLeast(0)
                start to (total - 1)
            }
            else -> {
                val start = startStr.toLongOrNull() ?: return null
                val end = if (endStr.isEmpty()) total - 1 else min(endStr.toLongOrNull() ?: (total - 1), total - 1)
                start to end
            }
        }
    }

    private fun copy(out: OutputStream, input: InputStream, limit: Long) {
        val buf = ByteArray(64 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val n = input.read(buf, 0, min(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
        out.flush()
    }

    // ---------------------------------------------------------------- 响应

    private fun writeHead(out: OutputStream, code: Int, reason: String, headers: List<Pair<String, String>>) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        headers.forEach { sb.append(it.first).append(": ").append(it.second).append("\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun respondBytes(
        out: OutputStream,
        code: Int,
        reason: String,
        body: ByteArray,
        contentType: String,
        extra: List<Pair<String, String>> = emptyList(),
        noStore: Boolean = false
    ) {
        val headers = ArrayList<Pair<String, String>>()
        headers += "Content-Type" to contentType
        headers += "Content-Length" to body.size.toString()
        if (noStore) headers += "Cache-Control" to "no-store"
        headers.addAll(extra)
        writeHead(out, code, reason, headers)
        out.write(body)
        out.flush()
    }

    private fun b(text: String) = text.toByteArray()

    companion object {
        private const val TAG = "WiFiVault"
        private const val COOKIE = "wv_token"
        private const val jsonType = "application/json; charset=utf-8"
        private val STATIC_EXT = setOf("css", "js", "svg", "ico", "html", "woff2", "png")
        private const val MAX_SUB_DIRS = 40
        private const val MAX_VIDEOS_PER_DIR = 300
    }
}
