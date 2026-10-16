package com.catlink.app.ui.screens.catdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.CatVersionEntity
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing

/** 档案版本详情：展示变更前旧档案的完整信息（含封面与当时关联领养人）。 */
@Composable
fun CatVersionDetailScreen(
    versionId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scheme = MaterialTheme.colorScheme
    var version by remember { mutableStateOf<CatVersionEntity?>(null) }

    androidx.compose.runtime.LaunchedEffect(versionId) {
        version = repo.catVersionById(versionId)
    }

    val v = version
    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CatSpacing.xs, vertical = CatSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowLeft, "返回", tint = scheme.onSurface) }
            Text("档案版本详情", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        }

        if (v == null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            return@Column
        }

        val snapshot = remember(v.id) {
            runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(CatEntity.serializer(), v.snapshotJson)
            }.getOrNull()
        }
        if (snapshot == null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("版本数据无法解析", style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            }
            return@Column
        }

        val dateText = remember(v.createdAt) {
            java.time.Instant.ofEpochMilli(v.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
        ) {
            item {
                // 版本元信息
                SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CatRadii.card)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "版本 #${v.versionNo} · $dateText",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(v.changeSummary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                        if (v.reason.isNotBlank()) {
                            Text("原因：${v.reason}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        if (v.adopterSummary.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Pets, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                                Text(
                                    "当时关联领养人：${v.adopterSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (snapshot.coverUri.isNotBlank()) {
                item {
                    coil.compose.AsyncImage(
                        model = snapshot.coverUri,
                        contentDescription = "旧档案封面",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(CatRadii.card)),
                    )
                }
            }
            item {
                DetailSection("基本信息") {
                    FieldLine("昵称", snapshot.nickname)
                    FieldLine("别名", snapshot.alias)
                    FieldLine("性别", snapshot.gender)
                    FieldLine("年龄", snapshot.ageEstimate)
                    FieldLine("毛色", snapshot.coat)
                    FieldLine("体重", snapshot.weight)
                }
            }
            item {
                DetailSection("状态与健康") {
                    FieldLine("状态", com.catlink.app.data.CatStatus.fromName(snapshot.status).label)
                    FieldLine("归档原因", com.catlink.app.data.ArchiveReason.fromNameOrNull(snapshot.archiveReason)?.label.orEmpty())
                    FieldLine("状态日期", snapshot.statusDateEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "")
                    FieldLine("状态原因", snapshot.statusReason)
                    FieldLine("健康", com.catlink.app.data.HealthStatus.fromName(snapshot.healthStatus).label)
                    FieldLine("标签", snapshot.tags)
                }
            }
            item {
                DetailSection("相处与饮食") {
                    FieldLine("饮食禁忌", snapshot.dietRestrictions)
                    FieldLine("与人相处", snapshot.contactWithHumans)
                    FieldLine("与猫相处", snapshot.contactWithCats)
                }
            }
            item {
                DetailSection("救助信息") {
                    FieldLine("救助日期", java.time.LocalDate.ofEpochDay(snapshot.rescuedDateEpochDay).toString())
                    FieldLine("救助故事", snapshot.story)
                }
            }
            item {
                Text(
                    "资料默认仅保存在本机；版本快照为变更前档案的完整记录。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CatRadii.card)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            content()
        }
    }
}

@Composable
private fun FieldLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(56.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
