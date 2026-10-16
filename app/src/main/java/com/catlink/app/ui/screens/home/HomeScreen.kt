package com.catlink.app.ui.screens.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlink.app.CatLinkApplication
import com.catlink.app.R
import com.catlink.app.data.DailyEncouragementProvider
import com.catlink.app.data.NearbyLocationResolver
import com.catlink.app.data.Priority
import com.catlink.app.data.db.TodoEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.data.FollowUpStatus
import com.catlink.app.data.PrivateCaseMediaManager
import com.catlink.app.ui.screens.casedetail.CompleteFollowUpDialog
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.MainViewModel
import com.catlink.app.ui.components.BrandLogoAvatar
import com.catlink.app.ui.components.CatAvatar
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.SectionHeader
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.components.StatusChip
import com.catlink.app.ui.components.PulsingAddButton
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSizes
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenArchive: (com.catlink.app.ui.navigation.ArchiveFilterRequest) -> Unit,
    onOpenStats: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenCat: (String) -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenPublicCat: (String) -> Unit,
    onNewCat: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val settings = app.container.settings
    var encouragement by remember(settings) { mutableStateOf<DailyEncouragementProvider.Result?>(null) }
    LaunchedEffect(settings) {
        encouragement = DailyEncouragementProvider.today(settings) {
            app.container.publicApi.encouragement().getOrNull()
        }
    }
    val stats by vm.stats.collectAsState()
    val todos by vm.todos.collectAsState()
    val followUps by vm.followUps.collectAsState()
    val scope = rememberCoroutineScope()
    var completingFollowUp by remember { mutableStateOf<FollowUpEntity?>(null) }
    var followUpSaving by remember { mutableStateOf(false) }
    val followUpMediaManager = remember(app, vm.repository) {
        PrivateCaseMediaManager(context, vm.repository, app.container.publicApi, app.container.authSession::isSignedIn)
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
        verticalArrangement = Arrangement.spacedBy(CatSpacing.section),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.lg)) {
                HomeTopBar(
                    onNewCat = onNewCat,
                )
                Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
                    Text(
                        text = "你好，救助人！",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "准备好今天去改变一个小生命了吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                SectionHeader(title = "救助概览", actionText = "查看统计", onAction = onOpenStats)
                StatsGrid(stats = stats, onOpenArchive = onOpenArchive)
            }
        }

        item { DailyEncouragementCard(encouragement) }

        item {
            NearbyAdoptableSection(
                onOpenCommunity = onOpenCommunity,
                onOpenPublicCat = onOpenPublicCat,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                SectionHeader(title = "近期待办", actionText = "全部待办", onAction = onOpenTodos)
                if (todos.isEmpty()) {
                    Text(
                        text = "没有待办，好好休息一下吧 🎉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = CatSpacing.xs),
                    )
                } else {
                    todos.forEach { todo ->
                        val followUp = todoFollowUpId(todo)?.let { id -> followUps.firstOrNull { it.id == id && it.status == FollowUpStatus.PENDING.name } }
                        TodoRow(
                            todo = todo,
                            onToggle = { scope.launch { vm.repository.toggleTodo(todo.id, !todo.done) } },
                            onOpenCat = if (todo.catId != null) ({ onOpenCat(todo.catId!!) }) else null,
                            onCompleteFollowUp = followUp?.let { { completingFollowUp = it } },
                        )
                    }
                }
            }
        }

        item {
            FeatureRecommendationCard(
                onNewCat = onNewCat,
                onOpenTodos = onOpenTodos,
                onOpenStats = onOpenStats,
                onOpenCommunity = onOpenCommunity,
            )
        }
    }
    completingFollowUp?.let { followUp ->
        CompleteFollowUpDialog(
            initialMethod = followUp.method,
            submitting = followUpSaving,
            onDismiss = { if (!followUpSaving) completingFollowUp = null },
            onConfirm = { method, note, statusAtVisit, doneDate, photos ->
                scope.launch {
                    followUpSaving = true
                    followUpMediaManager.completeFollowUp(followUp, method, note, statusAtVisit, doneDate, photos)
                        .onSuccess {
                            completingFollowUp = null
                            if (it.pendingSync && app.container.authSession.isSignedIn()) com.catlink.app.sync.SyncWorker.syncNow(context)
                            CatNoticeCenter.show(if (it.pendingSync) "回访已保存，截图将在联网后同步" else "回访已保存到猫咪档案")
                        }
                        .onFailure { CatNoticeCenter.show(it.message ?: "回访保存失败", error = true) }
                    followUpSaving = false
                }
            },
        )
    }
}

@Composable
private fun HomeTopBar(onNewCat: () -> Unit) {
    val breath = rememberInfiniteTransition(label = "home-top-breath")
    val pawBreath by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-paw-breath-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CatSizes.touchTarget),
    ) {
        // 首页左上角保留原有猫爪印，不与内容运营头像混用。
        Icon(
            imageVector = Icons.Outlined.Pets,
            contentDescription = "首页",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(24.dp)
                .graphicsLayer {
                    val scale = 0.93f + pawBreath * 0.17f
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.58f + pawBreath * 0.42f
                },
        )
        // 标题绝对居中
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center),
        )
        // 恢复首页高频建档入口；通知由全局吸边浮球承载。
		PulsingAddButton(
			contentDescription = "新建档案",
			onClick = onNewCat,
			modifier = Modifier.align(Alignment.CenterEnd),
		)
    }
}

/** 首页核心摘要：稳定的 2×2 网格，保证数字和标签都有舒展空间。 */
@Composable
private fun StatsGrid(
    stats: com.catlink.app.data.HomeStats,
    onOpenArchive: (com.catlink.app.ui.navigation.ArchiveFilterRequest) -> Unit,
) {
    val extras = CatLinkThemeColors.extras
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            StatCard(
                value = stats.rescued,
                label = "已救助",
                icon = { Icon(Icons.Outlined.Favorite, null, tint = extras.onMint, modifier = Modifier.size(18.dp)) },
                iconBg = extras.mint,
                modifier = Modifier.weight(1f),
                onClick = { onOpenArchive(com.catlink.app.ui.navigation.ArchiveFilterRequest(emptySet(), null)) },
            )
            StatCard(
                value = stats.adopted,
                label = "已领养",
                icon = { Icon(Icons.Outlined.Home, null, tint = extras.onSelection, modifier = Modifier.size(18.dp)) },
                iconBg = extras.selection,
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenArchive(
                        com.catlink.app.ui.navigation.ArchiveFilterRequest(
                            setOf(com.catlink.app.data.CatStatus.ADOPTED),
                            null,
                        )
                    )
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            StatCard(
                value = stats.readyForAdoption,
                label = "待领养",
                icon = { Icon(Icons.Outlined.PendingActions, null, tint = extras.onWarm, modifier = Modifier.size(18.dp)) },
                iconBg = extras.warm,
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenArchive(
                        com.catlink.app.ui.navigation.ArchiveFilterRequest(
                            setOf(com.catlink.app.data.CatStatus.ADOPTABLE),
                            null,
                        )
                    )
                },
            )
            StatCard(
                value = stats.treating,
                label = "治疗中",
                icon = { Icon(Icons.Outlined.LocalHospital, null, tint = extras.onNeutral, modifier = Modifier.size(18.dp)) },
                iconBg = extras.neutral,
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenArchive(
                        com.catlink.app.ui.navigation.ArchiveFilterRequest(
                            setOf(com.catlink.app.data.CatStatus.TREATING),
                            null,
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun StatCard(
    value: Int,
    label: String,
    icon: @Composable () -> Unit,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SoftCard(modifier = modifier, shape = RoundedCornerShape(CatRadii.card), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = CatSpacing.md, vertical = CatSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(CatSizes.iconButton)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DailyEncouragementCard(encouragement: DailyEncouragementProvider.Result?) {
    val extras = CatLinkThemeColors.extras
    val shape = RoundedCornerShape(CatRadii.prominent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(extras.warm.copy(alpha = 0.62f))
            .padding(CatSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CatSpacing.md),
    ) {
        BrandLogoAvatar(
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(56.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            Text(
                text = "今日份温柔语句",
                style = MaterialTheme.typography.labelMedium,
                color = extras.onWarm,
            )
            Text(
                text = encouragement?.text ?: "正在准备今天的温柔语句…",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onOpenCat: (() -> Unit)?,
    onCompleteFollowUp: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    val extras = CatLinkThemeColors.extras
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CatSizes.touchTarget)
            .clip(RoundedCornerShape(CatRadii.control))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                CatSpacing.hairline,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(CatRadii.control),
            )
            .clickable { showMenu = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CatSpacing.sm, vertical = CatSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 仅展示完成状态；整行点击打开待办操作，不伪装成可切换 Checkbox。
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (todo.done) extras.mint else Color.Transparent)
                    .border(CatSpacing.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (todo.done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已完成",
                        tint = extras.onMint,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(CatSpacing.sm))
            // 近期待办只显示标题
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (todo.priority == Priority.HIGH.name) {
                StatusChip(
                    text = "紧急",
                    container = extras.warm,
                    content = extras.onWarm,
                )
            } else if (todo.dueEpochDay != null) {
                Text(
                    text = LocalDate.ofEpochDay(todo.dueEpochDay).format(DateTimeFormatter.ofPattern("M/d")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showMenu) {
        CatDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(if (onCompleteFollowUp != null) "完成回访" else if (todo.done) "恢复待办？" else "待办操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (todo.dueEpochDay != null) {
                        Text(
                            text = "截止 ${LocalDate.ofEpochDay(todo.dueEpochDay).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (onOpenCat != null) {
                        TextButton(onClick = { showMenu = false; onOpenCat() }) { Text("查看猫咪档案") }
                    }
                    if (onCompleteFollowUp != null) {
                        TextButton(onClick = { showMenu = false; onCompleteFollowUp() }) { Text("完成回访") }
                    } else {
                        TextButton(onClick = { showMenu = false; onToggle() }) { Text(if (todo.done) "恢复未完成" else "标记为完成") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMenu = false }) { Text("取消") }
            },
        )
    }
}

private fun todoFollowUpId(todo: TodoEntity): String? = todo.subtitle
    .split('|')
    .firstOrNull { it.startsWith("followup:") }
    ?.removePrefix("followup:")

@Composable
private fun FeatureRecommendationCard(
    onNewCat: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCommunity: () -> Unit,
) {
    val recommendation = remember { kotlin.random.Random.nextInt(4) }
    val title = listOf("记录猫咪档案", "整理近期待办", "查看救助统计", "浏览救助社区")[recommendation]
    val subtitle = listOf("照片和资料可离线保存", "集中处理提醒与回访", "了解档案、健康与回访进度", "发现附近猫咪和最新动态")[recommendation]
    val actionText = listOf("建档", "查看", "统计", "进入")[recommendation]
    val onClick = listOf(onNewCat, onOpenTodos, onOpenStats, onOpenCommunity)[recommendation]
    val icon = listOf(Icons.AutoMirrored.Outlined.EventNote, Icons.Outlined.PendingActions, Icons.Outlined.Favorite, Icons.Outlined.Pets)[recommendation]
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CatRadii.card),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(CatSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(CatSizes.iconButton)
                    .clip(CircleShape)
                    .background(CatLinkThemeColors.extras.selection),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CatLinkThemeColors.extras.onSelection,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}


/** 待领养猫咪：加载服务端公开档案（游客可读），横向卡片展示。 */
@Composable
private fun NearbyAdoptableSection(
    onOpenCommunity: () -> Unit,
    onOpenPublicCat: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf<List<com.catlink.app.data.PublicCatItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var nearbyCity by remember { mutableStateOf("") }
    var nearbyDistrict by remember { mutableStateOf("") }
    var nearbyProvince by remember { mutableStateOf("") }
    var nearbyStreet by remember { mutableStateOf("") }
    var nearbyCommunity by remember { mutableStateOf("") }
    var nearbyLatitude by remember { mutableStateOf<Double?>(null) }
    var nearbyLongitude by remember { mutableStateOf<Double?>(null) }
    var nearbyAttribution by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    val loadPreciseArea: suspend () -> Unit = {
        NearbyLocationResolver.currentArea(context, app.container.publicApi::reverseLocation).fold(
            onSuccess = { area ->
                nearbyCity = area.city
                nearbyDistrict = area.district
                nearbyProvince = area.province
                nearbyStreet = area.street
                nearbyCommunity = area.community
                nearbyLatitude = area.latitude
                nearbyLongitude = area.longitude
                nearbyAttribution = "设备精确定位"
            },
            onFailure = { loaded = true },
        )
    }
    val locatePrecisely = {
        scope.launch {
            locating = true
            loadPreciseArea()
            locating = false
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locatePrecisely() else loaded = true
    }

    LaunchedEffect(Unit) {
        locating = true
        NearbyLocationResolver.ipArea(app.container.publicApi::ipLocation).fold(
            onSuccess = { area ->
                nearbyCity = area.city
                nearbyDistrict = area.district
                nearbyProvince = area.province
                nearbyStreet = area.street
                nearbyCommunity = area.community
                nearbyLatitude = area.latitude
                nearbyLongitude = area.longitude
                nearbyAttribution = area.attribution
            },
            onFailure = { if (NearbyLocationResolver.hasPermission(context)) loadPreciseArea() else loaded = true },
        )
        locating = false
    }
    LaunchedEffect(nearbyProvince, nearbyCity, nearbyDistrict, nearbyStreet, nearbyCommunity, nearbyLatitude, nearbyLongitude) {
        if (nearbyCity.isBlank()) return@LaunchedEffect
        app.container.publicApi.listPublicCats(
            com.catlink.app.data.PublicCatFilters(
                province = nearbyProvince,
                city = nearbyCity,
                district = nearbyDistrict,
                street = nearbyStreet,
                community = nearbyCommunity,
                status = "all",
                limit = 8,
            ),
        ).fold(
            onSuccess = { cats = it; loaded = true },
            onFailure = { loaded = true },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        SectionHeader(
            title = "附近猫咪",
            actionText = "去社区看看",
            onAction = onOpenCommunity,
        )
        if (!loaded) {
            Text(
                text = "正在获取附近猫咪…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (nearbyCity.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                Text("使用当前位置查看所在区县的猫咪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(enabled = !locating, onClick = {
                    if (NearbyLocationResolver.hasPermission(context)) locatePrecisely()
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(if (locating) "定位中…" else "精确定位") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(nearbyCity, nearbyDistrict, nearbyStreet, nearbyCommunity)
                        .filter(String::isNotBlank)
                        .joinToString(" · ") + if (nearbyAttribution.isBlank()) "" else " · $nearbyAttribution",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = !locating, onClick = {
                    if (NearbyLocationResolver.hasPermission(context)) locatePrecisely()
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text("精确定位") }
            }
        }
        if (nearbyCity.isNotBlank() && cats.isEmpty()) {
            Text("${nearbyCity}${nearbyDistrict}暂无公开猫咪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (nearbyCity.isNotBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                items(cats.take(8), key = { it.id }) { cat ->
                    NearbyCatCard(cat = cat, onClick = { onOpenPublicCat(cat.id) })
                }
            }
        }
    }
}

@Composable
private fun NearbyCatCard(
    cat: com.catlink.app.data.PublicCatItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(CatRadii.card))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.card))
            .clickable(onClick = onClick)
            .padding(CatSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (cat.coverUrl.isNotBlank()) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context).data(cat.coverUrl).size(160).build(),
                contentDescription = cat.nickname,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            com.catlink.app.ui.components.CatAvatar(index = cat.id.hashCode().absoluteValue, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(50))
        }
        Text(
            cat.nickname,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            com.catlink.app.data.CatStatus.fromName(cat.statusCode).let { status ->
                val archive = com.catlink.app.data.ArchiveReason.fromNameOrNull(cat.archiveReason)
                if (status == com.catlink.app.data.CatStatus.ARCHIVED && archive != null) "${status.label} · ${archive.label}" else status.label
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.primary,
        )
        Text(
            com.catlink.app.data.RegionData.locationLabel(cat.cityCode, cat.districtCode),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
