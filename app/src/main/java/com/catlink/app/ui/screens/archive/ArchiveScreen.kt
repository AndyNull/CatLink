package com.catlink.app.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.CatStatus
import com.catlink.app.data.HealthStatus
import com.catlink.app.data.db.CatEntity
import com.catlink.app.ui.components.CatAvatar
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatButtonStyle
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatIconButton
import com.catlink.app.ui.components.EmptyState
import com.catlink.app.ui.components.StatusChip
import com.catlink.app.ui.components.catStatusColors
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSizes
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch

private data class Filter(val label: String, val statuses: Set<CatStatus>, val health: HealthStatus? = null)

private val filters = listOf(
    Filter("全部", emptySet()),
    Filter("救助中", setOf(CatStatus.RESCUE)),
    Filter("治疗中", setOf(CatStatus.TREATING)),
    Filter("待领养", setOf(CatStatus.ADOPTABLE)),
    Filter("已领养", setOf(CatStatus.ADOPTED)),
    Filter("已归档", setOf(CatStatus.ARCHIVED)),
    Filter("医疗处理中", emptySet(), HealthStatus.TREATING),
)

@Composable
fun ArchiveScreen(
    cats: List<CatEntity>,
    onOpenCat: (String) -> Unit,
    onNewCat: () -> Unit,
    initialStatuses: Set<CatStatus>? = null,
    initialHealth: HealthStatus? = null,
    initialToken: Long? = null,
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(filters.first()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDelete by remember { mutableStateOf(false) }

    // 外部跳转（统计页点击）指定初始筛选
    LaunchedEffect(initialStatuses, initialHealth, initialToken) {
        if (initialStatuses != null || initialHealth != null) {
            selectedFilter = filters.firstOrNull { filter ->
                when {
                    initialHealth != null && !initialStatuses.isNullOrEmpty() ->
                        filter.health == initialHealth && filter.statuses == initialStatuses
                    initialHealth != null -> filter.health == initialHealth
                    initialStatuses != null -> filter.statuses == initialStatuses
                    else -> false
                }
            } ?: Filter(
                label = "筛选",
                statuses = initialStatuses ?: emptySet(),
                health = initialHealth,
            )
        }
    }

    val filtered = remember(query, selectedFilter, cats) {
        val sf = selectedFilter
        cats.filter { cat ->
            val matchQuery = query.isBlank() ||
                cat.nickname.contains(query, ignoreCase = true) ||
                cat.alias.contains(query, ignoreCase = true) ||
                cat.coat.contains(query, ignoreCase = true) ||
                cat.tags.contains(query, ignoreCase = true)
            val matchStatus = sf.statuses.isEmpty() || CatStatus.fromName(cat.status) in sf.statuses
            val matchHealth = sf.health == null || cat.healthStatus == sf.health.name
            matchQuery && matchStatus && matchHealth
        }
    }
    // 按状态分组（保持筛选顺序）
    val grouped = remember(filtered) {
        filtered.groupBy { CatStatus.fromName(it.status) }
            .toList()
            .sortedBy { (status, _) -> status.ordinal }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CatSpacing.pageHorizontal,
            end = CatSpacing.pageHorizontal,
            top = CatSpacing.pageTop,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        item {
            Column {
                if (selectionMode) {
                    SelectionHeader(
                        selectedCount = selectedIds.size,
                        onCancel = {
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        onDelete = { showBatchDelete = true },
                    )
                } else {
                    ArchiveHeader(count = cats.size, onNewCat = onNewCat)
                }
                Spacer(Modifier.height(CatSpacing.sm))
                SearchField(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(CatSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 常用筛选与“更多”按钮同一行；更多弹出独立悬浮菜单（不占页面布局）
                    val primaryFilters = filters.take(4)
                    val moreFilters = filters.drop(4)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        primaryFilters.forEach { filter ->
                            FilterChip(
                                label = filter.label,
                                selected = filter == selectedFilter,
                                onClick = { selectedFilter = filter },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        MoreFilterButton(
                            selectedFilter = selectedFilter,
                            moreFilters = moreFilters,
                            onSelect = { selectedFilter = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(CatSpacing.sm))
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🐾",
                    title = "没有找到相关档案",
                    subtitle = "换个关键词或筛选条件试试",
                )
            }
        } else {
            grouped.forEach { (status, list) ->
                item(key = "header-${status.name}") {
                    GroupHeader(
                        status = status,
                        count = list.size,
                        modifier = Modifier.padding(top = CatSpacing.sm, bottom = CatSpacing.xxs),
                    )
                }
                items(list, key = { it.id }) { cat ->
                    CatCard(
                        cat = cat,
                        selected = selectionMode && cat.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (cat.id in selectedIds) selectedIds - cat.id else selectedIds + cat.id
                            } else {
                                onOpenCat(cat.id)
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = setOf(cat.id)
                        },
                    )
                }
            }
        }
    }

    if (showBatchDelete) {
        CatDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("移入回收站？") },
            text = { Text("已选 ${selectedIds.size} 只猫咪的档案将移入回收站，30 天内可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        selectedIds.forEach { repo.softDeleteCat(it) }
                        selectionMode = false
                        selectedIds = emptySet()
                        showBatchDelete = false
                    }
                }) { Text("确认移入", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "取消批量选择",
            onClick = onCancel,
        )
        Spacer(Modifier.width(CatSpacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "批量选择",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "已选 $selectedCount 只猫咪",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CatButton(
            text = "移入回收站",
            onClick = onDelete,
            enabled = selectedCount > 0,
            style = CatButtonStyle.Danger,
            height = CatSizes.controlHeight,
            leading = {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun ArchiveHeader(count: Int, onNewCat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "档案",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(CatSpacing.xxs))
            Text(
                text = "$count 只猫咪档案 · 长按可批量操作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 与首页一致的圆形主色按钮
        Box(
            modifier = Modifier
                .size(com.catlink.app.ui.theme.CatSizes.touchTarget)
                .clip(CircleShape)
                .clickable(onClick = onNewCat),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(com.catlink.app.ui.theme.CatSizes.iconButton)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新建档案",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CatSizes.controlHeight)
            .clip(RoundedCornerShape(CatRadii.field))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CatRadii.field))
            .padding(horizontal = CatSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(CatSpacing.xs))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "搜索昵称、花色或标签…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extras = CatLinkThemeColors.extras
    val shape = RoundedCornerShape(CatRadii.pill)
    Box(
        modifier = modifier
            .height(CatSizes.touchTarget)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(shape)
                .background(
                    if (selected) extras.accentPink.copy(alpha = 0.58f)
                    else scheme.surfaceContainerLowest,
                )
                .border(
                    CatSpacing.hairline,
                    if (selected) extras.onAccentPink.copy(alpha = 0.16f) else scheme.outlineVariant,
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = if (selected) extras.onAccentPink else scheme.onSurfaceVariant,
            )
        }
    }
}

/** “更多”筛选：按钮与常用筛选同一行；弹窗与按钮无缝拼接（等宽、紧贴下方、圆角视觉连续）。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MoreFilterButton(
    selectedFilter: Filter,
    moreFilters: List<Filter>,
    onSelect: (Filter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (moreFilters.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val selectedInMore = moreFilters.any { it == selectedFilter }

    Box(modifier = modifier) {
        FilterChip(
            label = "更多",
            selected = expanded || selectedInMore,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        // 统一 App 风格悬浮下拉（DropdownMenu）
        com.catlink.app.ui.components.CatDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = moreFilters.map { filter ->
                com.catlink.app.ui.components.CatDropdownItem(
                    label = filter.label,
                    selected = filter == selectedFilter,
                    onClick = { onSelect(filter) },
                )
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CatCard(
    cat: CatEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CatRadii.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) scheme.primaryContainer.copy(alpha = 0.34f)
                else scheme.surfaceContainerLowest,
            )
            .border(
                CatSpacing.hairline,
                if (selected) scheme.primary.copy(alpha = 0.28f) else scheme.outlineVariant,
                shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
            .padding(CatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选择模式角标
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.primary else scheme.surfaceContainerHighest)
                    .border(
                        CatSpacing.hairline,
                        if (selected) scheme.primary else scheme.outlineVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(CatSpacing.xs))
        }
        CatAvatar(
            index = cat.coverColorIndex,
            coverUri = cat.coverUri.ifBlank { null },
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(CatRadii.compact),
        )
        Spacer(Modifier.width(CatSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cat.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(CatSpacing.xs))
                StatusChipFor(cat)
            }
            Spacer(Modifier.height(CatSpacing.xxs))
            Text(
                text = "${cat.gender} · ${cat.ageEstimate} · ${cat.coat}".trimStart('·'),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            val tags = cat.tags.split(",").filter { it.isNotBlank() }.take(3)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(CatSpacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs),
                ) {
                    tags.forEach { tag -> TagPill(tag) }
                }
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = scheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun GroupHeader(status: CatStatus, count: Int, modifier: Modifier = Modifier) {
    val (container, content) = catStatusColors(status)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(content, CircleShape),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(CatSpacing.xs))
        Text(
            text = "$count 只",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(CatRadii.pill))
                .background(container.copy(alpha = 0.72f))
                .padding(horizontal = CatSpacing.xs, vertical = CatSpacing.xxs),
        )
    }
}

@Composable
private fun StatusChipFor(cat: CatEntity) {
    val status = CatStatus.fromName(cat.status)
    val (container, content) = catStatusColors(status)
    val archive = com.catlink.app.data.ArchiveReason.fromNameOrNull(cat.archiveReason)
    StatusChip(
        text = if (status == CatStatus.ARCHIVED && archive != null) "${status.label} · ${archive.label}" else status.label,
        container = container,
        content = content,
    )
}

@Composable
private fun TagPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CatRadii.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = CatSpacing.xs, vertical = CatSpacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
