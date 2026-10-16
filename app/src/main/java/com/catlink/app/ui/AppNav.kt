package com.catlink.app.ui

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.CatStatus
import com.catlink.app.data.HealthStatus
import com.catlink.app.ui.screens.about.AboutScreen
import com.catlink.app.ui.screens.adopters.AdoptersScreen
import com.catlink.app.ui.screens.ai.AiChatScreen
import com.catlink.app.ui.screens.ai.AiSettingsScreen
import com.catlink.app.ui.screens.archive.ArchiveScreen
import com.catlink.app.ui.screens.backup.BackupScreen
import com.catlink.app.ui.screens.casedetail.CaseDetailScreen
import com.catlink.app.ui.screens.catdetail.CatDetailScreen
import com.catlink.app.ui.screens.export.ExportScreen
import com.catlink.app.ui.screens.home.HomeScreen
import com.catlink.app.ui.screens.lock.LockScreen
import com.catlink.app.ui.screens.newcat.NewCatScreen
import com.catlink.app.ui.screens.poster.PosterScreen
import com.catlink.app.ui.screens.profile.ProfileScreen
import com.catlink.app.ui.screens.stats.StatsScreen
import com.catlink.app.ui.screens.todo.TodoListScreen
import com.catlink.app.ui.screens.trash.TrashScreen
import com.catlink.app.ui.theme.CatLinkTheme
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.ThemeStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 底部导航五页（社区已改为 AI 对话） */
enum class TopTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Outlined.Home),
    ARCHIVE("archive", "档案", Icons.Outlined.MenuBook),
    STATS("stats", "统计", Icons.Outlined.QueryStats),
    AI("ai", "AI", Icons.Outlined.SmartToy),
    PROFILE("profile", "我的", Icons.Outlined.Person),
}

object Routes {
    const val MAIN = "main"
    const val CAT_DETAIL = "cat/{catId}"
    const val NEW_CAT = "newcat"
    const val EDIT_CAT = "editcat/{catId}"
    const val TRASH = "trash"
    const val ADOPTERS = "adopters"
    const val CASE_DETAIL = "case/{caseId}"
    const val POSTER = "poster"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val THEME = "theme"
    const val SYNC = "sync"
    const val TODOS = "todos"
    const val AI_SETTINGS = "ai_settings"
    const val EXPORT = "export"

    fun catDetail(id: String) = "cat/$id"
    fun editCat(id: String) = "editcat/$id"
    fun caseDetail(id: String) = "case/$id"
}

@Composable
fun CatLinkApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val settings = app.container.settings

    val themeStyle by settings.themeStyle.collectAsState(initial = ThemeStyle.WHITE)

    CatLinkTheme(themeStyle) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // 仅在主容器页显示底部栏（二级页隐藏）
        val showBottomBar = currentRoute == Routes.MAIN || currentRoute == null

        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { TopTab.entries.size })
        // 统计页点击模块 → 档案页预筛选
        var archiveInitial by remember {
            mutableStateOf<Pair<Set<CatStatus>, HealthStatus?>?>(null)
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    CatLinkPagerBottomBar(
                        pagerState = pagerState,
                        onSelect = { page ->
                            scope.launch { pagerState.animateScrollToPage(page) }
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                // 主页面：左右滑动切换 5 个 tab
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            vm = vm,
                            onOpenArchive = { scope.launch { pagerState.animateScrollToPage(1) } },
                            onOpenTodos = { navController.navigate(Routes.TODOS) },
                            onOpenCat = { id -> navController.navigate(Routes.catDetail(id)) },
                            onNewCat = { navController.navigate(Routes.NEW_CAT) },
                        )
                        1 -> {
                            val cats by vm.cats.collectAsState()
                            ArchiveScreen(
                                cats = cats,
                                onOpenCat = { id -> navController.navigate(Routes.catDetail(id)) },
                                onNewCat = { navController.navigate(Routes.NEW_CAT) },
                                initialStatuses = archiveInitial?.first,
                                initialHealth = archiveInitial?.second,
                            )
                        }
                        2 -> {
                            val stats by vm.stats.collectAsState()
                            val cats by vm.cats.collectAsState()
                            StatsScreen(
                                stats = stats,
                                cats = cats,
                                onOpenArchive = { statuses, health ->
                                    archiveInitial = statuses to health
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                },
                            )
                        }
                        3 -> AiChatScreen(
                            onOpenSettings = { navController.navigate(Routes.AI_SETTINGS) },
                        )
                        4 -> ProfileScreen(
                            vm = vm,
                            onOpenTrash = { navController.navigate(Routes.TRASH) },
                            onOpenBackup = { navController.navigate(Routes.BACKUP) },
                            onOpenAdopters = { navController.navigate(Routes.ADOPTERS) },
                            onOpenPoster = { navController.navigate(Routes.POSTER) },
                            onOpenTheme = { navController.navigate(Routes.THEME) },
                            onOpenSync = { navController.navigate(Routes.SYNC) },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) },
                            onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                            onOpenExport = { navController.navigate(Routes.EXPORT) },
                        )
                    }
                }

                // 二级页 NavHost（透明主容器 + 详情/表单页）
                NavHost(
                    navController = navController,
                    startDestination = Routes.MAIN,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(120)) },
                    popExitTransition = { fadeOut(tween(180)) },
                ) {
                    composable(Routes.MAIN) { Box(Modifier.fillMaxSize()) }
                    composable(Routes.CAT_DETAIL) { entry ->
                        val catId = entry.arguments?.getString("catId") ?: return@composable
                        SecondaryPage {
                            CatDetailScreen(
                                catId = catId,
                                onBack = { navController.popBackStack() },
                                onEdit = { navController.navigate(Routes.editCat(catId)) },
                                onOpenCase = { caseId -> navController.navigate(Routes.caseDetail(caseId)) },
                            )
                        }
                    }
                    composable(Routes.NEW_CAT) {
                        SecondaryPage { NewCatScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.EDIT_CAT) { entry ->
                        val catId = entry.arguments?.getString("catId") ?: return@composable
                        SecondaryPage { NewCatScreen(catId = catId, onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.TRASH) {
                        SecondaryPage { TrashScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.ADOPTERS) {
                        SecondaryPage { AdoptersScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.CASE_DETAIL) { entry ->
                        val caseId = entry.arguments?.getString("caseId") ?: return@composable
                        SecondaryPage {
                            CaseDetailScreen(caseId = caseId, onBack = { navController.popBackStack() })
                        }
                    }
                    composable(Routes.POSTER) {
                        SecondaryPage { PosterScreen(vm = vm, onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.BACKUP) {
                        SecondaryPage { BackupScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.ABOUT) {
                        SecondaryPage { AboutScreen(onBack = { navController.popBackStack() }) }
                    }
                    composable(Routes.THEME) {
                        SecondaryPage {
                            com.catlink.app.ui.screens.theme.ThemeScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable(Routes.SYNC) {
                        SecondaryPage {
                            com.catlink.app.ui.screens.sync.SyncScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable(Routes.TODOS) {
                        SecondaryPage {
                            TodoListScreen(
                                vm = vm,
                                onBack = { navController.popBackStack() },
                                onOpenCat = { id -> navController.navigate(Routes.catDetail(id)) },
                            )
                        }
                    }
                    composable(Routes.AI_SETTINGS) {
                        SecondaryPage {
                            AiSettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable(Routes.EXPORT) {
                        SecondaryPage {
                            ExportScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部导航（悬浮胶囊版）：高度降低的悬浮圆角 dock，
 * 粉 pill 跟随 Pager 滑动连续移动，点击无灰色按压效果，pill 加宽一点点。
 */
@Composable
private fun CatLinkPagerBottomBar(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val itemWidth = maxWidth / TopTab.entries.size
        val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
        // 连续滑动位置：currentPage + offsetFraction（滑动时跟随手指）
        val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = CatLinkThemeColors.extras.warmShadow,
                    spotColor = CatLinkThemeColors.extras.warmShadow,
                ),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerLowest,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                // 滑动选中指示器（粉 pill，加宽一点点），跟随 Pager 滑动连续移动
                Box(
                    modifier = Modifier
                        .offset { IntOffset((pageOffset * itemWidthPx).roundToInt(), 0) }
                        .padding(horizontal = 3.dp)
                        .fillMaxHeight()
                        .width(itemWidth - 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.4f)),
                )
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    TopTab.entries.forEachIndexed { index, tab ->
                        val selected = pagerState.currentPage == index
                        val tint by animateColorAsState(
                            targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            animationSpec = tween(200),
                        )
                        // 无灰色按压效果：indication = null
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 二级页包装：不透明背景（避免透出下层 Pager 内容）。
 * NavHost 叠加在 HorizontalPager 之上，页面根容器透明会透出主页面，必须包背景。
 */
@Composable
private fun SecondaryPage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

/** 获取应用容器（供页面直接使用 repository/settings） */
