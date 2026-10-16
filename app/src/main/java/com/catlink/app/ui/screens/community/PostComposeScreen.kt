package com.catlink.app.ui.screens.community

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.PublicApi
import com.catlink.app.network.OnlineErrorException
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.navigation.SecondaryPage
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val postDraftJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * 发布动态独立页面：话题 + 标题/正文 + 上传图片（最多 4 张）+ 关联自己的猫咪档案。
 */
@Composable
fun PostComposeScreen(onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    var topic by rememberSaveable { mutableStateOf("rescue") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoJson by rememberSaveable { mutableStateOf("[]") }
    val pendingPhotos = remember(pendingPhotoJson) {
        runCatching { postDraftJson.decodeFromString<List<PendingPostPhoto>>(pendingPhotoJson) }.getOrDefault(emptyList())
    }
    fun updatePendingPhotos(photos: List<PendingPostPhoto>) {
        pendingPhotoJson = postDraftJson.encodeToString(photos)
    }
    var cats by remember { mutableStateOf<List<CatEntity>>(emptyList()) }
    var linkedCatId by rememberSaveable { mutableStateOf("") }
    val linkedCat = cats.firstOrNull { it.id == linkedCatId }
    var city by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var showRegionPicker by remember { mutableStateOf(false) }
    var previewPhotoId by remember { mutableStateOf<String?>(null) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var published by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        app.container.repository.observeCats().collect { cats = it }
    }

    val photosAtDispose by rememberUpdatedState(pendingPhotos)
    val publishedAtDispose by rememberUpdatedState(published)
    DisposableEffect(Unit) {
        onDispose {
            if (!publishedAtDispose && shouldCleanupPostDraft(context.findActivity()?.isChangingConfigurations == true)) {
                val photos = photosAtDispose
                deletePostDraftFiles(context, photos)
                photos.map(PendingPostPhoto::serverId).filter(String::isNotBlank).forEach { mediaId ->
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        app.container.publicApi.deleteTemporaryMedia(mediaId)
                    }
                }
            }
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            val result = preparePostPhotos(context, pendingPhotos, uris)
            updatePendingPhotos(result.photos)
            error = when {
                result.failed > 0 && result.ignored > 0 -> "${result.failed} 张无法读取，另有 ${result.ignored} 张超过数量限制"
                result.failed > 0 -> "${result.failed} 张图片无法读取，请重新选择"
                result.ignored > 0 -> "最多添加 $MAX_POST_PHOTOS 张图片，已忽略 ${result.ignored} 张"
                else -> null
            }
            busy = false
        }
    }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            SecondaryTopBar(title = "发布动态", onBack = onBack)
            Text(
                "分享救助故事或为猫咪寻找新家，可关联你的猫咪档案。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 话题
            Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                listOf("rescue" to "救助", "seeking_adoption" to "求领养", "general" to "交流").forEach { (code, label) ->
                    val selected = topic == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CatRadii.pill))
                            .background(
                                if (selected) CatLinkThemeColors.extras.accentPink.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .heightIn(min = 44.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { topic = code })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) CatLinkThemeColors.extras.onAccentPink else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CatTextField(
                value = title,
                onValueChange = { title = it },
                label = "标题",
                placeholder = "一句话说清动态主题",
                modifier = Modifier.fillMaxWidth(),
            )
            CatTextField(
                value = body,
                onValueChange = { body = it },
                label = "正文",
                placeholder = "补充细节、经过或领养要求…",
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "所在区县",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CatRadii.control))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { showRegionPicker = true }
                    .padding(CatSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = com.catlink.app.data.RegionData.locationLabel(city, district, "选择城市和区县"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (district.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (district.isBlank()) "选择" else "更改",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 上传图片
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "图片",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${pendingPhotos.size}/$MAX_POST_PHOTOS · 可调整顺序",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                items(pendingPhotos, key = { it.id }) { photo ->
                    val index = pendingPhotos.indexOfFirst { it.id == photo.id }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            AsyncImage(
                                model = photo.localPath,
                                contentDescription = "预览第 ${index + 1} 张动态图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(CatRadii.compact))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { previewPhotoId = photo.id },
                            )
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(5.dp)
                                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                    .clickable {
                                        updatePendingPhotos(pendingPhotos.filterNot { it.id == photo.id })
                                        deletePostDraftFiles(context, listOf(photo))
                                        if (photo.serverId.isNotBlank()) scope.launch {
                                            app.container.publicApi.deleteTemporaryMedia(photo.serverId)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, "移除第 ${index + 1} 张图片", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                enabled = index > 0 && !busy,
                                onClick = { updatePendingPhotos(movePostPhoto(pendingPhotos, photo.id, -1)) },
                                modifier = Modifier.size(44.dp),
                            ) { Icon(Icons.Outlined.ChevronLeft, "前移", modifier = Modifier.size(20.dp)) }
                            IconButton(
                                enabled = index in 0 until pendingPhotos.lastIndex && !busy,
                                onClick = { updatePendingPhotos(movePostPhoto(pendingPhotos, photo.id, 1)) },
                                modifier = Modifier.size(44.dp),
                            ) { Icon(Icons.Outlined.ChevronRight, "后移", modifier = Modifier.size(20.dp)) }
                        }
                        if (photo.failed) {
                            Text("待重试", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (pendingPhotos.size < MAX_POST_PHOTOS) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(CatRadii.compact))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(CatRadii.compact))
                                .clickable(enabled = !busy) {
                                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Add, "添加图片", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 关联猫咪档案
            Text(
                "关联猫咪档案（可选）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val availableCats = cats.filter { it.deletedAt == null }
            if (availableCats.isEmpty()) {
                Text(
                    "还没有猫咪档案，先去“档案”页创建吧。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 简易选择器：当前选中的猫 + 切换按钮
                if (linkedCat == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CatRadii.control))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { linkedCatId = availableCats.firstOrNull()?.id.orEmpty() }
                            .padding(CatSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Pets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(CatSpacing.sm))
                        Text("选择一只猫咪档案", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val cat = linkedCat!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CatRadii.control))
                            .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.12f))
                            .padding(CatSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🐱", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.width(CatSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.nickname, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("已关联：动态将展示这只猫咪", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { linkedCatId = "" }) { Text("移除", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            if (error != null) {
                Text(error ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            CatButton(
                text = when {
                    busy && uploadProgress > 0 -> "正在上传 $uploadProgress/${pendingPhotos.size}"
                    busy -> "发布中…"
                    pendingPhotos.any(PendingPostPhoto::failed) -> "重试并发布"
                    else -> "发布动态"
                },
                enabled = !busy && title.isNotBlank() && body.isNotBlank() && district.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val uploaded = uploadPostPhotos(app.container.publicApi, pendingPhotos, postDraftDir(context)) { updated ->
                            updatePendingPhotos(updated)
                            uploadProgress = updated.count { it.serverId.isNotBlank() }
                        }
                        updatePendingPhotos(uploaded.photos)
                        if (uploaded.failed > 0) {
                            busy = false
                            uploadProgress = 0
                            error = "${uploaded.failed} 张图片上传失败，点击“重试并发布”继续"
                            return@launch
                        }
                        app.container.publicApi.createPost(
                            PublicApi.PostInput(
                                id = requestId,
                                topic = topic,
                                title = title.trim(),
                                body = body.trim(),
                                cityCode = city,
                                districtCode = district,
                                mediaUrls = uploaded.photos.joinToString(",") { it.url },
                                mediaIds = uploaded.photos.map { it.serverId },
                                catRef = linkedCat?.id,
                            )
                        ).fold(
                            onSuccess = {
                                busy = false
                                uploadProgress = 0
                                published = true
                                deletePostDraftFiles(context, uploaded.photos)
                                updatePendingPhotos(emptyList())
                                requestCommunityFeedRefresh()
                                CatNoticeCenter.show("发布成功")
                                onPublished()
                            },
                            onFailure = {
                                busy = false
                                uploadProgress = 0
                                error = (it as? OnlineErrorException)?.error?.userMessage ?: it.message
                            },
                        )
                    }
                },
            )
        }
    }

    if (showRegionPicker) {
        com.catlink.app.ui.components.RegionPickerDialog(
            initialCity = city,
            initialDistrict = district,
            onDismiss = { showRegionPicker = false },
            onConfirm = { selectedCity, selectedDistrict ->
                city = selectedCity
                district = selectedDistrict
                showRegionPicker = false
            },
        )
    }

    pendingPhotos.firstOrNull { it.id == previewPhotoId }?.let { photo ->
        com.catlink.app.ui.components.CatDialog(
            onDismissRequest = { previewPhotoId = null },
            title = { Text("图片预览") },
            text = {
                AsyncImage(
                    model = photo.localPath,
                    contentDescription = "动态图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(480.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { previewPhotoId = null }) { Text("关闭") }
            },
        )
    }
}

internal fun shouldCleanupPostDraft(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
