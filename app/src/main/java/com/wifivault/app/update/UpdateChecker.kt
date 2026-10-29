package com.wifivault.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wifivault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    object Failed : UpdateResult()
    object Skipped : UpdateResult()
}

/**
 * 从仓库根目录的 update.json 检查新版本。
 *
 * 更新源使用 GitHub raw，失败后尝试 jsDelivr；国内网络下 JSON 很小，
 * APK 仍由系统浏览器下载，避免在 App 内处理大文件下载与安装权限。
 */
object UpdateChecker {

    private const val PREFS = "wifivault_update"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val HOUR_MS = 60L * 60 * 1000

    private val updateUrls = listOf(
        "https://raw.githubusercontent.com/keyks/WiFiVault/main/update.json",
        "https://cdn.jsdelivr.net/gh/keyks/WiFiVault@main/update.json",
        "https://raw.githubusercontent.com/keyks/WiFiVault/master/update.json",
        "https://cdn.jsdelivr.net/gh/keyks/WiFiVault@master/update.json"
    )

    suspend fun check(context: Context, force: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)
            val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
            if (lastSuccess > 0 && now - lastSuccess < DAY_MS) return@withContext UpdateResult.Skipped
            if (lastAttempt > 0 && now - lastAttempt < HOUR_MS) return@withContext UpdateResult.Skipped
        }

        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        val json = fetchAny() ?: return@withContext UpdateResult.Failed
        val remoteCode = json.optInt("versionCode", 0)
        val versionName = json.optString("versionName").trim()
        val apkUrl = json.optString("apkUrl").trim()
        val releaseUrl = json.optString("releaseUrl").trim()
        if (remoteCode <= 0 || versionName.isEmpty()) return@withContext UpdateResult.Failed

        prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
        if (remoteCode <= BuildConfig.VERSION_CODE) return@withContext UpdateResult.UpToDate
        if (apkUrl.isEmpty() && releaseUrl.isEmpty()) return@withContext UpdateResult.Failed

        UpdateResult.Available(
            UpdateInfo(
                versionCode = remoteCode,
                versionName = versionName,
                apkUrl = apkUrl.ifEmpty { releaseUrl },
                releaseUrl = releaseUrl,
                notes = json.optString("notes").trim()
            )
        )
    }

    private fun fetchAny(): JSONObject? {
        for (url in updateUrls) {
            fetch(url)?.let { return it }
        }
        return null
    }

    private fun fetch(url: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WiFiVault/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > 256 * 1024) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** 用浏览器打开下载页 / 发布页；APK 的安装由系统浏览器处理。 */
    fun openUrl(context: Context, url: String): Boolean {
        // 只允许 HTTPS，避免 update.json 被篡改后导向明文下载地址。
        if (url.isBlank() || !url.startsWith("https://", ignoreCase = true)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
