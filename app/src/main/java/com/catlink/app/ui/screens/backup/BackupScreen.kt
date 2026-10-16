package com.catlink.app.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.BackupManager
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SoftCard
import kotlinx.coroutines.launch

/** 加密备份：导出到用户选择的位置，导入前先预检口令（产品文档 12.2） */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("export") } // export / import
    var message by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            mode = "export"
            pendingUri = uri
            showPasswordDialog = true
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mode = "import"
            pendingUri = uri
            showPasswordDialog = true
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
                    text = "备份与恢复",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "加密备份包",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "备份包含全部猫咪、健康、领养人与合同资料，使用 AES-256 加密。同步会传播变更，备份是可回溯的副本——两者不能互相替代。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ActionCard(
                icon = { Icon(Icons.Outlined.FileDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = "导出备份",
                subtitle = "选择保存位置并设置口令",
                onClick = { exportLauncher.launch("catlink-backup-${System.currentTimeMillis()}.clb") },
            )
        }
        item {
            ActionCard(
                icon = { Icon(Icons.Outlined.FileUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = "从备份恢复",
                subtitle = "导入前先校验口令，失败不写入当前资料库",
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        message?.let {
            item {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("成功") || it.startsWith("恢复")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "备份口令不会保存在设备上，请务必牢记；忘记口令将无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            title = if (mode == "export") "设置备份口令" else "输入备份口令",
            onDismiss = { showPasswordDialog = false; pendingUri = null },
            onConfirm = { password ->
                val uri = pendingUri ?: return@PasswordDialog
                scope.launch {
                    val result = if (mode == "export") {
                        BackupManager.export(context, app.container.dao, uri, password)
                    } else {
                        BackupManager.import(context, app.container.database, uri, password)
                    }
                    message = result.message + if (result.imported > 0) "（${result.imported} 条档案）" else ""
                    showPasswordDialog = false
                    pendingUri = null
                }
            },
        )
    }
}

@Composable
private fun ActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            CatTextField(
                value = password,
                onValueChange = { password = it },
                label = "口令（至少 6 位）",
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 6,
                onClick = { onConfirm(password) },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
