package com.wifivault.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections

/**
 * 基于 Storage Access Framework 的只读虚拟文件系统。
 * 只暴露用户主动授权过的文件夹，路径全部为库内相对路径。
 */
class VaultRepository(private val context: Context) {

    data class Node(
        val name: String,
        val uri: Uri,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    private val cache: MutableMap<String, Pair<Long, List<Node>>> =
        Collections.synchronizedMap(LinkedHashMap())

    companion object {
        private const val CACHE_TTL_MS = 15_000L
        private const val CACHE_MAX = 200

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        fun normalize(path: String?): List<String> =
            path.orEmpty().split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }

        fun join(parent: String, name: String): String =
            if (parent.isEmpty()) name else "$parent/$name"
    }

    /** 列出某库内相对路径 [path] 下的条目，目录在前、按名称排序。 */
    fun listDir(lib: Library, path: String = ""): List<Node> {
        val key = lib.id + "|" + path
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.let { (ts, nodes) ->
                if (now - ts < CACHE_TTL_MS) return nodes
            }
        }
        val nodes = try {
            queryChildren(lib, path)
        } catch (e: Exception) {
            queryChildrenFallback(lib, path)
        }
        synchronized(cache) {
            if (cache.size >= CACHE_MAX) {
                val it = cache.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            cache[key] = now to nodes
        }
        return nodes
    }

    /** 定位到 [path]；path 为空时返回库根目录自身（仅用于取名字）。 */
    fun resolve(lib: Library, path: String): Node? {
        var current: Node? = null
        var currentPath = ""
        for (seg in normalize(path)) {
            val child = listDir(lib, currentPath).firstOrNull { it.name == seg } ?: return null
            current = child
            currentPath = join(currentPath, seg)
        }
        return current
    }

    /** 打开位于 [offset] 处的文件输入流（用于 HTTP Range 续传）。 */
    fun openStream(node: Node, offset: Long): InputStream {
        val pfd = context.contentResolver.openFileDescriptor(node.uri, "r")
            ?: throw IOException("无法打开文件: ${node.name}")
        val fis = FileInputStream(pfd.fileDescriptor)
        return try {
            if (offset > 0) {
                var remaining = offset
                while (remaining > 0) {
                    val skipped = fis.skip(remaining)
                    if (skipped <= 0) break
                    remaining -= skipped
                }
                if (remaining > 0) throw IOException("无法定位到指定位置")
            }
            object : FilterInputStream(fis) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { pfd.close() }
                    }
                }
            }
        } catch (e: Throwable) {
            runCatching { fis.close() }
            runCatching { pfd.close() }
            throw e
        }
    }

    private fun queryChildren(lib: Library, path: String): List<Node> {
        val rootDocId = DocumentsContract.getTreeDocumentId(lib.uri)
        val parentDocId = resolveDocId(lib, path, rootDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(lib.uri, parentDocId)
        val result = ArrayList<Node>()
        context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2) ?: "application/octet-stream"
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4)
                result.add(
                    Node(
                        name = name,
                        uri = DocumentsContract.buildDocumentUriUsingTree(lib.uri, docId),
                        mimeType = mime,
                        size = size,
                        lastModified = modified,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    )
                )
            }
        } ?: throw IOException("目录不可读: $path")
        result.sortWith(compareByDescending<Node> { it.isDirectory }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return result
    }

    private fun resolveDocId(lib: Library, path: String, rootDocId: String): String {
        var docId = rootDocId
        var currentPath = ""
        for (seg in normalize(path)) {
            val child = listDir(lib, currentPath).firstOrNull { it.name == seg }
                ?: throw IOException("路径不存在: $seg")
            docId = DocumentsContract.getDocumentId(child.uri)
            currentPath = join(currentPath, seg)
        }
        return docId
    }

    /** 兜底：部分第三方存储提供器不支持 child documents 查询，改用 DocumentFile 逐级遍历。 */
    private fun queryChildrenFallback(lib: Library, path: String): List<Node> {
        val root = DocumentFile.fromTreeUri(context, lib.uri) ?: return emptyList()
        var dir = root
        for (seg in normalize(path)) {
            dir = dir.findFile(seg) ?: return emptyList()
        }
        return dir.listFiles()
            .filter { !it.name.orEmpty().startsWith(".") }
            .map { f ->
                Node(
                    name = f.name.orEmpty(),
                    uri = f.uri,
                    mimeType = f.type ?: "application/octet-stream",
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = f.isDirectory
                )
            }
            .sortedWith(
                compareByDescending<Node> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }
}
