package com.wifivault.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.wifivault.app.BuildConfig
import com.wifivault.app.data.Library
import com.wifivault.app.data.Settings
import com.wifivault.app.util.Net
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    running: Boolean,
    address: String?,
    error: String?,
    onPickFolder: ((Uri) -> Unit) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCheckUpdate: () -> Unit,
    checkingUpdate: Boolean
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val libraries = remember { mutableStateListOf<Library>().apply { addAll(settings.libraries()) } }

    var showSettings by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    val ip = remember(running) { Net.localIpAddress() }
    val currentAddress = address
        ?: (if (ip != null) "http://$ip:${settings.port}" else "http://192.168.x.x:${settings.port}")
    val statusColor = if (running) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val onAddFolder: () -> Unit = {
        onPickFolder { uri -> addLibrary(context, settings, libraries, uri) }
    }
    val onRemoveFolder: (Library) -> Unit = { lib ->
        releaseLibraryPermission(context, lib.uri)
        settings.removeLibrary(lib.id)
        libraries.clear()
        libraries.addAll(settings.libraries())
    }

    if (showSettings) {
        SettingsScreen(
            context = context,
            settings = settings,
            onBack = { showSettings = false },
            onCheckUpdate = onCheckUpdate,
            checkingUpdate = checkingUpdate
        )
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("WiFiVault", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(statusColor)
                )
            }
        },
        bottomBar = {
            PrimaryControlBar(
                running = running,
                onStart = onStart,
                onStop = onStop
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatusCard(
                    running = running,
                    address = currentAddress,
                    error = error,
                    onCopy = { copy(context, currentAddress) },
                    onQr = { showQr = true }
                )
            }

            item {
                FolderSection(
                    libraries = libraries,
                    onAdd = onAddFolder,
                    onRemove = onRemoveFolder
                )
            }

            item {
                QuickSettingsCard(
                    settings = settings,
                    onOpen = { showSettings = true }
                )
            }

            item {
                Text(
                    text = "仅限同一局域网内使用，不经过任何外部服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showQr && running) {
        QrDialog(
            address = currentAddress,
            onCopy = { copy(context, currentAddress) },
            onDismiss = { showQr = false }
        )
    }
}

@Composable
private fun StatusCard(
    running: Boolean,
    address: String,
    error: String?,
    onCopy: () -> Unit,
    onQr: () -> Unit
) {
    val accent = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val container = if (running) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = if (running) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (running) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (running) "共享中" else "未启动",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onContainer
                    )
                    Text(
                        text = if (running) "同一 WiFi 下的设备可直接观看" else "选择文件夹后，点击下方按钮启动",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (running) "运行中" else "已停止",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "启动失败：$error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = running, onClick = onCopy)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "访问地址",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (running) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制地址",
                        modifier = Modifier.size(20.dp),
                        tint = if (running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onQr,
                    enabled = running,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("二维码")
                }
                FilledTonalButton(
                    onClick = onCopy,
                    enabled = running,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复制地址")
                }
            }
        }
    }
}

@Composable
private fun PrimaryControlBar(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Button(
                onClick = { if (running) onStop() else onStart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (running) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (running) "停止共享" else "启动共享",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QrDialog(
    address: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val qr = remember(address) { qrBitmap(address, 480) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "扫码访问",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "同一 WiFi 下用相机扫码，或在浏览器输入地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                if (qr != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "访问二维码",
                            modifier = Modifier
                                .padding(12.dp)
                                .size(220.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("复制地址")
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSection(
    libraries: List<Library>,
    onAdd: () -> Unit,
    onRemove: (Library) -> Unit
) {
    var pendingRemove by remember { mutableStateOf<Library?>(null) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共享文件夹",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = libraries.size.toString(),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (libraries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "还没有共享文件夹",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "添加照片、视频或文档目录后，其他设备即可浏览",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                libraries.forEach { lib ->
                    FolderRow(
                        library = lib,
                        onRemoveRequest = { pendingRemove = lib }
                    )
                }
            }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除共享") },
            text = {
                Text("确定不再共享「${target.name}」吗？手机上的文件不会被删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        onRemove(target)
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FolderRow(
    library: Library,
    onRemoveRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = libraryPathLabel(library.uri),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("移除共享") },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onRemoveRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsCard(
    settings: Settings,
    onOpen: () -> Unit
) {
    val pin = if (settings.pin.isBlank()) "PIN 未开启" else "PIN 已开启"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "快捷设置",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "端口 ${settings.port} · $pin · 快进 ${settings.seekStep}s · ${playerModeLabel(settings.playerMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- 设置

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    context: Context,
    settings: Settings,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    checkingUpdate: Boolean
) {
    var portText by remember { mutableStateOf(settings.port.toString()) }
    var pinText by remember { mutableStateOf(settings.pin) }
    var seekText by remember { mutableStateOf(settings.seekStep.toString()) }
    var rateText by remember { mutableStateOf(settings.longPressRate.toString()) }
    var autoNext by remember { mutableStateOf(settings.autoPlayNext) }
    var playerMode by remember { mutableStateOf(settings.playerMode) }
    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsCard(title = "服务", icon = Icons.Default.Wifi) {
                    EditRow(
                        label = "端口",
                        value = portText,
                        placeholder = "1024 - 65535",
                        keyboardType = KeyboardType.Number,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                        onConfirm = {
                            val port = portText.toIntOrNull()
                            if (port == null || port !in 1024..65535) {
                                toast(context, "端口需在 1024 - 65535 之间")
                            } else {
                                settings.port = port
                                toast(context, "端口已保存，重启服务后生效")
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    EditRow(
                        label = "访问密码",
                        value = pinText,
                        placeholder = "留空表示不设密码",
                        password = true,
                        onValueChange = { pinText = it },
                        onConfirm = {
                            settings.pin = pinText.trim()
                            toast(context, "密码已保存，观看端需重新输入")
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    HintLine("端口修改后需停止并重新启动共享；密码修改后观看端需重新输入。")
                }
            }

            item {
                SettingsCard(title = "播放控制", icon = Icons.Default.PlayArrow) {
                    EditRow(
                        label = "快进 / 快退时长（秒）",
                        value = seekText,
                        placeholder = "1 - 120",
                        keyboardType = KeyboardType.Number,
                        onValueChange = { seekText = it.filter { ch -> ch.isDigit() }.take(3) },
                        onConfirm = {
                            val step = seekText.toIntOrNull()
                            if (step == null || step !in 1..120) {
                                toast(context, "请填写 1 - 120 之间的秒数")
                            } else {
                                settings.seekStep = step
                                toast(context, "已设为 $step 秒，观看端刷新后生效")
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    EditRow(
                        label = "长按屏幕倍速",
                        value = rateText,
                        placeholder = "1.25 - 4",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { rateText = sanitizeRate(it) },
                        onConfirm = {
                            val rate = rateText.toFloatOrNull()
                            if (rate == null || rate !in 1.25f..4f) {
                                toast(context, "请填写 1.25 - 4 之间的倍速")
                            } else {
                                settings.longPressRate = rate
                                toast(context, "长按倍速已设为 ${rate}x")
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("播完自动连播下一个", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "关闭后一个视频播完就停在当前画面",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoNext,
                            onCheckedChange = {
                                autoNext = it
                                settings.autoPlayNext = it
                                toast(context, if (it) "已开启自动连播" else "已关闭自动连播")
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    HintLine("观看端的快进按钮与左右滑动灵敏度都使用此数值。")

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "播放器显示模式",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PLAYER_MODE_OPTIONS.forEach { (value, label) ->
                            FilterChip(
                                selected = playerMode == value,
                                onClick = {
                                    playerMode = value
                                    settings.playerMode = value
                                    toast(context, "已切换为$label，观看端刷新后生效")
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HintLine("自动模式会按浏览器/机型选择；画面被系统控件遮挡时，可切到「精简」或「原生」。")
                }
            }

            item {
                SettingsCard(title = "使用说明", icon = Icons.AutoMirrored.Filled.HelpOutline) {
                    if (showGuide) {
                        Text(
                            text = GUIDE_TEXT,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showGuide = false }) { Text("收起") }
                    } else {
                        Text(
                            text = "查看观看方式、手势操作与常见问题。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showGuide = true }) { Text("展开说明") }
                    }
                }
            }

            item {
                SettingsCard(title = "关于", icon = Icons.Default.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "WiFiVault v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "把手机里的照片、视频、音乐通过局域网共享给同一 WiFi 下的其他设备，" +
                            "播放器与界面全部内置，不依赖外网。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("检查更新", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "从 GitHub Releases 获取新版本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onCheckUpdate,
                            enabled = !checkingUpdate
                        ) {
                            if (checkingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (checkingUpdate) "检查中" else "检查")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun EditRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("确定")
            }
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val PLAYER_MODE_OPTIONS = listOf(
    Settings.PLAYER_MODE_AUTO to "自动",
    Settings.PLAYER_MODE_FULL to "完整",
    Settings.PLAYER_MODE_COMPACT to "精简",
    Settings.PLAYER_MODE_NATIVE to "原生"
)

private fun playerModeLabel(mode: String): String = when (mode) {
    Settings.PLAYER_MODE_FULL -> "完整模式"
    Settings.PLAYER_MODE_COMPACT -> "精简模式"
    Settings.PLAYER_MODE_NATIVE -> "原生控件"
    else -> "自动模式"
}

private fun libraryPathLabel(uri: Uri): String = runCatching {
    val decoded = Uri.decode(uri.toString())
    val tree = decoded.substringAfter("/tree/", "")
    if (tree.isBlank()) decoded else tree.replace(':', '/')
}.getOrDefault(uri.toString())

private const val GUIDE_TEXT =
    "· 电脑 / 平板 / 另一部手机：浏览器打开主界面显示的地址\n" +
        "· 智能电视：用电视自带浏览器打开同一地址，可用遥控器操作\n" +
        "· 完全离线：界面与播放器都内置在 App 内，断网也能正常看\n" +
        "· 视频支持拖动进度（断点续传），图片支持缩放浏览\n" +
        "· 手机端观看：上下滑动调音量，左右滑动拖进度，长按屏幕倍速播放\n" +
        "· 电脑端观看：键盘 ↑ ↓ 调音量，← → 快退快进，长按画面倍速\n" +
        "· 播放器左下角可切换上一个 / 下一个，右下角「播放目录」列出主目录与子目录的视频，点一下即可接着播\n" +
        "· 视频播完会自动连播目录里的下一个，可在上方开关里关闭\n" +
        "· 文档、PDF、文本、代码、压缩包等文件点击后直接预览，不能预览的格式可下载\n" +
        "· 播放器显示模式：自动 / 完整 / 精简 / 原生；画面被系统控件遮挡时可手动切换\n" +
        "· 手机保持本应用在前台或后台运行，观看端才能读到内容"

private fun sanitizeRate(input: String): String {
    var seenDot = false
    return input.filter { ch ->
        when {
            ch.isDigit() -> true
            ch == '.' && !seenDot -> { seenDot = true; true }
            else -> false
        }
    }.take(4)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private fun addLibrary(
    context: Context,
    settings: Settings,
    libraries: MutableList<Library>,
    uri: Uri
) {
    val name = DocumentFile.fromTreeUri(context, uri)?.name
        ?: runCatching {
            Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast(':')
        }.getOrDefault("文件夹")
    settings.addLibrary(
        Library(
            id = UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "文件夹" },
            uri = uri
        )
    )
    libraries.clear()
    libraries.addAll(settings.libraries())
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("address", text))
    Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
}

/** 移除共享目录时释放已持久化的 SAF 权限，避免应用继续持有不再展示的目录。 */
private fun releaseLibraryPermission(context: Context, uri: Uri) {
    val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    val readWrite = read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    if (runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, readWrite)
        }.isFailure
    ) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, read) }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    bmp
} catch (e: Exception) {
    null
}
