package com.wifivault.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.wifivault.app.data.Settings
import com.wifivault.app.service.ServerService
import com.wifivault.app.service.ServerState
import com.wifivault.app.ui.MainScreen
import com.wifivault.app.ui.UpdateDialog
import com.wifivault.app.ui.theme.WiFiVaultTheme
import com.wifivault.app.update.UpdateChecker
import com.wifivault.app.update.UpdateInfo
import com.wifivault.app.update.UpdateResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermissionIfNeeded()
        // 上次退出时若服务仍在运行（例如进程被系统回收），重新打开 App 时自动恢复
        if (Settings(this).lastRunning && !ServerState.running.value) startServer()
        setContent {
            WiFiVaultTheme {
                val running by ServerState.running.collectAsState()
                val address by ServerState.address.collectAsState()
                val error by ServerState.error.collectAsState()

                val pickFolder = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        // 本应用只读共享，只申请读权限；写权限会被部分只读提供器拒绝，
                        // 导致持久化授权整体失败，重启后文件夹丢失。
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, flags)
                        }
                        FolderEvents.onPicked?.invoke(uri)
                    }
                }

                val scope = rememberCoroutineScope()
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var checkingUpdate by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val result = UpdateChecker.check(this@MainActivity, force = false)
                    if (result is UpdateResult.Available) updateInfo = result.info
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        running = running,
                        address = address,
                        error = error,
                        onPickFolder = { FolderEvents.onPicked = it; pickFolder.launch(null) },
                        onStart = { startServer() },
                        onStop = { stopServer() },
                        onCheckUpdate = {
                            if (!checkingUpdate) {
                                checkingUpdate = true
                                scope.launch {
                                    try {
                                        when (val result = UpdateChecker.check(this@MainActivity, force = true)) {
                                            is UpdateResult.Available -> updateInfo = result.info
                                            UpdateResult.UpToDate -> toast("已是最新版本")
                                            UpdateResult.Failed -> toast("检查更新失败，请检查网络")
                                            UpdateResult.Skipped -> Unit
                                        }
                                    } finally {
                                        checkingUpdate = false
                                    }
                                }
                            }
                        },
                        checkingUpdate = checkingUpdate
                    )

                    updateInfo?.let { info ->
                        UpdateDialog(
                            info = info,
                            onDismiss = { updateInfo = null },
                            onDownload = {
                                if (!UpdateChecker.openUrl(this@MainActivity, info.apkUrl)) {
                                    toast("无法打开下载页面")
                                }
                                updateInfo = null
                            },
                            onOpenRelease = {
                                UpdateChecker.openUrl(this@MainActivity, info.releaseUrl)
                                updateInfo = null
                            }
                        )
                    }
                }
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun startServer() {
        val intent = Intent(this, ServerService::class.java).setAction(ServerService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopServer() {
        stopService(Intent(this, ServerService::class.java))
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }
}

/** 文件夹选择结果的临时回调桥（SAF launcher 只能在 Composable 中注册）。 */
object FolderEvents {
    var onPicked: ((android.net.Uri) -> Unit)? = null
}
