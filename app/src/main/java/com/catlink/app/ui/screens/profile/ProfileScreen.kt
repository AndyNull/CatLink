package com.catlink.app.ui.screens.profile

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.SettingsStore
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.components.StatusChip
import com.catlink.app.ui.theme.CatLinkThemeColors
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    vm: com.catlink.app.ui.MainViewModel,
    onOpenTrash: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAdopters: () -> Unit,
    onOpenPoster: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenExport: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val settings = app.container.settings
    val scope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    val nickname by settings.nickname.collectAsState(initial = "")

    // 保留滚动位置（返回时不再回弹到顶部）
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 个人卡片
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (nickname.isBlank()) "救助人" else nickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { showNicknameDialog = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "修改昵称",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Text(
                            text = "本地私密资料库 · 仅本机 · 账号待接入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 数据
        item {
            SettingsGroup(title = "数据") {
                FeatureRow(Icons.Outlined.Restore, "备份与恢复", onOpenBackup)
                FeatureRow(Icons.Outlined.DeleteSweep, "回收站", onOpenTrash)
                FeatureRow(Icons.Outlined.Groups, "领养人管理", onOpenAdopters)
                FeatureRow(Icons.Outlined.IosShare, "鼓励海报", onOpenPoster)
            }
        }

        // 同步与安全
        item {
            SettingsGroup(title = "AI 与同步") {
                FeatureRow(Icons.Outlined.SmartToy, "AI 对话设置", onOpenAiSettings, badge = "DeepSeek")
                FeatureRow(Icons.Outlined.CloudSync, "远端同步", onOpenSync, badge = "WebDAV")
            }
        }

        // 外观
        item {
            SettingsGroup(title = "外观") {
                FeatureRow(Icons.Outlined.Palette, "主题", onOpenTheme)
            }
        }

        // 其他
        item {
            SettingsGroup(title = "其他") {
                FeatureRow(Icons.Outlined.FileDownload, "导出数据", onOpenExport, badge = "可移植 ZIP")
                FeatureRow(Icons.Outlined.HelpOutline, "帮助与隐私说明", onOpenAbout)
                FeatureRow(Icons.Outlined.DeleteSweep, "删除本地资料库", { showClearDialog = true })
            }
        }

        item {
            Text(
                text = "CatLink v0.8.8 · 本地优先，同步不等于备份",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showClearDialog) {
        CatDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("删除本地资料库？") },
            text = {
                Text(
                    "将永久删除本机全部档案（猫咪、健康、领养人、合同、回访、待办、照片引用）。" +
                        "若曾导出加密备份，可稍后从备份恢复。此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.container.database.clearAllTables()
                        showClearDialog = false
                    }
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
    if (showNicknameDialog) {
        NicknameDialog(
            initial = nickname,
            onDismiss = { showNicknameDialog = false },
            onSave = { name ->
                scope.launch {
                    settings.setNickname(name)
                    showNicknameDialog = false
                }
            },
        )
    }
}

@Composable
private fun NicknameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称") },
        text = {
            CatTextField(
                value = name,
                onValueChange = { name = it },
                label = "昵称（显示在个人卡片）",
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, onClick: () -> Unit, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            StatusChip(
                text = badge,
                container = if (badge == "开") CatLinkThemeColors.extras.mint else MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}


