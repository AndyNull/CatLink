package com.catlink.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.PublicApi
import com.catlink.app.network.OnlineErrorException
import com.catlink.app.ui.navigation.SecondaryPage
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch

/** 站内通知列表。 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenAdoptionApplications: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var items by remember { mutableStateOf<List<PublicApi.NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PublicApi.NotificationItem?>(null) }

    LaunchedEffect(Unit) {
        app.container.publicApi.notifications().fold(
            onSuccess = {
                items = it
                loading = false
            },
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
                .padding(horizontal = CatSpacing.pageHorizontal),
        ) {
            // 统一顶栏：带返回键
            com.catlink.app.ui.components.SecondaryTopBar(title = "消息", onBack = onBack)
            Spacer(Modifier.height(CatSpacing.sm))
            Text(
                "公告、提醒、私聊等动态会出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CatSpacing.lg))
            when {
                loading -> Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error != null -> Column {
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    // 未登录或服务不可用时给出明确操作
                    val authErr = (error ?: "").contains("登录")
                    if (!authErr) {
                        Spacer(Modifier.height(8.dp))
                        com.catlink.app.ui.components.CatButton(
                            text = "重试",
                            onClick = {
                                error = null; loading = true
                                scope.launch {
                                    app.container.publicApi.notifications().fold(
                                        onSuccess = { items = it; loading = false },
                                        onFailure = { error = (it as? OnlineErrorException)?.error?.userMessage ?: it.message; loading = false },
                                    )
                                }
                            },
                            height = 36.dp,
                            modifier = Modifier.widthIn(min = 120.dp),
                        )
                    }
                }
                items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(CatSpacing.sm))
                        Text("暂无通知", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
                ) {
                    items(items, key = { it.id }) { item ->
                        NotificationRow(item) {
                            val openItem = {
                                if (opensAdoptionApplications(item)) onOpenAdoptionApplications() else selected = item
                            }
                            if (item.isRead || item.read) {
                                openItem()
                            } else {
                                scope.launch {
                                    app.container.publicApi.markNotificationsRead(item.id)
                                        .onSuccess {
                                            items = items.map { current -> if (current.id == item.id) current.copy(read = true, isRead = true) else current }
                                            com.catlink.app.ui.social.NotificationIndicator.accept(items)
                                        }
                                        .onFailure { com.catlink.app.ui.components.CatNoticeCenter.show(it.message ?: "更新已读状态失败", error = true) }
                                    openItem()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { item ->
        com.catlink.app.ui.components.CatDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.title) },
            text = { Text(item.body.ifBlank { "暂无正文" }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { selected = null }) { Text("知道了") } },
        )
    }
}

internal fun opensAdoptionApplications(item: PublicApi.NotificationItem): Boolean =
    item.kind == "adoption" && item.refType == "link" && item.refId.startsWith("/adoption/")

@Composable
private fun NotificationRow(item: PublicApi.NotificationItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val date = remember(item.createdAt) {
        java.time.Instant.ofEpochMilli(item.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (item.isRead || item.read) scheme.surfaceContainerLowest else CatLinkThemeColors.extras.accentPink.copy(alpha = 0.08f),
                RoundedCornerShape(CatRadii.card),
            )
            .padding(CatSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            com.catlink.app.ui.components.BrandLogoAvatar(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(50))
        }
        Spacer(Modifier.width(CatSpacing.sm))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 类型标签：公告 / 提醒 / 私聊
            val kindLabel = when (item.kind) {
                "announcement" -> "公告"
                "reminder", "remind" -> "提醒"
                "private" -> "私聊"
                else -> null
            }
            if (kindLabel != null) {
                Text(
                    kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CatRadii.pill))
                        .background(scheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.isRead || item.read) FontWeight.Normal else FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            if (item.body.isNotBlank()) {
                Text(item.body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
            }
            Text(date, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
        }
        if (!(item.isRead || item.read)) {
            Box(Modifier.size(8.dp).background(scheme.error, CircleShape))
        }
    }
}
