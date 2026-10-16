package com.catlink.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.AdoptionStatus
import com.catlink.app.data.CatStatus
import com.catlink.app.data.HealthStatus
import com.catlink.app.data.HomeStats
import com.catlink.app.data.MedicalCategory
import com.catlink.app.data.PublicApi
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatButtonStyle
import com.catlink.app.ui.components.UserAvatar
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    stats: HomeStats,
    cats: List<CatEntity> = emptyList(),
    medicals: List<MedicalEntity> = emptyList(),
    adoptionCases: List<CaseEntity> = emptyList(),
    followUps: List<FollowUpEntity> = emptyList(),
    onBack: (() -> Unit)? = null,
    onOpenTodos: () -> Unit = {},
    onOpenArchive: (Set<CatStatus>, HealthStatus?, Boolean) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val leaderboardYear = LocalDate.now().year
    var leaderboardKind by remember { mutableStateOf("rescue") }
    var leaderboardRows by remember { mutableStateOf<List<PublicApi.LeaderboardEntry>>(emptyList()) }
    var leaderboardLoading by remember { mutableStateOf(false) }
    val snapshot = remember(cats, medicals, adoptionCases, followUps) {
        buildStatsSnapshot(cats, medicals, adoptionCases, followUps)
    }
    LaunchedEffect(leaderboardKind, leaderboardYear) {
        leaderboardLoading = true
        leaderboardRows = emptyList()
        app.container.publicApi.annualLeaderboard(leaderboardKind, leaderboardYear)
            .onSuccess { leaderboardRows = it.items }
        leaderboardLoading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CatSpacing.pageHorizontal,
            end = CatSpacing.pageHorizontal,
            top = CatSpacing.pageTop,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.md),
    ) {
        item { StatsHeader(snapshot, adoptionCases.size, medicals.size, onBack) }
        item {
            OverviewPanel(
                snapshot = snapshot,
                onOpenAll = { onOpenArchive(emptySet(), null, false) },
                onOpenAdopted = { onOpenArchive(setOf(CatStatus.ADOPTED), null, false) },
                onOpenTreating = { onOpenArchive(emptySet(), HealthStatus.TREATING, false) },
                onOpenFollowUps = onOpenTodos,
            )
        }
        item { StatusComposition(snapshot) }
        item { HealthOverview(snapshot) }
        item { AdoptionFollowUpOverview(snapshot, stats) }
        item { ArchiveQualityOverview(snapshot) }
        item { RegionDistribution(snapshot.regions) }
        item { ActivityOverview(snapshot, cats, stats.streakDays) }
        item {
            AnnualContributionLeaderboard(
                year = leaderboardYear,
                kind = leaderboardKind,
                rows = leaderboardRows,
                loading = leaderboardLoading,
                onKindChange = { leaderboardKind = it },
            )
        }
    }
}

@Composable
private fun AnnualContributionLeaderboard(
    year: Int,
    kind: String,
    rows: List<PublicApi.LeaderboardEntry>,
    loading: Boolean,
    onKindChange: (String) -> Unit,
) {
    val label = when (kind) {
        "sterilization" -> "绝育"
        "adoption" -> "领养"
        else -> "救助"
    }
    StatsSection("${year} 年度${label}榜", CatLinkThemeColors.extras.warm) {
        Text(
            "全站 App 用户 · 按年度有效记录统计",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs), modifier = Modifier.fillMaxWidth()) {
            listOf("rescue" to "救助榜", "sterilization" to "绝育榜", "adoption" to "领养榜").forEach { (value, label) ->
                CatButton(
                    text = label,
                    onClick = { onKindChange(value) },
                    style = if (kind == value) CatButtonStyle.Primary else CatButtonStyle.Secondary,
                    height = 34.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when {
            loading -> Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.isEmpty() -> Text("当前年度暂无有效记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> rows.take(10).forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CatSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
                ) {
                    Text(
                        text = row.rank.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(24.dp),
                    )
                    UserAvatar(row.avatarUrl, "${row.nickname}的头像", Modifier.size(34.dp))
                    Text(
                        text = row.nickname.ifBlank { "用户 ${row.accountId}" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${row.score} 次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != rows.take(10).lastIndex) StatsDivider()
            }
        }
    }
}

@Composable
private fun StatsHeader(snapshot: StatsSnapshot, cases: Int, medicals: Int, onBack: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
        if (onBack != null) {
            SecondaryTopBar(title = "统计", onBack = onBack)
        } else {
            Text(
                text = "统计",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "${snapshot.totalCats} 份猫咪档案 · $cases 份领养档案 · $medicals 条健康记录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewPanel(
    snapshot: StatsSnapshot,
    onOpenAll: () -> Unit,
    onOpenAdopted: () -> Unit,
    onOpenTreating: () -> Unit,
    onOpenFollowUps: () -> Unit,
) {
    val extras = CatLinkThemeColors.extras
    StatsSurface(contentPadding = PaddingValues(0.dp), gap = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            OverviewMetric("全部档案", snapshot.totalCats.toString(), extras.mint, onOpenAll, Modifier.weight(1f))
            StatsDivider(vertical = true)
            OverviewMetric(
                "已领养",
                snapshot.adoptionCounts.countOf(AdoptionStatus.ADOPTED).toString(),
                extras.selection,
                onOpenAdopted,
                Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            OverviewMetric(
                "治疗中",
                snapshot.careCounts.countOf(CatStatus.TREATING).toString(),
                extras.neutral,
                onOpenTreating,
                Modifier.weight(1f),
            )
            StatsDivider(vertical = true)
            OverviewMetric("待回访", snapshot.pendingFollowUps.toString(), extras.warm, onOpenFollowUps, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        Box(Modifier.size(CatSpacing.xs).clip(CircleShape).background(accent))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusComposition(snapshot: StatsSnapshot) {
    StatsSection("档案构成", CatLinkThemeColors.extras.warm) {
        SplitColumns(
            left = {
                GroupLabel("救助状态")
                CountRow("救助中", snapshot.careCounts.countOf(CatStatus.RESCUE))
                CountRow("治疗中", snapshot.careCounts.countOf(CatStatus.TREATING))
                CountRow("已放归", snapshot.careCounts.countOf(CatStatus.RELEASED))
                CountRow("已归档", snapshot.careCounts.countOf(CatStatus.ARCHIVED))
            },
            right = {
                GroupLabel("领养状态")
                CountRow("未开放", snapshot.adoptionCounts.countOf(AdoptionStatus.NONE))
                CountRow("待领养", snapshot.adoptionCounts.countOf(AdoptionStatus.ADOPTABLE))
                CountRow("已领养", snapshot.adoptionCounts.countOf(AdoptionStatus.ADOPTED))
                CountRow("转化率", snapshot.adoptionRate.asPercent())
            },
        )
    }
}

@Composable
private fun HealthOverview(snapshot: StatsSnapshot) {
    val total = snapshot.totalCats
    StatsSection("医疗与健康", CatLinkThemeColors.extras.mint) {
        Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            CompactValue("健康", snapshot.healthCounts.countOf(HealthStatus.OK).toString(), Modifier.weight(1f))
            CompactValue("治疗", snapshot.healthCounts.countOf(HealthStatus.TREATING).toString(), Modifier.weight(1f))
            CompactValue("待检查", snapshot.healthCounts.countOf(HealthStatus.UNKNOWN).toString(), Modifier.weight(1f))
        }
        StatsDivider()
        CoverageRow("绝育覆盖", snapshot.medicalCoverage.countOf(MedicalCategory.SPAY), total, CatLinkThemeColors.extras.onMint)
        CoverageRow("疫苗覆盖", snapshot.medicalCoverage.countOf(MedicalCategory.VACCINE), total, MaterialTheme.colorScheme.primary)
        CoverageRow("驱虫覆盖", snapshot.medicalCoverage.countOf(MedicalCategory.DEWORM), total, CatLinkThemeColors.extras.onWarm)
        Text(
            text = "健康记录 ${snapshot.medicalRecordCount} 条 · 治疗与检查 ${snapshot.treatmentRecordCount} 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdoptionFollowUpOverview(snapshot: StatsSnapshot, stats: HomeStats) {
    StatsSection("领养与回访", CatLinkThemeColors.extras.selection) {
        SplitColumns(
            left = {
                GroupLabel("领养档案")
                CountRow("进行中", snapshot.activeCases)
                CountRow("已完成", snapshot.completedCases)
                CountRow("已退养", snapshot.returnedCases)
            },
            right = {
                GroupLabel("回访执行")
                CountRow("待完成", snapshot.pendingFollowUps)
                CountRow("已完成", snapshot.completedFollowUps)
                CountRow("已逾期", snapshot.overdueFollowUps)
                CountRow("完成率", snapshot.followUpRate.asPercent())
            },
        )
        StatsDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.md), modifier = Modifier.fillMaxWidth()) {
            CompactValue(
                label = "平均待领养",
                value = stats.avgWaitDays?.let { "$it 天" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            CompactValue(
                label = "回访及时率",
                value = stats.followUpOnTimeRate.asPercent(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArchiveQualityOverview(snapshot: StatsSnapshot) {
    val coverage = snapshot.archiveCoverage
    val total = snapshot.totalCats
    StatsSection("档案资料", CatLinkThemeColors.extras.neutral) {
        QualityRow("地区", coverage.region, "街道", coverage.street, total)
        StatsDivider()
        QualityRow("社区", coverage.community, "年龄", coverage.age, total)
        StatsDivider()
        QualityRow("性格", coverage.personality, "照片", coverage.photo, total)
    }
}

@Composable
private fun QualityRow(left: String, leftCount: Int, right: String, rightCount: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.md), modifier = Modifier.fillMaxWidth()) {
        CompactValue(left, coverageText(leftCount, total), Modifier.weight(1f))
        CompactValue(right, coverageText(rightCount, total), Modifier.weight(1f))
    }
}

@Composable
private fun RegionDistribution(groups: List<RegionGroup>) {
    val shown = groups.take(4)
    val maxCount = shown.maxOfOrNull(RegionGroup::total)?.coerceAtLeast(1) ?: 1
    StatsSection("地区分布", CatLinkThemeColors.extras.warm) {
        if (shown.isEmpty()) {
            Text("暂无地区数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        shown.forEachIndexed { index, group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${group.total} 只", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = group.districts.take(3).joinToString(" · ") { "${it.first} ${it.second}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { group.total.toFloat() / maxCount },
                color = CatLinkThemeColors.extras.onWarm,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(CatRadii.pill)),
            )
            if (index != shown.lastIndex) StatsDivider()
        }
        if (groups.size > shown.size) {
            Text(
                text = "另有 ${groups.size - shown.size} 个地区，共 ${groups.drop(shown.size).sumOf(RegionGroup::total)} 只",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityOverview(snapshot: StatsSnapshot, cats: List<CatEntity>, streakDays: Int) {
    StatsSection("近期活跃", CatLinkThemeColors.extras.selection) {
        Row(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            CompactValue("30天新增", snapshot.recentRescues.toString(), Modifier.weight(1f))
            CompactValue("30天更新", snapshot.recentlyUpdated.toString(), Modifier.weight(1f))
            CompactValue("连续记录", "$streakDays 天", Modifier.weight(1f))
        }
        StatsDivider()
        RescueTrendChart(cats)
    }
}

@Composable
private fun StatsSection(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(CatSpacing.xs))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        StatsSurface(content = content)
    }
}

@Composable
private fun StatsSurface(
    contentPadding: PaddingValues = PaddingValues(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
    gap: androidx.compose.ui.unit.Dp = CatSpacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CatRadii.control))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun SplitColumns(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CatSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xs), content = left)
        StatsDivider(vertical = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xs), content = right)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun CountRow(label: String, value: Int) = CountRow(label, value.toString())

@Composable
private fun CountRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CompactValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun CoverageRow(label: String, count: Int, total: Int, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(coverageText(count, total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else count.toFloat() / total },
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(CatRadii.pill)),
        )
    }
}

@Composable
private fun StatsDivider(vertical: Boolean = false) {
    val modifier = if (vertical) Modifier.width(CatSpacing.hairline).fillMaxHeight() else Modifier.fillMaxWidth().height(CatSpacing.hairline)
    Box(modifier.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)))
}

@Composable
private fun RescueTrendChart(cats: List<CatEntity>) {
    val today = LocalDate.now()
    val days = (0L..29L).map { today.minusDays(it).toEpochDay() }.reversed()
    val counts = days.map { day -> cats.count { it.rescuedDateEpochDay == day } }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("每日新增", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Text("单日最高 $maxCount", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val barWidth = size.width / counts.size
        counts.forEachIndexed { index, count ->
            val barHeight = if (count == 0) 2.dp.toPx() else count.toFloat() / maxCount * (size.height - 12.dp.toPx())
            drawRoundRect(
                color = if (index == counts.lastIndex) scheme.primary
                else if (count > 0) scheme.primary.copy(alpha = 0.52f)
                else scheme.surfaceContainerHighest,
                topLeft = Offset(index * barWidth + barWidth * 0.25f, size.height - barHeight),
                size = Size(barWidth * 0.5f, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
    Text(
        text = "${today.minusDays(29).format(DateTimeFormatter.ofPattern("M/d"))} — ${today.format(DateTimeFormatter.ofPattern("M/d"))}",
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
    )
}

private fun <T> Map<T, Int>.countOf(key: T): Int = this[key] ?: 0

private fun Float?.asPercent(): String = this?.let { "${(it * 100).toInt()}%" } ?: "—"

private fun coverageText(count: Int, total: Int): String = if (total == 0) "—" else "$count/$total"

private fun RegionGroup.displayName(): String = when {
    province.isBlank() || province == city -> city
    else -> "$province · $city"
}
