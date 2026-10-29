package com.wifivault.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wifivault.app.BuildConfig
import com.wifivault.app.update.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                Text(
                    text = "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = info.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "APK 约 11 MB，建议在 WiFi 下下载；安装时需允许浏览器安装未知来源应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text("下载更新")
            }
        },
        dismissButton = {
            Row {
                if (info.releaseUrl.isNotBlank()) {
                    TextButton(onClick = onOpenRelease) {
                        Text("发布页")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("稍后")
                }
            }
        }
    )
}
