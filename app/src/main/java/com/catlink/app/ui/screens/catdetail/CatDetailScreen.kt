package com.catlink.app.ui.screens.catdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VolunteerActivism
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.AlbumSyncState
import com.catlink.app.data.CatAlbumManager
import com.catlink.app.data.CatStatus
import com.catlink.app.data.ArchiveReason
import com.catlink.app.data.albumDisplayUri
import com.catlink.app.data.nextStates
import com.catlink.app.data.sortedAlbumMedia
import com.catlink.app.data.EventKind
import com.catlink.app.data.MedicalCategory
import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.MediaEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.data.db.TimelineEntity
import com.catlink.app.ui.components.CatAvatar
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.catStatusColors
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatDateField
import com.catlink.app.ui.components.CatIconButton
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SectionHeader
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.components.StatusChip
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSizes
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val dateFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val shortFmt = DateTimeFormatter.ofPattern("M月d日")
private const val MAX_ALBUM_PHOTOS = 30

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CatDetailScreen(
    catId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenSharePreview: (String) -> Unit = {},
    onOpenVersions: () -> Unit = {},
    onOpenRelations: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val repo = app.container.repository
    val scope = rememberCoroutineScope()
    val albumManager = remember(app, repo) {
        CatAlbumManager(context, repo, app.container.publicApi, app.container.authSession::isSignedIn)
    }

    var cat by remember { mutableStateOf<CatEntity?>(null) }
    var medicals by remember { mutableStateOf<List<MedicalEntity>>(emptyList()) }
    var cases by remember { mutableStateOf<List<CaseEntity>>(emptyList()) }
    var timeline by remember { mutableStateOf<List<TimelineEntity>>(emptyList()) }
    var media by remember { mutableStateOf<List<MediaEntity>>(emptyList()) }
    var showMedicalDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewCaseDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var activeCaseId by remember { mutableStateOf<String?>(null) }
    var deletingMedical by remember { mutableStateOf<MedicalEntity?>(null) }
    var deletingMedia by remember { mutableStateOf<MediaEntity?>(null) }
    var previewingMedia by remember { mutableStateOf<MediaEntity?>(null) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var totalCost by remember { mutableStateOf(0.0) }
    var versionCount by remember { mutableStateOf(0) }
    var relationCount by remember { mutableStateOf(0) }
    var albumBusy by remember { mutableStateOf(false) }
    var newestMediaFirst by remember { mutableStateOf(true) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val accepted = uris.take((MAX_ALBUM_PHOTOS - media.size).coerceAtLeast(0))
        if (accepted.isEmpty()) {
            com.catlink.app.ui.components.CatNoticeCenter.show("每只猫最多保存 $MAX_ALBUM_PHOTOS 张相册照片", error = true)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            albumBusy = true
            val result = albumManager.import(catId, accepted)
            albumBusy = false
            val message = when {
                result.failed > 0 -> "已导入 ${result.imported} 张，${result.failed} 张同步失败，可点击重试"
                app.container.authSession.isSignedIn() -> "已添加并同步 ${result.synced} 张照片"
                else -> "已添加 ${result.imported} 张照片，登录后自动同步"
            }
            com.catlink.app.ui.components.CatNoticeCenter.show(message, error = result.failed > 0)
        }
    }

    LaunchedEffect(catId) {
        launch { repo.observeCat(catId).collect { cat = it } }
        launch { repo.observeMedicals(catId).collect { medicals = it } }
        launch { repo.observeCases(catId).collect { cases = it } }
        launch { repo.observeTimeline(catId).collect { timeline = it } }
        launch { repo.observeMedia(catId).collect { media = it } }
        totalCost = repo.totalMedicalCost(catId)
        versionCount = repo.catVersions(catId).size
        relationCount = repo.catRelations(catId).size
        if (app.container.authSession.isSignedIn()) {
            val pending = repo.catMedia(catId).filter { it.syncState != AlbumSyncState.UPLOADED.value }
            if (pending.isNotEmpty()) {
                albumBusy = true
                albumManager.syncPending(pending)
                albumBusy = false
            }
        }
    }

    if (cat == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val c = cat!!
    val current = cases
        .filter {
            it.adopterId.isNotBlank() &&
                it.stage !in setOf(com.catlink.app.data.CaseStage.REJECTED.name, com.catlink.app.data.CaseStage.WITHDRAWN.name)
        }
        .maxByOrNull { it.updatedAt }
    val activeAdoptionCases = listOfNotNull(current)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            DetailTopBar(
                onBack = onBack,
                onEdit = onEdit,
                onDelete = { showDeleteDialog = true },
                onShare = { showShareDialog = true },
            )
        }
        item { HeroCard(cat = c, onStatusClick = { showStatusDialog = true }) }


        // 相册
        item {
            SectionHeader(title = "相册 · ${media.size}/$MAX_ALBUM_PHOTOS", actionText = if (albumBusy) "处理中…" else "批量添加", onAction = {
                if (albumBusy) return@SectionHeader
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        if (media.isEmpty()) {
            item {
                Text(
                    text = "还没有照片，可从系统相册一次选择多张；登录后会同步为服务端媒体。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val failedCount = media.count { it.syncState == AlbumSyncState.FAILED.value }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { newestMediaFirst = true }) { Text(if (newestMediaFirst) "最新优先 ✓" else "最新优先") }
                        TextButton(onClick = { newestMediaFirst = false }) { Text(if (!newestMediaFirst) "最早优先 ✓" else "最早优先") }
                        Spacer(Modifier.weight(1f))
                        if (failedCount > 0) {
                            TextButton(
                                enabled = !albumBusy,
                                onClick = {
                                    scope.launch {
                                        albumBusy = true
                                        val result = albumManager.syncPending(media.filter { it.syncState == AlbumSyncState.FAILED.value })
                                        albumBusy = false
                                        com.catlink.app.ui.components.CatNoticeCenter.show(
                                            if (result.failed == 0) "相册同步完成" else "仍有 ${result.failed} 张同步失败",
                                            error = result.failed > 0,
                                        )
                                    }
                                },
                            ) { Text("重试 $failedCount 张") }
                        }
                    }
                    Text("点击查看，右上角 × 或长按删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val displayed = sortedAlbumMedia(media, newestMediaFirst)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(displayed, key = MediaEntity::id) { item ->
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(albumDisplayUri(item)).size(384).crossfade(false).build(),
                                    contentDescription = "猫咪照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .combinedClickable(
                                            onClick = { previewingMedia = item },
                                            onLongClick = { deletingMedia = item },
                                        ),
                                )
                                if (item.syncState != AlbumSyncState.UPLOADED.value) {
                                    Text(
                                        text = if (item.syncState == AlbumSyncState.FAILED.value) "同步失败" else "仅本机",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f), RoundedCornerShape(topEnd = 8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                // 删除按钮（右上角 ×）
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                                        .clickable { deletingMedia = item },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        "删除照片",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 健康记录
        item {
            SectionHeader(
                title = if (totalCost > 0) "健康记录 · 累计 ¥${if (totalCost % 1.0 == 0.0) totalCost.toInt().toString() else totalCost.toString()}" else "健康记录",
                actionText = "添加",
                onAction = { showMedicalDialog = true },
            )
        }
        if (medicals.isEmpty()) {
            item {
                Text(
                    text = "还没有健康记录，点「添加」记录疫苗、驱虫或就诊。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(medicals.size) { index ->
                MedicalRow(
                    medical = medicals[index],
                    onDelete = { deletingMedical = medicals[index] },
                )
            }
        }

        // 档案版本 + 亲属关系
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EntryCard(
                    label = "档案版本",
                    value = if (versionCount > 0) "$versionCount 个历史版本" else "暂无历史版本",
                    onClick = onOpenVersions,
                    modifier = Modifier.weight(1f),
                )
                EntryCard(
                    label = "亲属关系",
                    value = if (relationCount > 0) "$relationCount 条关系" else "添加关系",
                    onClick = onOpenRelations,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 领养个案
        item {
            SectionHeader(
                title = "领养档案",
                actionText = if (activeAdoptionCases.isEmpty()) "新建" else null,
                onAction = {
                    scope.launch {
                        val active = repo.activeCaseForCat(catId)
                        if (active != null) activeCaseId = active.id else showNewCaseDialog = true
                    }
                },
            )
        }
        if (activeAdoptionCases.isEmpty()) {
            item {
                Text(
                    text = "尚未关联领养人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(activeAdoptionCases.size) { index ->
                CaseRow(case = activeAdoptionCases[index], onClick = { onOpenCase(activeAdoptionCases[index].id) })
            }
        }

        // 故事
        if (c.story.isNotBlank()) {
            item { StoryCard(cat = c) }
        }

        // 时间线
        item { SectionTitle("救助时间线") }
        items(timeline.size) { index ->
            TimelineRow(event = timeline[index], isLast = index == timeline.lastIndex)
        }
    }

    previewingMedia?.let { item ->
        CatDialog(
            onDismissRequest = { previewingMedia = null },
            title = { Text("照片预览") },
            text = {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(albumDisplayUri(item)).size(1080).crossfade(false).build(),
                    contentDescription = "猫咪照片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 520.dp)
                        .clip(RoundedCornerShape(CatRadii.card)),
                )
            },
            confirmButton = {
                TextButton(onClick = { previewingMedia = null }) { Text("关闭") }
            },
        )
    }

    if (showMedicalDialog) {
        AddMedicalDialog(catId = catId, onDismiss = { showMedicalDialog = false })
    }
    if (showPublishDialog) {
        cat?.let { c ->
            com.catlink.app.ui.components.PublicPublishDialog(
                initial = (context.applicationContext as CatLinkApplication).container.publicDrafts.draftFromCat(c),
                onDismiss = { showPublishDialog = false },
                onPublished = { showPublishDialog = false },
            )
        }
    }
    if (showShareDialog) {
        cat?.let { c ->
            com.catlink.app.ui.components.ShareCatDialog(
                cat = c,
                onDismiss = { showShareDialog = false },
                onPublish = {
                    showShareDialog = false
                    showPublishDialog = true
                },
                onPreview = { code ->
                    showShareDialog = false
                    onOpenSharePreview(code)
                },
            )
        }
    }
    if (showNewCaseDialog) {
        NewCaseDialog(
            catId = catId,
            onDismiss = { showNewCaseDialog = false },
            onCreated = { caseId ->
                showNewCaseDialog = false
                onOpenCase(caseId)
            },
        )
    }
    activeCaseId?.let { existingId ->
        CatDialog(
            onDismissRequest = { activeCaseId = null },
            title = { Text("已存在领养档案") },
            text = { Text("一只猫咪只保留一个当前领养人关联。是否查看现有档案？") },
            confirmButton = {
                TextButton(onClick = {
                    onOpenCase(existingId)
                    activeCaseId = null
                }) { Text("查看领养档案") }
            },
            dismissButton = {
                TextButton(onClick = { activeCaseId = null }) { Text("取消") }
            },
        )
    }
    if (showStatusDialog) {
        StatusChangeDialog(
            current = CatStatus.fromName(c.status),
            currentArchiveReason = ArchiveReason.fromNameOrNull(c.archiveReason),
            onDismiss = { showStatusDialog = false },
            onConfirm = { target, reason, archiveReason, date ->
                scope.launch {
                    repo.changeCatStatus(
                        id = catId,
                        status = target,
                        reason = reason,
                        archiveReason = archiveReason,
                        date = date,
                    ).onSuccess {
                        showStatusDialog = false
                    }.onFailure {
                        com.catlink.app.ui.components.CatNoticeCenter.show(it.message ?: "状态变更失败", error = true)
                    }
                }
            },
        )
    }
    if (showDeleteDialog) {
        CatDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("移入回收站？") },
            text = { Text("「${c.nickname}」的档案将移入回收站，30 天内可恢复。关联的媒体与记录不会被立即删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.softDeleteCat(catId)
                        showDeleteDialog = false
                        onBack()
                    }
                }) { Text("移入回收站", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
    deletingMedia?.let { m ->
        CatDialog(
            onDismissRequest = { deletingMedia = null },
            title = { Text("删除这张照片？") },
            text = { Text("照片将从猫咪档案中移除。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        albumBusy = true
                        albumManager.delete(m)
                            .onSuccess { deletingMedia = null }
                            .onFailure {
                                com.catlink.app.ui.components.CatNoticeCenter.show(
                                    it.message ?: "照片删除失败；若已公开，请先从公开档案移除",
                                    error = true,
                                )
                            }
                        albumBusy = false
                    }
                }, enabled = !albumBusy) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingMedia = null }) { Text("取消") }
            },
        )
    }
    deletingMedical?.let { m ->
        CatDialog(
            onDismissRequest = { deletingMedical = null },
            title = { Text("删除这条健康记录？") },
            text = { Text("「${MedicalCategory.fromName(m.category).label} · ${m.title}」将被永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.removeMedical(m)
                        deletingMedical = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMedical = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    SecondaryTopBar(
        title = "猫咪档案",
        onBack = onBack,
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatIconButton(Icons.Outlined.IosShare, "公开分享", onShare)
                CatIconButton(Icons.Outlined.Edit, "编辑", onEdit)
                CatIconButton(Icons.Outlined.DeleteOutline, "删除", onDelete)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(cat: CatEntity, onStatusClick: () -> Unit = {}) {
    Column {
        CatAvatar(
            index = cat.coverColorIndex,
            coverUri = cat.coverUri.ifBlank { null },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = cat.nickname,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusChipFor(cat, onClick = onStatusClick)
        }
        if (CatStatus.fromName(cat.status) == CatStatus.ARCHIVED) {
            ArchiveReason.fromNameOrNull(cat.archiveReason)?.let { archive ->
                Spacer(Modifier.height(4.dp))
                Text("归档原因：${archive.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (cat.alias.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = cat.alias,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        // 一行 4 个紧凑标签（性别/年龄/体重/毛色）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoPill(Icons.Outlined.Favorite, cat.gender.ifBlank { "未知" }, Modifier.weight(1f))
            InfoPill(Icons.Outlined.Schedule, cat.ageEstimate.ifBlank { "未知" }, Modifier.weight(1f))
            InfoPill(Icons.Outlined.MonitorHeart, cat.weight.ifBlank { "体重" }, Modifier.weight(1f))
            InfoPill(Icons.Outlined.Palette, cat.coat.ifBlank { "毛色" }, Modifier.weight(1f))
        }
        val visibleTags = cat.tags.split(",").filter { it.isNotBlank() }
        if (visibleTags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleTags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CatRadii.pill))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CatLinkThemeColors.extras.onMint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 状态流转（产品文档 7.1）：只显示合法转移目标；ADOPTED 由个案流程驱动不可手动设置 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusFlowRow(current: CatStatus, currentArchiveReason: ArchiveReason?, onChange: (CatStatus) -> Unit) {
    val available = current.nextStates(currentArchiveReason)
    if (available.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    // 紧凑单行：当前状态 → 可流转目标（小胶囊，不占大板块）
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(current.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text("›", style = MaterialTheme.typography.labelMedium, color = scheme.outline)
        available.forEach { s ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CatRadii.pill))
                    .background(scheme.primaryContainer.copy(alpha = 0.45f))
                    .clickable { onChange(s) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(s.label, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
        }
    }
}

@Composable
private fun StatusSelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CatRadii.pill)
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                CatSpacing.hairline,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MedicalRow(medical: MedicalEntity, onDelete: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CatLinkThemeColors.extras.mint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalHospital,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${MedicalCategory.fromName(medical.category).label} · ${medical.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (medical.detail.isNotBlank()) {
                    Text(
                        text = medical.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val meta = buildList {
                    if (medical.hospital.isNotBlank()) add(medical.hospital)
                    if (medical.weight.isNotBlank()) add(if (medical.weight.endsWith("kg", ignoreCase = true)) "称重 ${medical.weight}" else "称重 ${medical.weight}kg")
                    if (medical.cost.isNotBlank()) add("¥${medical.cost}")
                    if (medical.behaviorNote.isNotBlank()) add("性格：${medical.behaviorNote}")
                    if (medical.result.isNotBlank()) add("结果：${medical.result}")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
            Text(
                text = LocalDate.ofEpochDay(medical.dateEpochDay).format(shortFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CatIconButton(
                icon = Icons.Outlined.DeleteOutline,
                contentDescription = "删除记录",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CaseRow(case: CaseEntity, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "领养档案",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = case.source.ifBlank { "来源未记录" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(
                text = "已领养",
                container = CatLinkThemeColors.extras.mint,
                content = CatLinkThemeColors.extras.onMint,
            )
        }
    }
}

@Composable
private fun StoryCard(cat: CatEntity) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "救助故事",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = cat.story,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "发现于 ${LocalDate.ofEpochDay(cat.rescuedDateEpochDay).format(dateFmt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TimelineRow(event: TimelineEntity, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(kindColor(EventKind.fromName(event.kind))),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(70.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SoftCard(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 2.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = LocalDate.ofEpochDay(event.dateEpochDay).format(shortFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.detail.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = event.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun kindColor(kind: EventKind) = when (kind) {
    EventKind.RESCUE -> MaterialTheme.colorScheme.secondary
    EventKind.HEALTH -> CatLinkThemeColors.extras.onMint
    EventKind.ADOPTION -> MaterialTheme.colorScheme.primary
    EventKind.FOLLOW_UP -> MaterialTheme.colorScheme.tertiary
    EventKind.STATUS -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusChipFor(cat: CatEntity, onClick: () -> Unit = {}) {
    val status = CatStatus.fromName(cat.status)
    val (container, content) = catStatusColors(status)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // 可点击 chip：增大触摸区域确保点击有效
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp, max = 40.dp)
            .clip(RoundedCornerShape(CatRadii.pill))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val archive = ArchiveReason.fromNameOrNull(cat.archiveReason)
        StatusChip(
            text = if (status == CatStatus.ARCHIVED && archive != null) "${status.label} · ${archive.label}" else status.label,
            container = container,
            content = content,
        )
    }
}

/** 添加健康记录对话框 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMedicalDialog(catId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val repo = app.container.repository
    val scope = rememberCoroutineScope()
    val albumManager = remember(app, repo) {
        CatAlbumManager(context, repo, app.container.publicApi, app.container.authSession::isSignedIn)
    }

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MedicalCategory.VACCINE) }
    var detail by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hospital by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var behaviorNote by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var showMoreCategories by remember { mutableStateOf(false) }
    var attachedPhotos by remember { mutableStateOf(0) }
    val pickMedicalPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = albumManager.import(catId, listOf(uri))
                if (result.imported > 0) attachedPhotos++
                com.catlink.app.ui.components.CatNoticeCenter.show(
                    if (result.failed == 0) "照片已添加到档案相册" else "照片已保存在本机，稍后可重试同步",
                    error = result.failed > 0,
                )
            }
        }
    }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加健康记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 常用分类平铺（绝育/疫苗/驱虫/体况），其余放“更多”内联展开
                val common = listOf(
                    MedicalCategory.SPAY, MedicalCategory.VACCINE,
                    MedicalCategory.DEWORM, MedicalCategory.CHECKUP,
                )
                val others = MedicalCategory.entries.filter { it !in common }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    common.forEach { c ->
                        StatusSelectChip(
                            label = c.label,
                            selected = category == c,
                            onClick = { category = c },
                        )
                    }
                    // 更多分类：统一悬浮下拉（DropdownMenu）
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.control))
                                .clickable { showMoreCategories = true }
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(
                                    androidx.compose.foundation.BorderStroke(CatSpacing.hairline, MaterialTheme.colorScheme.outlineVariant),
                                    RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.control),
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Text("更多 ▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        com.catlink.app.ui.components.CatDropdown(
                            expanded = showMoreCategories,
                            onDismissRequest = { showMoreCategories = false },
                            items = others.map { c ->
                                com.catlink.app.ui.components.CatDropdownItem(
                                    label = c.label,
                                    selected = category == c,
                                    onClick = { category = c },
                                )
                            },
                        )
                    }
                }
                CatDateField(
                    value = date,
                    onDateChange = { date = it },
                    label = "日期",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题 *",
                    placeholder = "如：猫三联第一针",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = "详情（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatTextField(
                        value = hospital,
                        onValueChange = { hospital = it },
                        label = "医院（可选）",
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = "金额 ¥（可选）",
                        placeholder = "如 350",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = "称重 kg（可选）",
                        placeholder = "如 3.2",
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = behaviorNote,
                        onValueChange = { behaviorNote = it },
                        label = "性格/行为变化（可选）",
                        placeholder = "如：绝育后变粘人",
                        modifier = Modifier.weight(1f),
                    )
                }
                CatTextField(
                    value = result,
                    onValueChange = { result = it },
                    label = "结果/恢复情况（可选）",
                    placeholder = "如：恢复良好，已出院",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatButton(
                        text = if (attachedPhotos > 0) "已添加 $attachedPhotos 张照片" else "添加照片",
                        onClick = {
                            pickMedicalPhoto.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        style = com.catlink.app.ui.components.CatButtonStyle.Secondary,
                        height = 36.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "照片会加入档案相册",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    scope.launch {
                        repo.addMedical(
                            catId = catId,
                            date = date,
                            category = category,
                            title = title,
                            detail = detail,
                            hospital = hospital,
                            cost = cost,
                            nextReminder = null,
                            weight = weight,
                            behaviorNote = behaviorNote,
                            result = result,
                        )
                        onDismiss()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 状态变更：记录原因（可选）与发生日期（产品文档 6.1 状态原因） */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatusChangeDialog(
    current: CatStatus,
    currentArchiveReason: ArchiveReason?,
    onDismiss: () -> Unit,
    onConfirm: (CatStatus, String, ArchiveReason?, LocalDate) -> Unit,
) {
    val targets = remember(current, currentArchiveReason) { current.nextStates(currentArchiveReason) }
    var target by remember(current, currentArchiveReason) { mutableStateOf(targets.firstOrNull() ?: current) }
    var reason by remember { mutableStateOf("") }
    var archiveReason by remember { mutableStateOf<ArchiveReason?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改猫咪状态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 当前状态高亮（与档案页显示一致）
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前状态：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChipFor(
                        cat = com.catlink.app.data.db.CatEntity(
                            id = "",
                            nickname = "",
                            status = current.code,
                            healthStatus = "OK",
                            rescuedDateEpochDay = 0L,
                            archiveReason = currentArchiveReason?.code.orEmpty(),
                        )
                    )
                }
                Text("选择目标状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CatStatus.entries.filter { it != CatStatus.ADOPTED }.forEach { s ->
                        val direct = s in targets
                        Box {
                            StatusSelectChip(
                                label = s.label,
                                selected = target == s,
                                onClick = { if (direct) target = s },
                            )
                            if (!direct) {
                                // 非法直接流转：灰显，点击提示
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.pill))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f))
                                        .clickable { com.catlink.app.ui.components.CatNoticeCenter.show("不能直接从「${current.label}」变更为「${s.label}」", error = true) },
                                )
                            }
                        }
                    }
                }
                if (targets.isEmpty()) {
                    Text("离世归档为终态，不能恢复或变更。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (target == CatStatus.ARCHIVED) {
                    Text("归档原因 *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArchiveReason.entries.forEach { item ->
                            StatusSelectChip(
                                label = item.label,
                                selected = archiveReason == item,
                                onClick = { archiveReason = item },
                            )
                        }
                    }
                }
                CatTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "原因（可选）",
                    placeholder = "如：已恢复健康，开放领养",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatDateField(
                    value = date,
                    onDateChange = { date = it },
                    label = "发生日期",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "状态变更会写入时间线，不可删除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = target in targets && (target != CatStatus.ARCHIVED || archiveReason != null),
                onClick = { onConfirm(target, reason.trim(), archiveReason, date) },
            ) { Text("确认变更") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 新建领养档案：主弹窗仅显示摘要；选择和完整新增分别使用独立弹窗，不挤压页面布局。 */
@Composable
private fun NewCaseDialog(catId: String, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scope = rememberCoroutineScope()
    var adopters by remember { mutableStateOf<List<AdopterEntity>>(emptyList()) }
    var selectedAdopterId by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("main") }

    LaunchedEffect(Unit) { repo.observeAdopters().collect { adopters = it } }
    val selected = adopters.firstOrNull { it.id == selectedAdopterId }

    when (mode) {
        "pick" -> AdopterPickerDialog(
            adopters = adopters,
            selectedId = selectedAdopterId,
            onDismiss = { mode = "main" },
            onSelect = { selectedAdopterId = it; mode = "main" },
            onAdd = { mode = "add" },
        )
        "add" -> com.catlink.app.ui.components.AdopterFormDialog(
            onDismiss = { mode = "main" },
            onSave = { draft ->
                scope.launch {
                    val id = UUID.randomUUID().toString()
                    repo.saveAdopter(
                        id, draft.name, draft.phone, draft.wechat, draft.qq, draft.email,
                        draft.idCard, draft.address, draft.notes, draft.backupContact,
                        draft.preferredContact, draft.residence, draft.petExperience,
                        draft.familyMembers, draft.contactAuthorized,
                    )
                    selectedAdopterId = id
                    mode = "main"
                }
            },
        )
        else -> CatDialog(
            onDismissRequest = onDismiss,
            title = { Text("建立领养档案") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "关联领养人后，猫咪会立即标记为已领养。删除领养档案后恢复待领养。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SoftCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { mode = "pick" },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(selected?.name?.take(1) ?: "+", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    selected?.name ?: "选择或新增领养人",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    selected?.let { listOf(it.phone, it.wechat).firstOrNull(String::isNotBlank) ?: "已保存完整资料" }
                                        ?: "支持搜索已有领养人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    CatTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = "领养来源（可选）",
                        placeholder = "如：救助群推荐",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedAdopterId.isNotBlank(),
                    onClick = {
                        scope.launch {
                            onCreated(repo.createCase(catId, selectedAdopterId, source, ""))
                        }
                    },
                ) { Text("确认领养") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
    }
}

@Composable
private fun AdopterPickerDialog(
    adopters: List<AdopterEntity>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = adopters.filter {
        query.isBlank() || listOf(it.name, it.phone, it.wechat, it.email).any { field -> field.contains(query, ignoreCase = true) }
    }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择领养人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CatTextField(query, { query = it }, label = "搜索", placeholder = "姓名、手机、微信或邮箱", modifier = Modifier.fillMaxWidth())
                CatButton(
                    text = "新增完整领养人资料",
                    onClick = onAdd,
                    style = com.catlink.app.ui.components.CatButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text("没有匹配的领养人", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = { it.id }) { adopter ->
                            val chosen = adopter.id == selectedId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { onSelect(adopter.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(adopter.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                    val contact = listOf(adopter.phone, adopter.wechat, adopter.email).firstOrNull(String::isNotBlank).orEmpty()
                                    if (contact.isNotBlank()) Text(contact, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (chosen) Text("已选", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } },
    )
}

/** 档案版本/亲属关系入口卡片 */
@Composable
private fun EntryCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SoftCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
