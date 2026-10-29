package com.wifivault.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.wifivault.app.MainActivity
import com.wifivault.app.R
import com.wifivault.app.util.Net

/**
 * 下拉通知栏里的快捷磁贴：一键启停局域网共享服务。
 * 磁贴副标题显示当前访问地址（IP:端口），方便在其他设备上直接输入。
 */
class VaultTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ServerState.running.value) stopSharing() else startSharing()
        updateTile()
        // 服务的启动 / 停止是异步完成的，稍后再对齐几次真实状态
        listOf(400L, 1000L, 2000L).forEach { delay ->
            mainHandler.postDelayed({ updateTile() }, delay)
        }
    }

    private fun startSharing() {
        val intent = Intent(this, ServerService::class.java).setAction(ServerService.ACTION_START)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            // Android 12+ 个别机型会拒绝后台启动前台服务，此时退回打开 App 由用户手动开启
            Log.w(TAG, "startForegroundService 被拒绝", e)
            openApp()
        }
    }

    private fun stopSharing() {
        stopService(Intent(this, ServerService::class.java))
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = ServerState.running.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_wifi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) {
                Net.hostOnly(ServerState.address.value) ?: getString(R.string.tile_subtitle_running)
            } else {
                getString(R.string.tile_subtitle_idle)
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "VaultTile"
    }
}
