package com.catlink.app.ui.screens.catdetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.RegionData
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch
import coil.request.ImageRequest

/** 他人创建的公开猫咪简略档案（非本人只能看到公开字段）。 */
@Composable
fun CatPublicDetailScreen(
    catId: String,
    onBack: () -> Unit,
    onOpenCollaboration: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val api = app.container.publicApi
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf<com.catlink.app.data.PublicCatItem?>(null) }
    var timeline by remember { mutableStateOf<List<com.catlink.app.data.PublicApi.TimelineEvent>>(emptyList()) }
    var followed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(catId) {
        api.publicCat(catId).onSuccess { cat = it }.onFailure { error = it.message ?: "加载失败" }
        api.catTimeline(catId).onSuccess { timeline = it }
        if (app.container.authSession.isSignedIn()) {
            followed = api.followedCats().getOrNull()?.any { it.id == catId } == true
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        SecondaryTopBar(title = "猫咪档案", onBack = onBack, modifier = Modifier.padding(horizontal = CatSpacing.pageHorizontal))

        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            }
            return@Column
        }
        val c = cat ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.md),
        ) {
            item {
                // 封面
                if (c.coverUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = ImageRequest.Builder(context).data(c.coverUrl).size(1080).build(),
                        contentDescription = "封面",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(CatRadii.card)),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(CatRadii.card))
                            .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.nickname.take(1), style = MaterialTheme.typography.displayMedium, color = scheme.onSurface)
                    }
                }
            }
            if (c.photoUrls.isNotEmpty()) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
                        contentPadding = PaddingValues(horizontal = CatSpacing.xs),
                    ) {
                        items(c.photoUrls) { url ->
                            coil.compose.AsyncImage(
                                model = ImageRequest.Builder(context).data(url).size(480).build(),
                                contentDescription = "猫咪相册",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(CatRadii.control)),
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.nickname, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
                            if (c.alias.isNotBlank()) Text("也叫 ${c.alias}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        Text("${c.followCount} 人关注", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                    }
                    val location = RegionData.locationLabel(c.cityCode, c.districtCode, c.locationName)
                    if (location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Text(location, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (c.story.isNotBlank()) {
                        Text(c.story, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PublicInfoPill(c.gender.ifBlank { "未知" }, Modifier.weight(1f))
                    PublicInfoPill(c.ageBand.ifBlank { "未知" }, Modifier.weight(1f))
                    PublicInfoPill(c.coat.ifBlank { "毛色" }, Modifier.weight(1f))
                    PublicInfoPill(publicStatusLabel(c.statusCode, c.archiveReason), Modifier.weight(1f))
                }
            }
            item {
                PublicDetailSection(
                    "基本情况",
                    listOf(
                        "当前状态" to c.catStatus,
                        "体重" to c.weight,
                    ),
                )
            }
            if (timeline.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                        Text("公开动态", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
                        timeline.forEach { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(CatRadii.control))
                                    .background(scheme.surfaceContainerLow)
                                    .padding(CatSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Text(event.kind.ifBlank { "救助记录" }, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                                    Text(formatTimelineDate(event.createdAt), style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                                }
                                Text(event.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                PublicDetailSection(
                    "性格与相处",
                    listOf(
                        "性格特点" to c.personalityTags,
                        "与人相处" to c.contactWithHumans,
                        "与猫相处" to c.contactWithCats,
                    ),
                )
            }
            item {
                PublicDetailSection(
                    "健康与照护",
                    listOf(
                        "饮食与禁忌" to c.dietRestrictions,
                        "健康记录摘要" to c.healthSummary,
                    ),
                )
            }
            item {
                PublicDetailSection(
                    "救助进展",
                    listOf(
                        "救助日期" to epochDayLabel(c.rescuedDateEpochDay),
                        "状态更新" to c.statusDateEpochDay?.let(::epochDayLabel).orEmpty(),
                        "归档原因" to com.catlink.app.data.ArchiveReason.fromNameOrNull(c.archiveReason)?.label.orEmpty(),
                        "状态说明" to c.statusReason,
                    ),
                )
            }
            item {
                CatButton(
                    text = "救助协作与动态",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenCollaboration,
                )
            }
            item {
                CatButton(
                    text = if (followed) "已关注" else "关注这只猫",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            val result = if (followed) api.unfollowCat(c.id).map { false } else api.followCat(c.id)
                            result.onSuccess { followed = it }
                                .onFailure { CatNoticeCenter.show(it.message ?: "操作失败", error = true) }
                        }
                    },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                    Icon(Icons.Outlined.Lock, null, tint = scheme.outline, modifier = Modifier.size(14.dp))
                    Text(
                        "领养人身份与联系方式、合同、费用不会公开。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicDetailSection(title: String, rows: List<Pair<String, String>>) {
    val visible = rows.filter { it.second.isNotBlank() }.distinctBy { it.first to it.second }
    if (visible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        visible.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun epochDayLabel(epochDay: Long): String =
    if (epochDay <= 0) "" else java.time.LocalDate.ofEpochDay(epochDay).toString()

private fun formatTimelineDate(epochMillis: Long): String =
    if (epochMillis <= 0) "" else java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

private fun publicStatusLabel(status: String, archiveReason: String): String {
    val main = com.catlink.app.data.CatStatus.fromName(status)
    val archive = com.catlink.app.data.ArchiveReason.fromNameOrNull(archiveReason)
    return if (main == com.catlink.app.data.CatStatus.ARCHIVED && archive != null) "${main.label} · ${archive.label}" else main.label
}

@Composable
private fun PublicInfoPill(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
