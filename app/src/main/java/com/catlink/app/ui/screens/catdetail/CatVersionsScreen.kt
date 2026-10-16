package com.catlink.app.ui.screens.catdetail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.CatVersionEntity
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.flow.first

/** 档案版本：每次内容变更自动备份的旧档案全量快照。 */
@Composable
fun CatVersionsScreen(
    catId: String,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scheme = MaterialTheme.colorScheme
    var versions by remember { mutableStateOf<List<CatVersionEntity>>(emptyList()) }
    var current by remember { mutableStateOf<CatEntity?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(catId) {
        versions = repo.catVersions(catId)
        current = repo.catById(catId)
    }

    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CatSpacing.xs, vertical = CatSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowLeft, "返回", tint = scheme.onSurface) }
            Text("档案版本", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
        ) {
            item {
                Text(
                    "编辑猫咪档案或添加医疗记录时，系统会自动保存变更前的完整档案，方便随时回溯。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (versions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.History, null, tint = scheme.outline, modifier = Modifier.size(44.dp))
                        Text("暂无历史版本", style = MaterialTheme.typography.bodyMedium, color = scheme.outline)
                    }
                }
            } else {
                items(versions.size) { index ->
                    val v = versions[index]
                    val expanded = expandedId == v.id
                    VersionCard(
                        version = v,
                        expanded = expanded,
                        onClick = { expandedId = if (expanded) null else v.id },
                        onOpenDetail = { onOpenDetail(v.id) },
                    )
                }
                item {
                    Text(
                        "当前版本",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                current?.let { c ->
                    item {
                        SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.Pets, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(c.nickname, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                                    Text(
                                        "${c.weight.ifBlank { "体重未知" }} · ${com.catlink.app.data.CatStatus.fromName(c.status).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(
    version: CatVersionEntity,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val snapshot = remember(version.id) {
        runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(CatEntity.serializer(), version.snapshotJson)
        }.getOrNull()
    }
    val dateText = remember(version.createdAt) {
        java.time.Instant.ofEpochMilli(version.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
    SoftCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(scheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "#${version.versionNo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        version.changeSummary,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "$dateText${if (version.reason.isNotBlank()) " · ${version.reason}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "收起" else "查看", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
            if (expanded && snapshot != null) {
                Spacer(Modifier.height(4.dp))
                Text("变更前的完整档案：", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FieldLine("昵称", snapshot.nickname)
                    FieldLine("别名", snapshot.alias)
                    FieldLine("性别", snapshot.gender)
                    FieldLine("年龄", snapshot.ageEstimate)
                    FieldLine("毛色", snapshot.coat)
                    FieldLine("体重", snapshot.weight)
                    FieldLine("性格", snapshot.personality)
                    FieldLine("状态", com.catlink.app.data.CatStatus.fromName(snapshot.status).label)
                    FieldLine("归档原因", com.catlink.app.data.ArchiveReason.fromNameOrNull(snapshot.archiveReason)?.label.orEmpty())
                    FieldLine("健康", com.catlink.app.data.HealthStatus.fromName(snapshot.healthStatus).label)
                    FieldLine("饮食禁忌", snapshot.dietRestrictions)
                    FieldLine("与人相处", snapshot.contactWithHumans)
                    FieldLine("与猫相处", snapshot.contactWithCats)
                    FieldLine("救助日期", snapshot.rescuedDateEpochDay.let { java.time.LocalDate.ofEpochDay(it).toString() })
                    FieldLine("标签", snapshot.tags)
                    FieldLine("故事", snapshot.story)
                }
                if (version.adopterSummary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Pets, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                        Text(
                            "关联领养人：${version.adopterSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                CatButton(
                    text = "查看详细",
                    onClick = onOpenDetail,
                    height = 32.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(58.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
