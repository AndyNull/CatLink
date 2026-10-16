package com.catlink.app.ui.screens.trash

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.catlink.app.data.db.CatEntity
import com.catlink.app.ui.components.CatAvatar
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.EmptyState
import com.catlink.app.ui.components.SoftCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val trashFmt = DateTimeFormatter.ofPattern("M月d日")

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scope = rememberCoroutineScope()

    var trash by remember { mutableStateOf<List<CatEntity>>(emptyList()) }
    var confirmPurge by remember { mutableStateOf<CatEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.observeTrash().collect { trash = it }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    text = "回收站",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "删除的档案保留 30 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trash.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🗑️",
                    title = "回收站是空的",
                    subtitle = "删除的档案会先来到这里，30 天内可恢复",
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { repo.restoreAllFromTrash() } },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("全部恢复", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { showClearAllDialog = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("清空回收站", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(trash, key = { it.id }) { cat ->
                TrashRow(
                    cat = cat,
                    onRestore = { scope.launch { repo.restoreCat(cat.id) } },
                    onPurge = { confirmPurge = cat },
                )
            }
        }
    }

    if (showClearAllDialog) {
        CatDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空回收站？") },
            text = { Text("将彻底删除 ${trash.size} 只猫咪的全部档案（含健康、领养、合同、回访记录），不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.purgeAllTrash()
                        showClearAllDialog = false
                    }
                }) { Text("彻底删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("取消") }
            },
        )
    }

    confirmPurge?.let { cat ->
        CatDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("永久删除？") },
            text = {
                Text("「${cat.nickname}」及其关联的健康记录、领养个案、合同与回访将被永久删除，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.purgeCat(cat.id)
                        confirmPurge = null
                    }
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TrashRow(cat: CatEntity, onRestore: () -> Unit, onPurge: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatAvatar(index = cat.coverColorIndex, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cat.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                cat.deletedAt?.let {
                    Text(
                        text = "删除于 ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(trashFmt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRestore)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = "恢复",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPurge)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = "永久删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
