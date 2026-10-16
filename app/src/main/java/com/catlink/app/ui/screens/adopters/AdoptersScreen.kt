package com.catlink.app.ui.screens.adopters

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.EmptyState
import com.catlink.app.ui.components.SoftCard
import kotlinx.coroutines.launch

/** 领养人管理：联系方式字段最小化，敏感信息仅本机保存（产品文档 6.4） */
@Composable
fun AdoptersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scope = rememberCoroutineScope()

    var adopters by remember { mutableStateOf<List<AdopterEntity>>(emptyList()) }
    var editing by remember { mutableStateOf<AdopterEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<AdopterEntity?>(null) }

    LaunchedEffect(Unit) {
        repo.observeAdopters().collect { adopters = it }
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
                    text = "领养人",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "添加领养人",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (adopters.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🤝",
                    title = "还没有领养人",
                    subtitle = "联系方式仅保存在本机，不会公开",
                )
            }
        } else {
            items(adopters, key = { it.id }) { adopter ->
                AdopterRow(
                    adopter = adopter,
                    onEdit = { editing = adopter; showDialog = true },
                    onDelete = { confirmDelete = adopter },
                )
            }
        }
    }

    if (showDialog) {
        AdopterDialog(
            existing = editing,
            onDismiss = { showDialog = false; editing = null },
            onSave = { id, name, phone, wechat, qq, email, idCard, address, notes ->
                scope.launch {
                    repo.saveAdopter(id, name, phone, wechat, qq, email, idCard, address, notes)
                    showDialog = false
                    editing = null
                }
            },
        )
    }

    confirmDelete?.let { adopter ->
        CatDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除领养人？") },
            text = { Text("「${adopter.name}」的联系方式将被删除。已关联的历史个案记录会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteAdopter(adopter.id)
                        confirmDelete = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AdopterRow(adopter: AdopterEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = adopter.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adopter.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 联系方式摘要（私密，仅本机展示）
                val contact = buildList {
                    adopter.phone.takeIf { it.isNotBlank() }?.let { add("电话 $it") }
                    adopter.wechat.takeIf { it.isNotBlank() }?.let { add("微信 $it") }
                    adopter.qq.takeIf { it.isNotBlank() }?.let { add("QQ $it") }
                    adopter.email.takeIf { it.isNotBlank() }?.let { add("邮箱 $it") }
                }.joinToString(" · ")
                if (contact.isNotEmpty()) {
                    Text(
                        text = contact,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onEdit)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AdopterDialog(
    existing: AdopterEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String, String, String, String) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var wechat by remember(existing) { mutableStateOf(existing?.wechat ?: "") }
    var qq by remember(existing) { mutableStateOf(existing?.qq ?: "") }
    var email by remember(existing) { mutableStateOf(existing?.email ?: "") }
    var idCard by remember(existing) { mutableStateOf(existing?.idCard ?: "") }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加领养人" else "编辑领养人") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "称呼 / 姓名 *",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "手机号",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = wechat,
                        onValueChange = { wechat = it },
                        label = "微信",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatTextField(
                        value = qq,
                        onValueChange = { qq = it },
                        label = "QQ",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "邮箱",
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.weight(1f),
                    )
                }
                CatTextField(
                    value = idCard,
                    onValueChange = { idCard = it },
                    label = "身份证号（私密）",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "地址",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "以上联系方式仅保存在本机，不会出现在社区或备份明文之外。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(existing?.id, name, phone, wechat, qq, email, idCard, address, notes) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
