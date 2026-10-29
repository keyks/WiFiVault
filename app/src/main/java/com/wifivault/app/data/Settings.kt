package com.wifivault.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个被用户授权共享的文件夹（"库"）。
 * [uri] 来自 SAF 的 ACTION_OPEN_DOCUMENT_TREE，并已调用 takePersistableUriPermission。
 */
data class Library(
    val id: String,
    val name: String,
    val uri: Uri
)

class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("wifivault_prefs", Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    /** 为空表示不设密码；开启后观看端需输入该 PIN 才能访问。 */
    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    /** 播放器的快进 / 快退步长（秒），同时决定左右滑动的灵敏度。 */
    var seekStep: Int
        get() = prefs.getInt(KEY_SEEK_STEP, DEFAULT_SEEK_STEP)
        set(value) = prefs.edit().putInt(KEY_SEEK_STEP, value.coerceIn(1, 120)).apply()

    /** 长按屏幕时启用的倍速，松手恢复原速。 */
    var longPressRate: Float
        get() = prefs.getFloat(KEY_LONG_PRESS_RATE, DEFAULT_LONG_PRESS_RATE)
        set(value) = prefs.edit().putFloat(KEY_LONG_PRESS_RATE, value.coerceIn(1.25f, 4f)).apply()

    /** 一个视频播完后是否自动接着播目录里的下一个。 */
    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, value).apply()

    var lastRunning: Boolean
        get() = prefs.getBoolean(KEY_LAST_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_RUNNING, value).apply()

    fun libraries(): List<Library> {
        val raw = prefs.getString(KEY_LIBRARIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<Library>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Library(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        uri = Uri.parse(obj.getString("uri"))
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLibraries(list: List<Library>) {
        val array = JSONArray()
        list.forEach { lib ->
            array.put(
                JSONObject().apply {
                    put("id", lib.id)
                    put("name", lib.name)
                    put("uri", lib.uri.toString())
                }
            )
        }
        prefs.edit().putString(KEY_LIBRARIES, array.toString()).apply()
    }

    fun addLibrary(lib: Library) {
        val current = libraries().toMutableList()
        if (current.none { it.uri == lib.uri }) {
            current.add(lib)
            saveLibraries(current)
        }
    }

    fun removeLibrary(id: String) {
        saveLibraries(libraries().filterNot { it.id == id })
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_SEEK_STEP = 5
        const val DEFAULT_LONG_PRESS_RATE = 2.0f

        private const val KEY_PORT = "port"
        private const val KEY_PIN = "pin"
        private const val KEY_LAST_RUNNING = "last_running"
        private const val KEY_LIBRARIES = "libraries"
        private const val KEY_SEEK_STEP = "seek_step"
        private const val KEY_LONG_PRESS_RATE = "long_press_rate"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
    }
}
