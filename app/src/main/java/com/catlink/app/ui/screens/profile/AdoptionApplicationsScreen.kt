package com.catlink.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.PublicApi
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.EmptyState
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.theme.CatSpacing
import com.catlink.app.ui.theme.CatRadii
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val activeAdoptionStatuses = setOf("pending", "contact", "home_visit")

internal fun adoptionStatus(value: String) = mapOf(
    "pending" to "待审核", "contact" to "沟通中", "home_visit" to "家访中",
    "approved" to "已通过", "rejected" to "已拒绝", "withdrawn" to "已撤回",
)[value] ?: value

internal fun adoptionOwnerActions(status: String): List<Pair<String, String>> = when (status) {
    "pending" -> listOf("开始沟通" to "contact", "标记家访" to "home_visit", "通过" to "approved", "拒绝" to "rejected")
    "contact" -> listOf("标记家访" to "home_visit", "通过" to "approved", "拒绝" to "rejected")
    "home_visit" -> listOf("通过" to "approved", "拒绝" to "rejected")
    else -> emptyList()
}

internal fun adoptionMediaLinks(value: String): List<String> = value.split(',', '|').map(String::trim).filter(String::isNotBlank)

@Composable
fun AdoptionApplicationsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as CatLinkApplication
    val api = app.container.publicApi
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PublicApi.AdoptionApplication>>(emptyList()) }
    var selectedRole by remember { mutableStateOf("applicant") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<PublicApi.AdoptionApplication, String>?>(null) }
    val notes = remember { mutableStateMapOf<String, String>() }
    val links = remember { mutableStateMapOf<String, String>() }

    suspend fun load() {
        loading = true
        api.adoptionApplications().fold(
            onSuccess = { applications ->
                rows = applications
                error = null
            },
            onFailure = { error = it.message ?: "领养申请加载失败" },
        )
        loading = false
    }

    fun transition(row: PublicApi.AdoptionApplication, status: String) {
        busyId = row.id
        scope.launch {
            api.updateAdoptionTimeline(row.id, status, notes[row.id].orEmpty().trim(), links[row.id].orEmpty().trim()).fold(
                onSuccess = {
                    notes.remove(row.id)
                    links.remove(row.id)
                    CatNoticeCenter.show("领养申请已更新")
                    load()
                },
                onFailure = { CatNoticeCenter.show(it.message ?: "操作失败", error = true) },
            )
            busyId = null
        }
    }

    LaunchedEffect(Unit) { load() }
    val visibleRows = rows.filter { it.role == selectedRole }

    Column(Modifier.fillMaxSize()) {
        SecondaryTopBar("领养申请", onBack = onBack, modifier = Modifier.padding(horizontal = CatSpacing.pageHorizontal))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CatSpacing.md)
                .clip(RoundedCornerShape(CatRadii.control))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .selectableGroup(),
        ) {
            AdoptionRoleTab("我发起的（${rows.count { it.role == "applicant" }}）", selectedRole == "applicant", { selectedRole = "applicant" }, Modifier.weight(1f))
            AdoptionRoleTab("收到的（${rows.count { it.role == "owner" }}）", selectedRole == "owner", { selectedRole = "owner" }, Modifier.weight(1f))
        }
        LazyColumn(contentPadding = PaddingValues(CatSpacing.md), verticalArrangement = Arrangement.spacedBy(CatSpacing.md)) {
            when {
                loading -> item { Text("正在加载领养申请…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                error != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { load() } }) { Text("重新加载") }
                    }
                }
                visibleRows.isEmpty() -> item {
                    EmptyState(
                        emoji = "🐾",
                        title = if (selectedRole == "owner") "还没有收到申请" else "还没有发起申请",
                        subtitle = if (selectedRole == "owner") "公开待领养猫咪后，申请会显示在这里" else "从待领养猫咪详情提交申请后，可在这里查看进度",
                    )
                }
                else -> items(visibleRows, key = { it.id }) { row ->
                    AdoptionApplicationCard(
                        row = row,
                        timeline = row.timeline,
                        note = notes[row.id].orEmpty(),
                        link = links[row.id].orEmpty(),
                        busy = busyId == row.id,
                        onNoteChange = { notes[row.id] = it },
                        onLinkChange = { links[row.id] = it },
                        onSupplement = { transition(row, row.status) },
                        onAction = { status ->
                            if (status == "approved" || status == "rejected" || status == "withdrawn") pendingAction = row to status
                            else transition(row, status)
                        },
                    )
                }
            }
        }
    }

    pendingAction?.let { (row, status) ->
        CatDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("确认${adoptionStatus(status)}？") },
            text = { Text("猫咪「${row.catNickname.ifBlank { "#${row.catId}" }}」的申请状态将更新为${adoptionStatus(status)}。") },
            confirmButton = { TextButton(onClick = { pendingAction = null; transition(row, status) }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AdoptionRoleTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AdoptionApplicationCard(
    row: PublicApi.AdoptionApplication,
    timeline: List<PublicApi.AdoptionEvent>,
    note: String,
    link: String,
    busy: Boolean,
    onNoteChange: (String) -> Unit,
    onLinkChange: (String) -> Unit,
    onSupplement: () -> Unit,
    onAction: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.catNickname.ifBlank { "猫咪 #${row.catId}" }, style = MaterialTheme.typography.titleMedium)
            Text(adoptionStatus(row.status), color = MaterialTheme.colorScheme.primary)
        }
        Text(if (row.role == "owner") "申请人：${row.applicantName.ifBlank { "用户 #${row.applicantId}" }}" else "发布者：${row.ownerName.ifBlank { "用户 #${row.ownerId}" }}", style = MaterialTheme.typography.bodySmall)
        if (row.message.isNotBlank()) Text(row.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.contactSummary.isNotBlank()) Text("联系方式：${row.contactSummary}", style = MaterialTheme.typography.bodySmall)
        timeline.forEach { event ->
            Text(
                "${formatAdoptionTime(event.createdAt)} · ${event.actorName.ifBlank { "系统" }} · ${adoptionStatus(event.status)}${event.note.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val mediaLinks = adoptionMediaLinks(event.mediaUrls)
            if (mediaLinks.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                    mediaLinks.forEachIndexed { index, url ->
                        TextButton(onClick = { uriHandler.openUri(url) }) { Text("查看材料 ${index + 1}") }
                    }
                }
            }
        }
        if (row.status in activeAdoptionStatuses) {
            CatTextField(value = note, onValueChange = onNoteChange, label = "补充说明", placeholder = "沟通情况、居住环境或养宠经验", singleLine = false, minLines = 2)
            CatTextField(value = link, onValueChange = onLinkChange, label = "材料链接", placeholder = "可选，填写 HTTPS 地址")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                TextButton(enabled = !busy && (note.isNotBlank() || link.isNotBlank()), onClick = onSupplement) { Text("补充材料") }
                if (row.role == "applicant") {
                    TextButton(enabled = !busy, onClick = { onAction("withdrawn") }) { Text("撤回") }
                }
            }
            if (row.role == "owner") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                    adoptionOwnerActions(row.status).forEach { (label, status) ->
                        TextButton(enabled = !busy, onClick = { onAction(status) }) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun formatAdoptionTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
