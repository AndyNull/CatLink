package com.catlink.app.ui.screens.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.SharedCatView
import com.catlink.app.network.OnlineErrorException
import com.catlink.app.ui.components.SoftCard
import kotlinx.coroutines.launch
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import com.catlink.app.ui.navigation.SecondaryPage
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing

/** 猫咪档案分享预览页（访客视角）：多图画廊 + 信息卡片 + 领养申请。 */
@Composable
fun SharePreviewScreen(code: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val runtime = com.catlink.app.runtime.LocalAppRuntimeState.current
    val signedIn = (runtime.session as? com.catlink.app.runtime.SessionRuntimeState.SignedIn)?.emailVerified == true
    var view by remember { mutableStateOf<SharedCatView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showApply by remember { mutableStateOf(false) }
    var applyResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(code) {
        app.container.shareApi.fetchSharedCat(code).fold(
            onSuccess = { view = it; loading = false },
            onFailure = {
                error = (it as? OnlineErrorException)?.error?.userMessage ?: it.message
                loading = false
            },
        )
    }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = CatSpacing.pageTop),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在加载档案…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🐾", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(CatSpacing.sm))
                        Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                view != null -> SharedCatGallery(view!!, onApply = { showApply = true })
            }
        }
    }

    if (showApply && view != null) {
        AdoptionApplyDialog(
            catName = view!!.nickname,
            publicCatId = view!!.catId,
            onDismiss = { showApply = false },
            onSubmitted = {
                showApply = false
                applyResult = "领养申请已提交，救助人会尽快与你联系。"
                com.catlink.app.ui.components.CatNoticeCenter.show("领养申请已提交")
            },
        )
    }
    applyResult?.let { msg ->
        CatDialog(
            onDismissRequest = { applyResult = null },
            title = { Text("申请已提交") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { applyResult = null }) { Text("好的") }
            },
        )
    }
}

/** 领养申请对话框（登录后可提交）。 */
@Composable
private fun AdoptionApplyDialog(
    catName: String,
    publicCatId: String,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("申请领养「$catName」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                Text(
                    "请填写你的情况，救助人会收到申请并与你联系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CatTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = "自我介绍",
                    placeholder = "居住环境、养猫经验等",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = "联系方式",
                    placeholder = "微信 / 电话（仅救助人可见）",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && contact.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        // 测试环境：申请提交到分享码对应内容（无公开档案时提交到通用申请）
                        app.container.publicApi.applyAdoption(
                            publicCatId = publicCatId,
                            message = message.trim(),
                            contactSummary = contact.trim(),
                        ).fold(
                            onSuccess = { busy = false; onSubmitted() },
                            onFailure = {
                                busy = false
                                error = (it as? OnlineErrorException)?.error?.userMessage ?: it.message
                            },
                        )
                    }
                },
            ) { Text(if (busy) "提交中…" else "提交申请") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 高级感档案展示：全宽画廊 + 白卡信息 + 标签 + 领养申请。 */
@Composable
private fun SharedCatGallery(view: SharedCatView, onApply: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val extras = CatLinkThemeColors.extras
    val photos = listOf(view.coverUrl) + view.photoUrls.filter { it != view.coverUrl }

    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.lg)) {
        // 全宽画廊
        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = CatSpacing.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
            ) {
                items(photos) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = view.nickname,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillParentMaxWidth(0.92f)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(CatRadii.card)),
                    )
                }
            }
        } else {
            // 无照片：优雅渐变占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CatSpacing.pageHorizontal)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(CatRadii.card))
                    .background(
                        Brush.linearGradient(
                            listOf(extras.accentPink.copy(alpha = 0.55f), extras.mint.copy(alpha = 0.45f)),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Pets, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(56.dp))
            }
        }

        // 标题区
        Column(
            modifier = Modifier.padding(horizontal = CatSpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    view.nickname,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                // 状态徽章
                Text(
                    com.catlink.app.data.CatStatus.fromName(view.statusCode).let { status ->
                        val archive = com.catlink.app.data.ArchiveReason.fromNameOrNull(view.archiveReason)
                        if (status == com.catlink.app.data.CatStatus.ARCHIVED && archive != null) "${status.label} · ${archive.label}" else status.label
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onMint,
                    modifier = Modifier
                        .background(extras.mint.copy(alpha = 0.18f), RoundedCornerShape(CatRadii.pill))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(
                "分享自救助人 · ${view.code}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // 基本信息行
        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CatSpacing.pageHorizontal),
        ) {
            Row(
                modifier = Modifier.padding(vertical = CatSpacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InfoItem(Icons.Outlined.Male, view.gender.ifBlank { "未知" })
                InfoItem(Icons.Outlined.Cake, view.ageBand.ifBlank { "未知" })
                InfoItem(Icons.Outlined.Spa, view.coat.ifBlank { "未知" })
            }
        }

        // 故事
        if (view.story.isNotBlank()) {
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CatSpacing.pageHorizontal),
            ) {
                Column(
                    modifier = Modifier.padding(CatSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
                ) {
                    Text("它的故事", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Text(view.story, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }

        // 健康与性格
        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CatSpacing.pageHorizontal),
        ) {
            Column(
                modifier = Modifier.padding(CatSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
            ) {
                if (view.healthSummary.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Favorite, null, tint = extras.accentPink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(CatSpacing.xs))
                        Text("健康概况：${view.healthSummary}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                val tags = view.personalityTags.split(",").filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(4).forEach { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = extras.onAccentPink,
                                modifier = Modifier
                                    .background(extras.accentPink.copy(alpha = 0.16f), RoundedCornerShape(CatRadii.pill))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }

        if (com.catlink.app.data.CatStatus.fromName(view.statusCode) == com.catlink.app.data.CatStatus.ADOPTABLE) {
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CatSpacing.pageHorizontal),
            ) {
                Row(
                    modifier = Modifier.padding(CatSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(extras.mint.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🐱", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(CatSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("想给它一个家？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text("提交领养申请，救助人会与你联系。", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onApply) { Text("申请领养", color = scheme.primary) }
                }
            }
        }
        Spacer(Modifier.height(CatSpacing.lg))
    }
}

@Composable
private fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}
