package com.catlink.app.ui.screens.newcat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.CatRelationType
import com.catlink.app.data.CatStatus
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.HealthStatus
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatDateField
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.theme.CatSizes
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** 建档/编辑：必填封面、基本资料、状态、标签与亲属关系。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCatScreen(catId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val repository = app.container.repository
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var nickname by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var story by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var coat by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var personality by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var coverUri by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(CatStatus.RESCUE.code) }
    var healthStatusName by rememberSaveable { mutableStateOf(HealthStatus.UNKNOWN.name) }
    var rescuedDate by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var selectedRelations by remember { mutableStateOf<List<Pair<String, CatRelationType>>>(emptyList()) }
    var cityName by rememberSaveable { mutableStateOf("") }
    var districtName by rememberSaveable { mutableStateOf("") }
    var showRegionDialog by remember { mutableStateOf(false) }
    var allCats by remember { mutableStateOf<List<CatEntity>>(emptyList()) }
    var showRelationDialog by remember { mutableStateOf(false) }
    var showCoverSourceDialog by remember { mutableStateOf(false) }
    var coverBusy by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf(false) }
    var originalCoverUri by rememberSaveable { mutableStateOf("") }
    var coverCommitted by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf("") }
    var cameraUriString by rememberSaveable { mutableStateOf("") }
    var formReady by remember { mutableStateOf(catId == null) }

    // 编辑模式：载入现有档案
    LaunchedEffect(catId) {
        if (catId != null) {
            val cat: CatEntity? = repository.observeCat(catId).first()
            if (cat != null) {
                nickname = cat.nickname
                alias = cat.alias
                story = cat.story
                cityName = cat.cityName
                districtName = cat.districtName
                gender = cat.gender
                age = cat.ageEstimate
                coat = cat.coat
                weight = cat.weight
                personality = cat.personality
                tags = cat.tags.replace(",", "，")
                coverUri = cat.coverUri
                originalCoverUri = cat.coverUri
                rescuedDate = cat.rescuedDateEpochDay
                statusName = cat.status
                healthStatusName = cat.healthStatus
                editing = true
            }
        }
        // 候选猫（排除自己）与已有关系
        allCats = repository.catsSnapshot().filter { it.id != catId && it.deletedAt == null }
        if (catId != null) {
            selectedRelations = repository.catRelations(catId!!)
                .mapNotNull { r ->
                    if (r.catId == catId) r.relatedCatId to CatRelationType.fromName(r.relationType) else null
                }
        }
        formReady = true
    }

    // 私密档案照片始终先保存到 App 私有目录；公开分享时再上传用户明确选择的照片。
    suspend fun pickToCover(uri: Uri) {
        coverBusy = true
        var stored = ""
        try {
            stored = withContext(Dispatchers.IO) {
                com.catlink.app.data.MediaFiles.copyToPrivate(context, uri.toString())
            }
            val file = File(stored).canonicalFile
            val photos = com.catlink.app.data.MediaFiles.photosDir(context).canonicalFile
            require(file.isFile && file.parentFile == photos) { "图片保存失败" }
            if (coverUri.isNotBlank() && coverUri != originalCoverUri) {
                withContext(Dispatchers.IO) {
                    com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(coverUri))
                }
            }
            coverUri = stored
            coverError = false
        } catch (cancelled: CancellationException) {
            if (stored.isNotBlank() && stored != coverUri && stored != originalCoverUri) {
                com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(stored))
            }
            throw cancelled
        } catch (error: Throwable) {
            if (stored.isNotBlank() && stored != coverUri && stored != originalCoverUri) {
                com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(stored))
            }
            CatNoticeCenter.show(error.message ?: "封面照片处理失败", error = true)
        } finally {
            coverBusy = false
        }
    }

    // 拍照：TakePicture + FileProvider（无需 CAMERA 权限）
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val capture = cameraPath.takeIf(String::isNotBlank)?.let(::File)
        val cameraUri = cameraUriString.takeIf(String::isNotBlank)?.let(Uri::parse)
        if (ok && cameraUri != null) {
            cameraUri.let { uri ->
                scope.launch {
                    try {
                        pickToCover(uri)
                    } finally {
                        withContext(Dispatchers.IO) { capture?.delete() }
                        cameraPath = ""
                        cameraUriString = ""
                    }
                }
            }
        } else {
            capture?.delete()
            cameraPath = ""
            cameraUriString = ""
        }
    }
    // 相册：Photo Picker（系统级，无需存储权限）
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { pickToCover(uri) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (shouldDeleteDraftOnDispose(context.findActivity()?.isChangingConfigurations == true)) {
                cameraPath.takeIf(String::isNotBlank)?.let(::File)?.delete()
                if (!coverCommitted && coverUri.isNotBlank() && coverUri != originalCoverUri) {
                    com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(coverUri))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = CatSpacing.pageHorizontal),
    ) {
        SecondaryTopBar(
            title = if (editing) "编辑档案" else "记录一位新朋友",
            onBack = onBack,
        )

        Spacer(Modifier.height(16.dp))

        // 封面即头像：整块区域是唯一上传/更换入口。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = if (coverError) 1.5.dp else 1.dp,
                    color = if (coverError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable(enabled = formReady && !coverBusy) { showCoverSourceDialog = true },
            contentAlignment = Alignment.Center,
        ) {
            if (coverUri.isNotBlank()) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "封面照片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                )
                Text(
                    text = if (coverBusy) "处理中…" else "更换",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (coverBusy) "处理中…" else "上传封面照片 *",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("封面将同时作为猫咪头像", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (coverError) {
            Spacer(Modifier.height(4.dp))
            Text("请先上传封面照片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "基本信息",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        CatTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = "昵称 *",
            placeholder = "先起个临时名字",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        CatTextField(
            value = alias,
            onValueChange = { alias = it },
            label = "别名（可选）",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "发现与状态",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        CatDateField(
            value = LocalDate.ofEpochDay(rescuedDate),
            onDateChange = { rescuedDate = it.toEpochDay() },
            label = "发现日期 *",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        // 编辑模式只展示当前状态；流转必须在详情页填写日期与原因。
        if (editing) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(CatStatus.fromName(statusName).label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "状态流转请在猫咪详情页完成，以便记录日期、原因和时间线。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(CatStatus.RESCUE, CatStatus.TREATING, CatStatus.ADOPTABLE).forEach { s ->
                    StatusSelectChip(label = s.label, selected = CatStatus.fromName(statusName) == s, onClick = { statusName = s.code })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "外貌与性格",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // 性别：直接可选（统一取值，保证筛选数据一致）
        Text("性别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("弟弟" to "弟弟", "妹妹" to "妹妹", "未知" to "未知").forEach { (value, label) ->
                val selected = gender == value
                Box(
                    modifier = Modifier
                        .clickable { gender = value }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.pill),
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CatTextField(
                value = age,
                onValueChange = { age = it },
                label = "年龄",
                placeholder = "约 6 个月",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CatTextField(
                value = coat,
                onValueChange = { coat = it },
                label = "毛色",
                placeholder = "橘白 · 短毛",
                modifier = Modifier.weight(1f),
            )
            CatTextField(
                value = weight,
                onValueChange = { weight = it },
                label = "体重",
                placeholder = "2.5 kg",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        CatTextField(
            value = personality,
            onValueChange = { personality = it },
            label = "性格（可选）",
            placeholder = "亲人、怕生、活泼…",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "标签（用逗号分隔，如：已疫苗,已驱虫,亲人）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        CatTextField(
            value = tags,
            onValueChange = { tags = it },
            label = "标签",
            placeholder = "已疫苗,已驱虫,亲人",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "记录疫苗/驱虫/绝育健康记录后，对应标签会自动添加。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Text("亲属关系（可选）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (selectedRelations.isEmpty()) {
            Text(
                "建档时即可关联其他猫咪：母亲、父亲、子女或同胞。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            selectedRelations.forEach { (relatedId, type) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${allCats.firstOrNull { it.id == relatedId }?.nickname ?: "猫咪"} · ${type.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { selectedRelations = selectedRelations.filterNot { it.first == relatedId } }) {
                        Icon(Icons.Outlined.Close, "移除关系", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        CatButton(
            text = "添加关系",
            onClick = { showRelationDialog = true },
            style = com.catlink.app.ui.components.CatButtonStyle.Secondary,
            height = 36.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("地区", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRegionDialog = true }
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.control))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (cityName.isNotBlank()) "$cityName · $districtName" else "选择省份 / 城市 / 区县",
                style = MaterialTheme.typography.bodyMedium,
                color = if (cityName.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        if (cityName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clickable { cityName = ""; districtName = "" }
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.pill))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("清除地区 ×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(Modifier.height(14.dp))
        CatTextField(
            value = story,
            onValueChange = { story = it },
            label = "救助故事（可选）",
            placeholder = "在哪里发现了它？当时怎么样？",
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        CatButton(
            text = if (saving) "正在保存…" else if (editing) "保存修改" else "保存档案",
            onClick = {
                val error = catFormValidationError(nickname, coverUri)
                if (error != null) {
                    coverError = coverUri.isBlank()
                    CatNoticeCenter.show(error, error = true)
                    scope.launch { scrollState.animateScrollTo(0) }
                } else if (!saving && !coverBusy) {
                    saving = true
                    val rescued = LocalDate.ofEpochDay(rescuedDate)
                    scope.launch {
                        runCatching {
                        if (editing && catId != null) {
                            repository.updateCat(
                                id = catId,
                                nickname = nickname,
                                alias = alias,
                                gender = gender,
                                ageEstimate = age,
                                coat = coat,
                                weight = weight,
                                personality = personality,
                                story = story,
                                rescuedDate = rescued,
                                healthStatus = HealthStatus.fromName(healthStatusName),
                                tags = tags.replace("，", ","),
                                cityName = cityName,
                                districtName = districtName,
                            )
                            repository.updateCatCover(catId, coverUri)
                            // 编辑时同步关系（只重建本猫发起的正向关系，不触碰别人指向本猫的关系）
                            val existing = repository.catRelations(catId).filter { it.catId == catId }.map { it.id }
                            existing.forEach { repository.removeCatRelation(it) }
                            selectedRelations.forEach { (relatedId, type) ->
                                repository.addCatRelation(catId, relatedId, type)
                            }
                        } else {
                            val newId = repository.createCat(
                                nickname = nickname,
                                alias = alias,
                                status = CatStatus.fromName(statusName),
                                gender = gender,
                                ageEstimate = age,
                                coat = coat,
                                weight = weight,
                                personality = personality,
                                story = story,
                                rescuedDate = rescued,
                                coverColorIndex = (nickname.hashCode() and Int.MAX_VALUE) % 6,
                                coverUri = coverUri,
                                tags = tags.replace("，", ","),
                                cityName = cityName,
                                districtName = districtName,
                            )
                            selectedRelations.forEach { (relatedId, type) ->
                                repository.addCatRelation(newId, relatedId, type)
                            }
                        }
                        }.onSuccess {
                            if (editing && originalCoverUri.isNotBlank() && originalCoverUri != coverUri) {
                                withContext(Dispatchers.IO) {
                                    com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(originalCoverUri))
                                }
                            }
                            coverCommitted = true
                            CatNoticeCenter.show(
                                if (editing) "档案已更新" else "档案已保存，可继续添加健康记录或创建领养个案",
                            )
                            onBack()
                        }.onFailure {
                            saving = false
                            CatNoticeCenter.show(it.message ?: "档案保存失败", error = true)
                        }
                    }
                }
            },
            enabled = formReady && !saving && !coverBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "资料默认仅保存在此设备，可离线使用。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showRegionDialog) {
        com.catlink.app.ui.components.RegionPickerDialog(
            initialCity = cityName,
            initialDistrict = districtName,
            onDismiss = { showRegionDialog = false },
            onConfirm = { city, district ->
                cityName = city
                districtName = district
                showRegionDialog = false
            },
        )
    }
    if (showRelationDialog) {
        AddRelationPickerDialog(
            candidates = allCats,
            selected = selectedRelations,
            onDismiss = { showRelationDialog = false },
            onAdd = { relatedId, type ->
                if (selectedRelations.none { it.first == relatedId }) {
                    selectedRelations = selectedRelations + (relatedId to type)
                }
            },
        )
    }
    if (showCoverSourceDialog) {
        CatDialog(
            onDismissRequest = { showCoverSourceDialog = false },
            title = { Text(if (coverUri.isBlank()) "上传封面照片" else "更换封面照片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoverSourceOption(Icons.Outlined.CameraAlt, "拍照") {
                        showCoverSourceDialog = false
                        val file = File.createTempFile("cover_capture_", ".jpg", context.cacheDir)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraPath = file.absolutePath
                        cameraUriString = uri.toString()
                        takePicture.launch(uri)
                    }
                    CoverSourceOption(Icons.Outlined.PhotoLibrary, "从相册选择") {
                        showCoverSourceDialog = false
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCoverSourceDialog = false }) { Text("取消") }
            },
        )
    }
}

internal fun shouldDeleteDraftOnDispose(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun catFormValidationError(nickname: String, coverUri: String): String? = when {
    coverUri.isBlank() -> "请先上传封面照片"
    nickname.isBlank() -> "请填写猫咪昵称"
    else -> null
}

@Composable
private fun CoverSourceOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CatSizes.touchTarget)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusSelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = CatSizes.touchTarget)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 建档/编辑时的关系选择对话框 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRelationPickerDialog(
    candidates: List<com.catlink.app.data.db.CatEntity>,
    selected: List<Pair<String, com.catlink.app.data.CatRelationType>>,
    onDismiss: () -> Unit,
    onAdd: (String, com.catlink.app.data.CatRelationType) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var relationType by remember { mutableStateOf(com.catlink.app.data.CatRelationType.SIBLING) }
    var searchQuery by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加亲属关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CatTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索猫咪",
                    placeholder = "输入昵称查找",
                    modifier = Modifier.fillMaxWidth(),
                )
                val available = candidates
                    .filterNot { c -> selected.any { it.first == c.id } }
                    .filter { c -> searchQuery.isBlank() || c.nickname.contains(searchQuery.trim(), ignoreCase = true) }
                if (available.isEmpty()) {
                    Text("没有其他猫咪可关联", style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(available.size) { index ->
                            val c = available[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCatId = c.id }
                                    .background(
                                        if (selectedCatId == c.id) scheme.primaryContainer.copy(alpha = 0.4f) else scheme.surfaceContainerLow,
                                        RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.control),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.nickname, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                                if (selectedCatId == c.id) Text("✓", color = scheme.primary, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.catlink.app.data.CatRelationType.entries.forEach { t ->
                        Box(
                            modifier = Modifier
                                .clickable { relationType = t }
                                .background(
                                    if (relationType == t) scheme.primaryContainer else scheme.surfaceContainerLow,
                                    RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.pill),
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (relationType == t) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = selectedCatId != null,
                onClick = {
                    selectedCatId?.let { id -> onAdd(id, relationType) }
                    onDismiss()
                },
            ) { Text("添加") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
