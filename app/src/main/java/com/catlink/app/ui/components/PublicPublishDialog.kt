package com.catlink.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.PublicDraftRepository
import com.catlink.app.network.ApiEnvironment
import com.catlink.app.network.OnlineErrorException
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class DraftMediaUpload(val id: String, val url: String, val source: String = "")

private val draftMediaJson = Json { ignoreUnknownKeys = true }

/**
 * 公开档案发布对话框：只编辑脱敏白名单字段，逐只确认后保存草稿并发布。
 */
@Composable
fun PublicPublishDialog(
    initial: PublicDraftRepository.DraftInput,
    onDismiss: () -> Unit,
    onPublished: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(initial.publicNickname) }
    var alias by remember { mutableStateOf(initial.alias) }
    var story by remember { mutableStateOf(initial.story) }
    var gender by remember { mutableStateOf(initial.gender) }
    var ageBand by remember { mutableStateOf(initial.ageBand) }
    var coat by remember { mutableStateOf(initial.coat) }
    var tags by remember { mutableStateOf(initial.personalityTags) }
    var health by remember { mutableStateOf(initial.healthSummary) }
    var weight by remember { mutableStateOf(initial.weight) }
    var diet by remember { mutableStateOf(initial.dietRestrictions) }
    var humanContact by remember { mutableStateOf(initial.contactWithHumans) }
    var catContact by remember { mutableStateOf(initial.contactWithCats) }
    var city by remember { mutableStateOf(com.catlink.app.data.RegionData.cityName(initial.cityCode)) }
    var district by remember { mutableStateOf(initial.districtCode) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val media by app.container.repository.observeMedia(initial.catId).collectAsState(initial = emptyList())
    var selectedMedia by remember(initial.catId) { mutableStateOf<Set<String>>(emptySet()) }
    var resumedUploads by remember(initial.catId) { mutableStateOf<Map<String, DraftMediaUpload>>(emptyMap()) }

    LaunchedEffect(initial.catId) {
        val encoded = app.container.publicDrafts.draftForCat(initial.catId)?.mediaUploadState
        resumedUploads = encoded?.let { runCatching { draftMediaJson.decodeFromString<Map<String, DraftMediaUpload>>(it) }.getOrNull() }.orEmpty()
        selectedMedia = resumedUploads.keys.filterNot { it == "__cover__" }.toSet()
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    androidx.compose.foundation.shape.RoundedCornerShape(CatRadii.card),
                )
                .padding(CatSpacing.lg),
        ) {
            Text(
                "公开分享猫咪",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(CatSpacing.xxs))
            Text(
                "本地档案不会被清空；仅上传你确认的封面和相册。领养人、合同、费用等私密资料永不公开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CatSpacing.md))
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
            ) {
                CatTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = "公开昵称",
                    placeholder = "猫咪昵称",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = "别名",
                    placeholder = "其他称呼（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = story,
                    onValueChange = { story = it },
                    label = "公开故事",
                    placeholder = "TA 的故事（可选）",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                    CatTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = "性别",
                        placeholder = "母/公",
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = ageBand,
                        onValueChange = { ageBand = it },
                        label = "年龄段",
                        placeholder = "幼猫/成年",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                    CatTextField(
                        value = coat,
                        onValueChange = { coat = it },
                        label = "毛色",
                        placeholder = "橘白/狸花",
                        modifier = Modifier.weight(1f),
                    )
                    CatTextField(
                        value = health,
                        onValueChange = { health = it },
                        label = "健康概况",
                        placeholder = "已驱虫/健康",
                        modifier = Modifier.weight(1f),
                    )
                }
                CatTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "体重",
                    placeholder = "如 3.8 kg",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = "性格标签",
                    placeholder = "亲人,粘人,安静（逗号分隔）",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = diet,
                    onValueChange = { diet = it },
                    label = "饮食与禁忌",
                    placeholder = "过敏、处方粮或喂养注意事项",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = humanContact,
                    onValueChange = { humanContact = it },
                    label = "与人相处",
                    placeholder = "亲人程度、抱摸反应",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = catContact,
                    onValueChange = { catContact = it },
                    label = "与猫相处",
                    placeholder = "能否合住、社交情况",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(CatRadii.control))
                        .clickable { showRegionPicker = true }
                        .padding(CatSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = com.catlink.app.data.RegionData.locationLabel(city, district, "选择城市和区县"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text("公开相册（默认不公开）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (media.isEmpty()) {
                    Text("该档案暂时没有相册照片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                } else {
                    media.forEach { item ->
                        val checked = item.id in selectedMedia
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(CatRadii.control)).clickable {
                                selectedMedia = if (checked) selectedMedia - item.id else selectedMedia + item.id
                            }.padding(horizontal = CatSpacing.sm, vertical = CatSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatCheckbox(checked = checked, onCheckedChange = { selectedMedia = if (it) selectedMedia + item.id else selectedMedia - item.id })
                            Text("照片 ${item.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(CatSpacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(CatSpacing.xs))
                CatButton(
                    text = if (busy) "发布中…" else "保存并发布",
                    enabled = !busy && nickname.isNotBlank(),
                    onClick = {
                        if (nickname.isBlank()) return@CatButton
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val selected = media.filter { it.id in selectedMedia }
                                val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
                                val uploadState = resumedUploads
                                    .filterKeys { it in selectedIds || it == "__cover__" }
                                    .mapValues { (_, upload) -> upload.copy(url = publishableMediaPath(context, upload.url)) }
                                    .toMutableMap()
                                val baseDraft = initial.copy(
                                    publicNickname = nickname.trim(),
                                    alias = alias.trim(),
                                    story = story.trim(),
                                    gender = gender.trim(),
                                    ageBand = ageBand.trim(),
                                    coat = coat.trim(),
                                    personalityTags = tags.trim(),
                                    healthSummary = health.trim(),
                                    weight = weight.trim(),
                                    dietRestrictions = diet.trim(),
                                    contactWithHumans = humanContact.trim(),
                                    contactWithCats = catContact.trim(),
                                    cityCode = city,
                                    districtCode = district.trim(),
                                )
                                val uploadedPhotos = selected.map { item ->
                                    uploadState[item.id] ?: uploadLocalMedia(context, app.container.publicApi, item.uri)
                                        .getOrElse { throw it }
                                        .let { DraftMediaUpload(it.id, it.url, item.uri) }
                                        .also {
                                            uploadState[item.id] = it
                                            app.container.publicDrafts.saveDraft(
                                                baseDraft.copy(
                                                    photoUrls = uploadState.filterKeys { key -> key != "__cover__" }.values.joinToString(",") { value -> value.url },
                                                    mediaUploadState = draftMediaJson.encodeToString(uploadState),
                                                )
                                            ).getOrThrow()
                                        }
                                }
                                val coverSource = initial.coverMediaId.orEmpty()
                                val cachedCover = uploadState["__cover__"]?.takeIf { it.source == coverSource }
                                val staleCoverId = uploadState["__cover__"]
                                    ?.takeIf { it.source != coverSource }
                                    ?.id
                                    .orEmpty()
                                if (cachedCover == null) uploadState.remove("__cover__")
                                val cover = cachedCover
                                    ?: uploadLocalMedia(context, app.container.publicApi, initial.coverMediaId.orEmpty())
                                        .getOrElse { throw it }
                                        .let { DraftMediaUpload(it.id, it.url, coverSource) }
                                        .also { if (it.id.isNotBlank()) uploadState["__cover__"] = it }
                                resumedUploads = uploadState
                                val draft = baseDraft.copy(
                                    coverMediaId = cover.url,
                                    photoUrls = uploadedPhotos.joinToString(",") { it.url },
                                    mediaUploadState = draftMediaJson.encodeToString(uploadState),
                                )
                                val saved = app.container.publicDrafts.saveDraft(draft)
                                val published = if (saved.isSuccess) app.container.publicDrafts.publishDraft(draft.catId) else saved
                                if (published.isSuccess && staleCoverId.isNotBlank()) {
                                    app.container.publicApi.deleteTemporaryMedia(staleCoverId)
                                }
                                published.onSuccess {
                                    CatNoticeCenter.show("已发布到社区公开档案")
                                    onPublished()
                                }.onFailure { e ->
                                    error = (e as? OnlineErrorException)?.error?.userMessage ?: e.message ?: "发布失败"
                                }
                            } catch (e: Throwable) {
                                error = e.message ?: "照片上传失败，可稍后重试"
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }
    }

    if (showRegionPicker) {
        RegionPickerDialog(
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
}

private suspend fun uploadLocalMedia(
    context: Context,
    api: com.catlink.app.data.PublicApi,
    rawPath: String,
): Result<com.catlink.app.data.PublicApi.MediaUploadResult> = withContext(Dispatchers.IO) {
    if (rawPath.isBlank()) return@withContext Result.success(com.catlink.app.data.PublicApi.MediaUploadResult())
    if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
        return@withContext runCatching {
            com.catlink.app.data.PublicApi.MediaUploadResult(url = publishableMediaPath(context, rawPath))
        }
    }
    runCatching {
        val temporary = rawPath.startsWith("content://")
        val file = if (temporary) {
            com.catlink.app.data.MediaFiles.copyToUploadCache(context, Uri.parse(rawPath), "cat")
        } else {
            File(rawPath.removePrefix("file://"))
        }
        try {
            api.uploadTemporaryCatMedia(file).getOrThrow()
        } finally {
            if (temporary) file.delete()
        }
    }
}

private fun publishableMediaPath(context: Context, rawPath: String): String {
    if (!rawPath.startsWith("http://") && !rawPath.startsWith("https://")) return rawPath
    return ApiEnvironment.serverRelativePath(context, rawPath)
        .ifBlank { error("照片地址不属于当前服务器") }
}
