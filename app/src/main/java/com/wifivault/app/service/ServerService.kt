package com.wifivault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.wifivault.app.MainActivity
import com.wifivault.app.R
import com.wifivault.app.data.Settings
import com.wifivault.app.data.VaultRepository
import com.wifivault.app.server.VaultHttpServer
import com.wifivault.app.util.Net

/** 前台服务：保持 HTTP 服务在后台持续运行，并提供常驻通知与快捷停止入口。 */
class ServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingRefresh: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ServerHolder.server?.running == true) {
            startForeground(NOTIFICATION_ID, buildNotification())
            return START_STICKY
        }
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        val settings = Settings(this)
        val repository = VaultRepository(this)
        val server = VaultHttpServer(this, settings, repository)
        ServerHolder.server = server

        val port = settings.port
        if (!server.start(port)) {
            ServerState.setError(server.lastError ?: "端口 $port 可能被占用")
            ServerState.setRunning(false)
            ServerHolder.server = null
            stopSelf()
            return
        }

        acquireLocks()
        val ip = Net.localIpAddress()
        ServerState.setPort(port)
        ServerState.setAddress(if (ip != null) "http://$ip:$port" else "http://<手机IP>:$port")
        ServerState.setError(null)
        ServerState.setRunning(true)
        settings.lastRunning = true

        startForeground(NOTIFICATION_ID, buildNotification())
        registerNetworkWatch()
        requestTileUpdate()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFiVault:server")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WiFiVault:server")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        wifiLock = null
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        createChannel()
        val host = currentHostPort()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, host))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.notification_big, host))
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    /** 通知里只显示 IP:端口，方便用户在其他设备上照着输入。 */
    private fun currentHostPort(): String {
        Net.hostOnly(ServerState.address.value)?.let { return it }
        val ip = Net.localIpAddress()
        val port = Settings(this).port
        return if (ip != null) "$ip:$port" else "<手机IP>:$port"
    }

    /**
     * 监听网络变化（切换 WiFi、开热点、重连），把通知里的地址刷新成当前 IP，
     * 避免显示出已失效的旧地址。
     */
    private fun registerNetworkWatch() {
        if (networkCallback != null) return
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleAddressRefresh()
            override fun onLost(network: Network) = scheduleAddressRefresh()
        }
        if (runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess) {
            networkCallback = callback
        }
    }

    private fun unregisterNetworkWatch() {
        val callback = networkCallback ?: return
        networkCallback = null
        pendingRefresh?.let { mainHandler.removeCallbacks(it) }
        pendingRefresh = null
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    /** 网络回调会连续触发多次，这里合并成一次延迟刷新。 */
    private fun scheduleAddressRefresh() {
        pendingRefresh?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { refreshAddress() }
        pendingRefresh = runnable
        mainHandler.postDelayed(runnable, 800)
    }

    private fun refreshAddress() {
        pendingRefresh = null
        if (ServerHolder.server?.running != true) return
        val ip = Net.localIpAddress() ?: return
        val address = "http://$ip:${Settings(this).port}"
        if (ServerState.address.value == address) return
        ServerState.setAddress(address)
        refreshNotification()
        requestTileUpdate()
    }

    private fun refreshNotification() {
        if (ServerHolder.server?.running != true) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    /** 让下拉栏里的快捷磁贴同步最新状态（磁贴未添加时调用无副作用）。 */
    private fun requestTileUpdate() {
        runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, VaultTileService::class.java)
            )
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    override fun onDestroy() {
        unregisterNetworkWatch()
        ServerHolder.server?.stop()
        ServerHolder.server = null
        ServerState.setRunning(false)
        Settings(this).lastRunning = false
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        requestTileUpdate()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.wifivault.app.action.START"
        const val ACTION_STOP = "com.wifivault.app.action.STOP"
        private const val NOTIFICATION_ID = 20240918
        private const val CHANNEL_ID = "wifivault_server"
    }
}
