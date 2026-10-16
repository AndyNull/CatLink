package com.catlink.app.ui.screens.sync

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.SettingsStore
import com.catlink.app.data.WebDavClient
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SoftCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 远端同步（阶段 2）：WebDAV 加密备份同步。
 * 配置服务器 → 测试连接 → 上传加密备份 / 下载恢复。
 */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val settings = app.container.settings
    val scope = rememberCoroutineScope()

    val syncConfig by settings.syncConfig.collectAsState(initial = SettingsStore.SyncConfig())
    val s3Config by settings.s3Config.collectAsState(initial = SettingsStore.S3Config())
    // 哪个配置非空就默认用哪个
    var provider by remember {
        mutableStateOf(if (s3Config.bucket.isNotBlank() || s3Config.accessKey.isNotBlank()) "s3" else "webdav")
    }
    var showConfigDialog by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf<Boolean?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("upload") }

    val syncFile = remember {
        File(context.cacheDir, "catlink_sync_backup.clb")
    }

    fun runSync(actionType: String, password: String) {
        scope.launch {
            statusText = if (actionType == "upload") "正在上传…" else "正在下载…"
            statusOk = null
            val outcome: Pair<Boolean, String> = withContext(Dispatchers.IO) {
                val dao = app.container.dao
                val db = app.container.database
                // 本机唯一前缀：同一远端上多设备/多用户互不干扰
                val deviceId = settings.ensureDeviceId()
                if (provider == "s3") {
                    val s3 = s3Config
                    val backupKey = "$deviceId/backup.clb"
                    val mediaPrefix = "$deviceId/media/"
                    if (actionType == "upload") {
                        val export = com.catlink.app.data.BackupManager.exportToFile(dao, syncFile, password)
                        if (!export.ok) return@withContext export.ok to export.message
                        val up = com.catlink.app.data.S3Client.upload(s3.endpoint, s3.region, s3.bucket, s3.accessKey, s3.secretKey, backupKey, syncFile)
                        if (!up.ok) return@withContext up.ok to up.message
                        // 结构化媒体同步：把私有照片目录逐个上传（对象存储无需建目录）
                        val photos = com.catlink.app.data.MediaFiles.listFiles(context)
                        var uploaded = 0
                        photos.forEach { f ->
                            val r = com.catlink.app.data.S3Client.upload(s3.endpoint, s3.region, s3.bucket, s3.accessKey, s3.secretKey, mediaPrefix + f.name, f)
                            if (r.ok) uploaded++
                        }
                        true to "备份上传成功（含 $uploaded/${photos.size} 张照片）"
                    } else {
                        val download = com.catlink.app.data.S3Client.download(s3.endpoint, s3.region, s3.bucket, s3.accessKey, s3.secretKey, backupKey, syncFile)
                        if (!download.ok) return@withContext download.ok to download.message
                        val imp = com.catlink.app.data.BackupManager.importFromFile(db, syncFile, password)
                        if (!imp.ok) return@withContext imp.ok to imp.message
                        // 拉回本设备媒体：只下载自己前缀目录下的照片
                        var pulled = 0
                        com.catlink.app.data.S3Client.list(s3.endpoint, s3.region, s3.bucket, s3.accessKey, s3.secretKey, mediaPrefix).forEach { key ->
                            val name = key.substringAfterLast('/')
                            if (name.isNotBlank()) {
                                val dest = File(com.catlink.app.data.MediaFiles.photosDir(context), name)
                                val r = com.catlink.app.data.S3Client.download(s3.endpoint, s3.region, s3.bucket, s3.accessKey, s3.secretKey, key, dest)
                                if (r.ok) pulled++
                            }
                        }
                        true to "${imp.message}（拉回 $pulled 张照片）"
                    }
                } else {
                    val backupName = "${deviceId}_backup.clb"
                    val mediaDir = "$deviceId/media/"
                    if (actionType == "upload") {
                        val export = com.catlink.app.data.BackupManager.exportToFile(dao, syncFile, password)
                        if (!export.ok) return@withContext export.ok to export.message
                        val up = WebDavClient.upload(syncConfig.endpoint, syncConfig.user, syncConfig.pass, syncFile, backupName)
                        if (!up.ok) return@withContext up.ok to up.message
                        // 结构化媒体同步：把私有照片目录逐个上传
                        WebDavClient.mkdir(syncConfig.endpoint, syncConfig.user, syncConfig.pass, mediaDir)
                        val photos = com.catlink.app.data.MediaFiles.listFiles(context)
                        var uploaded = 0
                        photos.forEach { f ->
                            val r = WebDavClient.upload(syncConfig.endpoint, syncConfig.user, syncConfig.pass, f, mediaDir + f.name)
                            if (r.ok) uploaded++
                        }
                        true to "备份上传成功（含 $uploaded/${photos.size} 张照片）"
                    } else {
                        val download = WebDavClient.download(syncConfig.endpoint, syncConfig.user, syncConfig.pass, backupName, syncFile)
                        if (!download.ok) return@withContext download.ok to download.message
                        val imp = com.catlink.app.data.BackupManager.importFromFile(db, syncFile, password)
                        if (!imp.ok) return@withContext imp.ok to imp.message
                        // 拉回本设备媒体：只下载自己前缀目录下的照片
                        var pulled = 0
                        WebDavClient.list(syncConfig.endpoint, syncConfig.user, syncConfig.pass, mediaDir).forEach { remote ->
                            val name = remote.substringAfterLast('/')
                            if (name.isNotBlank()) {
                                val dest = File(com.catlink.app.data.MediaFiles.photosDir(context), name)
                                val r = WebDavClient.download(syncConfig.endpoint, syncConfig.user, syncConfig.pass, remote, dest)
                                if (r.ok) pulled++
                            }
                        }
                        true to "${imp.message}（拉回 $pulled 张照片）"
                    }
                }
            }
            statusText = outcome.second
            statusOk = outcome.first
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(6.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "远端同步",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // 协议切换
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("webdav" to "WebDAV", "s3" to "S3").forEach { (id, name) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (provider == id) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable { provider = id }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (provider == id) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when (provider) {
                                    "s3" -> if (s3Config.bucket.isNotBlank()) "已配置 S3" else "未配置"
                                    else -> if (syncConfig.endpoint.isNotBlank()) "已配置 WebDAV" else "未配置"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = when (provider) {
                                    "s3" -> if (s3Config.bucket.isNotBlank()) "${s3Config.bucket} @ ${s3Config.region.ifBlank { "默认区域" }}"
                                    else "连接你自己的 S3 / 兼容存储，加密同步资料库"
                                    else -> if (syncConfig.endpoint.isNotBlank()) syncConfig.endpoint
                                    else "连接你自己的 WebDAV，加密同步资料库"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { showConfigDialog = true },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(
                    when (provider) {
                        "s3" -> if (s3Config.bucket.isNotBlank()) "修改 S3 配置" else "配置 S3 服务器"
                        else -> if (syncConfig.endpoint.isNotBlank()) "修改 WebDAV 配置" else "配置 WebDAV 服务器"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        statusText = "正在测试连接…"
                        statusOk = null
                        val r: Pair<Boolean, String> = withContext(Dispatchers.IO) {
                            if (provider == "s3") {
                                val rr = com.catlink.app.data.S3Client.testConnection(
                                    s3Config.endpoint, s3Config.region, s3Config.bucket,
                                    s3Config.accessKey, s3Config.secretKey,
                                )
                                rr.ok to rr.message
                            } else {
                                val rr = WebDavClient.testConnection(syncConfig.endpoint, syncConfig.user, syncConfig.pass)
                                rr.ok to rr.message
                            }
                        }
                        statusText = r.second
                        statusOk = r.first
                    }
                },
                enabled = if (provider == "s3") {
                    s3Config.bucket.isNotBlank() && s3Config.accessKey.isNotBlank()
                } else {
                    syncConfig.endpoint.isNotBlank()
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) { Text("测试连接") }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { action = "upload"; showPasswordDialog = true },
                    enabled = if (provider == "s3") {
                        s3Config.bucket.isNotBlank() && s3Config.accessKey.isNotBlank()
                    } else {
                        syncConfig.endpoint.isNotBlank()
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text("上传备份") }
                OutlinedButton(
                    onClick = { action = "download"; showPasswordDialog = true },
                    enabled = if (provider == "s3") {
                        s3Config.bucket.isNotBlank() && s3Config.accessKey.isNotBlank()
                    } else {
                        syncConfig.endpoint.isNotBlank()
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text("下载恢复") }
            }
        }

        if (statusText.isNotBlank()) {
            item {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (statusOk) {
                        true -> MaterialTheme.colorScheme.secondary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        item {
            Text(
                text = "同步说明：上传的是加密备份包，服务器只看到密文。下载恢复会合并到本机资料库。备份口令请牢记，忘记无法解密。S3 支持 AWS 与 MinIO/OSS 等兼容存储。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showConfigDialog) {
        if (provider == "s3") {
            S3ConfigDialog(
                initial = s3Config,
                onDismiss = { showConfigDialog = false },
                onSave = { bucket, region, accessKey, secretKey, endpoint ->
                    scope.launch {
                        settings.setS3Config(bucket, region, accessKey, secretKey, endpoint)
                        showConfigDialog = false
                    }
                },
            )
        } else {
            SyncConfigDialog(
                initial = syncConfig,
                onDismiss = { showConfigDialog = false },
                onSave = { endpoint, user, pass ->
                    scope.launch {
                        settings.setSyncConfig(endpoint, user, pass)
                        showConfigDialog = false
                    }
                },
            )
        }
    }

    if (showPasswordDialog) {
        SyncPasswordDialog(
            title = if (action == "upload") "上传加密备份" else "下载并恢复备份",
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                showPasswordDialog = false
                runSync(action, password)
            },
        )
    }
}

@Composable
private fun SyncConfigDialog(
    initial: SettingsStore.SyncConfig,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.pass) }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = "服务器地址",
                    placeholder = "https://dav.example.com/dav/catlink/",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = "用户名（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = "密码（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = endpoint.isNotBlank(),
                onClick = { onSave(endpoint, user, pass) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun S3ConfigDialog(
    initial: SettingsStore.S3Config,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var bucket by remember { mutableStateOf(initial.bucket) }
    var region by remember { mutableStateOf(initial.region) }
    var accessKey by remember { mutableStateOf(initial.accessKey) }
    var secretKey by remember { mutableStateOf(initial.secretKey) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("S3 配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatTextField(
                    value = bucket,
                    onValueChange = { bucket = it },
                    label = "存储桶 Bucket",
                    placeholder = "catlink-backup",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = "区域 Region",
                    placeholder = "ap-east-1",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    label = "Access Key",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = "Secret Key",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = "服务地址（可选）",
                    placeholder = "留空用 AWS；MinIO/OSS 兼容填 https://…",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = bucket.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank(),
                onClick = { onSave(bucket, region, accessKey, secretKey, endpoint) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SyncPasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "输入备份口令（创建备份时设置，至少 6 位）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CatTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "备份口令",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 6,
                onClick = { onConfirm(password) },
            ) { Text("开始") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
