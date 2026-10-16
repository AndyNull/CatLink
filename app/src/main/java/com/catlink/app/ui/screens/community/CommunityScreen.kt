package com.catlink.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlink.app.domain.community.CommunityFeed
import com.catlink.app.data.NearbyLocationResolver
import com.catlink.app.runtime.LocalAppRuntimeState
import com.catlink.app.runtime.NetworkRuntimeState
import com.catlink.app.runtime.SessionRuntimeState
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatButtonStyle
import com.catlink.app.ui.components.CatIconButton
import com.catlink.app.ui.components.PulsingAddButton
import com.catlink.app.ui.components.UserAvatar
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSizes
import androidx.compose.material.icons.outlined.Check
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.delay

private enum class TopicFilter(val label: String) {
    ALL("全部"),
    RESCUE("救助"),
    SEEKING_ADOPTION("求领养"),
}

private val communityFeedRefreshVersion = mutableLongStateOf(0L)

internal fun requestCommunityFeedRefresh() {
    communityFeedRefreshVersion.longValue++
}

/** 社区：加载服务端公开 feed（游客可读），只展示公开域。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onSignIn: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenPost: (String, Boolean) -> Unit = { _, _ -> },
    onOpenUser: (Long) -> Unit = {},
    onOpenPublicCat: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.catlink.app.CatLinkApplication
    val scope = rememberCoroutineScope()
    val runtime = LocalAppRuntimeState.current
    val signedInSession = runtime.session as? SessionRuntimeState.SignedIn
    val signedIn = signedInSession?.emailVerified == true
    val currentAccountId = signedInSession?.accountId.orEmpty()
    val offline = runtime.network == NetworkRuntimeState.Offline
    val refreshToken = communityFeedRefreshVersion.longValue
    var lastFeedRefreshToken by remember { mutableLongStateOf(refreshToken) }
    var lastCatRefreshToken by remember { mutableLongStateOf(refreshToken) }
    var feed by remember { mutableStateOf(CommunityFeed.PUBLIC) }
    var topic by remember { mutableStateOf(TopicFilter.ALL) }
    var posts by remember { mutableStateOf<List<com.catlink.app.data.CommunityFeedItem>>(emptyList()) }
    var publicCats by remember { mutableStateOf<List<com.catlink.app.data.PublicCatItem>>(emptyList()) }
    var publicUsers by remember { mutableStateOf<List<com.catlink.app.data.PublicApi.PublicUserItem>>(emptyList()) }
    var searchInput by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf("") }
    var publicCatsLoading by remember { mutableStateOf(false) }
    var publicUsersLoading by remember { mutableStateOf(false) }
    var publicCatsError by remember { mutableStateOf<String?>(null) }
    var publicUsersError by remember { mutableStateOf<String?>(null) }
    var followPendingAccountId by remember { mutableStateOf<String?>(null) }
    val screenCache = remember { mutableStateMapOf<String, List<com.catlink.app.data.CommunityFeedItem>>() }
    var feedLoading by remember { mutableStateOf(true) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var nearbyCity by remember { mutableStateOf("") }
    var nearbyDistrict by remember { mutableStateOf("") }
    var nearbyLatitude by remember { mutableStateOf<Double?>(null) }
    var nearbyLongitude by remember { mutableStateOf<Double?>(null) }
    var radiusKm by remember { mutableIntStateOf(3) }
    var locating by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(searchInput) {
        delay(350)
        searchKeyword = searchInput.trim()
    }
    val loadPreciseArea: suspend () -> Unit = {
        NearbyLocationResolver.currentArea(context, app.container.publicApi::reverseLocation).fold(
            onSuccess = { area ->
                nearbyError = null
                nearbyCity = area.city
                nearbyDistrict = area.district
                nearbyLatitude = area.latitude
                nearbyLongitude = area.longitude
                feed = CommunityFeed.NEARBY
            },
            onFailure = { nearbyError = it.message ?: "定位失败，请检查系统定位服务" },
        )
    }

    val requestNearby = {
        feed = CommunityFeed.NEARBY
        scope.launch {
            locating = true
            nearbyError = null
            NearbyLocationResolver.ipArea(app.container.publicApi::ipLocation).fold(
                onSuccess = { area ->
                    nearbyError = null
                    nearbyCity = area.city
                    nearbyDistrict = area.district
                    nearbyLatitude = area.latitude
                    nearbyLongitude = area.longitude
                },
                onFailure = {
                    if (NearbyLocationResolver.hasPermission(context)) loadPreciseArea()
                    else nearbyError = it.message ?: "定位失败，请下拉重试"
                },
            )
            locating = false
        }
    }

    // 社区广告位：后台未配置 community 广告时为空，列表不插入广告
    var communityAd by remember { mutableStateOf<com.catlink.app.data.PublicApi.AdItem?>(null) }
    LaunchedEffect(Unit) {
        app.container.publicApi.adConfig("community").getOrNull()?.firstOrNull()?.let { communityAd = it }
    }

    LaunchedEffect(feed, topic, signedIn, nearbyCity, nearbyDistrict, nearbyLatitude, nearbyLongitude, radiusKm, refreshToken) {
        if (feed == CommunityFeed.PUBLIC) {
            feedLoading = false
            feedError = null
            lastFeedRefreshToken = refreshToken
            refreshing = false
            return@LaunchedEffect
        }
        if (feed == CommunityFeed.NEARBY && nearbyCity.isBlank()) {
            posts = emptyList()
            feedLoading = false
            refreshing = false
            return@LaunchedEffect
        }
        feedLoading = posts.isEmpty()
        feedError = null
        val topicCode = when (topic) {
            TopicFilter.RESCUE -> "rescue"
            TopicFilter.SEEKING_ADOPTION -> "seeking_adoption"
            TopicFilter.ALL -> ""
        }
        val cacheKey = "${feed.name}:$topicCode:$nearbyCity:$nearbyDistrict:$nearbyLatitude:$nearbyLongitude:$radiusKm:$signedIn"
        if (feed == CommunityFeed.FOLLOWING && !signedIn) {
            posts = emptyList()
            feedLoading = false
            refreshing = false
            return@LaunchedEffect
        }
        val cached = screenCache[cacheKey] ?: when (feed) {
            CommunityFeed.PUBLIC -> null
            CommunityFeed.FOLLOWING -> app.container.publicApi.cachedFollowingFeed(topicCode)
            CommunityFeed.LATEST -> app.container.publicApi.cachedFeed(topicCode, authenticated = signedIn)
            CommunityFeed.NEARBY -> app.container.publicApi.cachedFeed(topicCode, nearbyCity, nearbyDistrict, signedIn)
        }
        if (cached != null) {
            posts = cached
            feedLoading = false
        } else {
            posts = emptyList()
            feedLoading = true
        }
        val forceRefresh = refreshToken != lastFeedRefreshToken
        lastFeedRefreshToken = refreshToken
        val result = when (feed) {
            CommunityFeed.PUBLIC -> Result.success(emptyList())
            CommunityFeed.FOLLOWING -> app.container.publicApi.followingFeed(topicCode, forceRefresh = forceRefresh)
            CommunityFeed.LATEST -> app.container.publicApi.feedPage(topicCode, authenticated = signedIn, forceRefresh = forceRefresh).map { it.items }
            CommunityFeed.NEARBY -> app.container.publicApi.feedPage(
                topic = topicCode,
                city = if (nearbyLatitude == null) nearbyCity else "",
                district = if (nearbyLatitude == null) nearbyDistrict else "",
                authenticated = signedIn,
                forceRefresh = forceRefresh,
                latitude = nearbyLatitude,
                longitude = nearbyLongitude,
                radiusKm = radiusKm.takeIf { nearbyLatitude != null && nearbyLongitude != null },
            ).map { it.items }
        }
        result.fold(
            onSuccess = { fresh ->
                posts = fresh
                screenCache[cacheKey] = posts
                feedLoading = false
            },
            onFailure = {
                feedError = (it as? com.catlink.app.network.OnlineErrorException)?.error?.userMessage ?: it.message
                feedLoading = false
            },
        )
        refreshing = false
    }

    LaunchedEffect(feed, topic, nearbyCity, nearbyDistrict, nearbyLatitude, nearbyLongitude, radiusKm, searchKeyword, refreshToken) {
        if (feed != CommunityFeed.PUBLIC) {
            publicCats = emptyList()
            publicCatsLoading = false
            publicCatsError = null
            if (refreshToken != lastCatRefreshToken) {
                lastCatRefreshToken = refreshToken
                refreshing = false
            }
            return@LaunchedEffect
        }
        publicCatsLoading = true
        publicCatsError = null
        val filters = com.catlink.app.data.PublicCatFilters(
            status = when (topic) {
                TopicFilter.ALL -> "all"
                TopicFilter.RESCUE -> "rescue"
                TopicFilter.SEEKING_ADOPTION -> "adoptable"
            },
            keyword = searchKeyword,
            limit = if (searchKeyword.isBlank()) 10 else 20,
        )
        val forceRefresh = refreshToken != lastCatRefreshToken
        lastCatRefreshToken = refreshToken
        app.container.publicApi.listPublicCats(filters, forceRefresh = forceRefresh).fold(
            onSuccess = { publicCats = it },
            onFailure = {
                publicCats = emptyList()
                if (searchKeyword.isNotBlank()) publicCatsError = it.message ?: "猫咪搜索失败"
            },
        )
        publicCatsLoading = false
        refreshing = false
    }

    LaunchedEffect(searchKeyword, signedIn) {
        if (searchKeyword.isBlank()) {
            publicUsers = emptyList()
            publicUsersLoading = false
            publicUsersError = null
            return@LaunchedEffect
        }
        publicUsersLoading = true
        publicUsersError = null
        app.container.publicApi.publicUsers(searchKeyword, pageSize = 10, authenticated = signedIn).fold(
            onSuccess = { publicUsers = it.items },
            onFailure = {
                publicUsers = emptyList()
                publicUsersError = it.message ?: "用户搜索失败"
            },
        )
        publicUsersLoading = false
    }

    val searchError = publicUsersError ?: publicCatsError
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            requestCommunityFeedRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 192.dp),
            )
        },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CatSpacing.pageHorizontal,
            end = CatSpacing.pageHorizontal,
            top = CatSpacing.pageTop,
            bottom = 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        item(key = "community-header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
                    Text(
                        text = "社区",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "记录与发现救助故事、公开档案和领养信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (feed != CommunityFeed.PUBLIC) {
                    PulsingAddButton(
                        contentDescription = if (signedIn) "发布动态" else "登录后发布",
                        onClick = {
                            if (signedIn) onCreatePost() else onSignIn()
                        },
                    )
                } else {
                    Spacer(Modifier.size(CatSizes.touchTarget))
                }
            }
        }

        item(key = "community-search", contentType = "search") {
            CatTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = "输入搜索内容",
                leadingIcon = Icons.Outlined.Search,
                trailingIcon = Icons.Outlined.Close.takeIf { searchInput.isNotBlank() },
                onTrailingClick = { searchInput = "" },
                trailingContentDescription = "清空搜索",
                imeAction = ImeAction.Search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CatSpacing.xs).heightIn(min = 48.dp),
            )
        }

        item(key = "community-filters", contentType = "filters") {
            FeedSelector(
                selected = feed,
                onSelect = { selected ->
                    if (selected == CommunityFeed.NEARBY) requestNearby() else feed = selected
                },
                topic = topic,
                onTopicSelect = { topic = it },
                nearbyDistrict = nearbyDistrict,
            )
            if (feed == CommunityFeed.NEARBY) {
                if (nearbyCity.isBlank() && !locating && nearbyError != null) {
                    Text(
                        text = "定位失败，下拉刷新重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(top = CatSpacing.xxs),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    if (locating) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = CatSpacing.xxs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("定位中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (nearbyLatitude != null && nearbyLongitude != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = CatSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            listOf(1, 3, 5).forEach { distance ->
                                TextButton(
                                    onClick = { radiusKm = distance },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text("${distance}km", color = if (radiusKm == distance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (searchKeyword.isNotBlank()) {
            if (publicUsers.isNotEmpty()) {
                item(key = "community-public-users", contentType = "public-users") {
                    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                        Text("用户", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        publicUsers.forEach { user ->
                            CommunityPublicUserCard(
                                user = user,
                                following = followPendingAccountId == user.accountId,
                                onOpen = { user.accountId.toLongOrNull()?.let(onOpenUser) },
                                onFollow = {
                                    if (!signedIn) {
                                        onSignIn()
                                    } else {
                                        scope.launch {
                                            followPendingAccountId = user.accountId
                                            app.container.publicApi.setUserFollow(user.accountId, !user.followed)
                                                .onSuccess { followed ->
                                                    publicUsers = publicUsers.map { item ->
                                                        if (item.accountId == user.accountId) item.copy(followed = followed) else item
                                                    }
                                                }
                                                .onFailure {
                                                    com.catlink.app.ui.components.CatNoticeCenter.show(
                                                        it.message ?: "关注操作失败",
                                                        error = true,
                                                    )
                                                }
                                            followPendingAccountId = null
                                        }
                                    }
                                },
                                showFollow = user.accountId != currentAccountId,
                            )
                        }
                    }
                }
            }
            if (publicCats.isNotEmpty() && feed == CommunityFeed.PUBLIC) item(key = "community-search-cats", contentType = "public-cats") {
                PublicCatRow("猫咪", publicCats, onOpenPublicCat)
            }
            if (!publicUsersLoading && !publicCatsLoading && publicUsers.isEmpty() && publicCats.isEmpty()) {
                item(key = "community-search-empty", contentType = "search-state") {
                    Text(
                        text = searchError ?: "没有找到匹配的用户或猫咪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (searchError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(vertical = CatSpacing.sm),
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (publicUsersLoading || publicCatsLoading) {
                item(key = "community-search-loading", contentType = "search-state") {
                    Text(
                        text = "正在搜索…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = CatSpacing.xs),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (feed == CommunityFeed.PUBLIC) {
            when {
                publicCatsLoading -> item(key = "public-cats-loading") {
                    CommunityLogoState("正在加载公开猫咪", Modifier.padding(vertical = CatSpacing.lg))
                }
                publicCats.isEmpty() -> item(key = "public-cats-empty") {
                    CommunityLogoState(
                        if (publicCatsError == null) "暂无公开猫咪" else "加载失败",
                        Modifier.padding(vertical = CatSpacing.lg),
                        publicCatsError.orEmpty(),
                    )
                }
                else -> items(publicCats, key = { "public-${it.id}" }, contentType = { "public-cat-card" }) { cat ->
                    CommunityPublicCatListCard(cat, onClick = { onOpenPublicCat(cat.id) })
                }
            }
        } else {
            communityAd?.let { ad ->
                item(key = "ad-community", contentType = "advertisement") {
                    com.catlink.app.ui.components.CommunityAdCard(
                        ad = ad,
                        onOpen = {
                            if (ad.linkUrl.isNotBlank()) runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ad.linkUrl)))
                            }
                        },
                    )
                }
            }

            when {
                feedLoading -> item {
                    CommunityLogoState("正在加载动态", Modifier.padding(vertical = CatSpacing.lg))
                }
                posts.isEmpty() && (feedError != null || offline) -> item {
                    CommunityLogoState(
                        if (offline) "网络不可用" else "加载失败",
                        Modifier.padding(vertical = CatSpacing.lg),
                        if (offline) "下拉刷新重试" else (feedError ?: "下拉刷新重试"),
                    )
                }
                posts.isEmpty() -> item {
                    CommunityEmptyState(feed, topic, signedIn, onSignIn)
                }
                else -> items(posts, key = { it.id }, contentType = { "post" }) { post ->
                PostCard(
                    modifier = Modifier.animateItem(),
                    post = post,
                    onClick = { onOpenPost(post.id, false) },
                    onLike = {
                        if (!signedIn) {
                            onSignIn()
                        } else {
                            scope.launch {
                                app.container.publicApi.likePost(post.id)
                                    .onSuccess { liked ->
                                        posts = posts.map { item ->
                                            if (item.id != post.id) item else item.copy(
                                                liked = liked,
                                                likeCount = (item.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                                            )
                                        }
                                    }
                                    .onFailure {
                                        com.catlink.app.ui.components.CatNoticeCenter.show(
                                            it.message ?: "点赞失败，请稍后重试",
                                            error = true,
                                        )
                                    }
                            }
                        }
                    },
                    onComment = { onOpenPost(post.id, true) },
                    onShare = { sharePost(context, post) },
                    onOpenUser = { id -> onOpenUser(id) },
                )
                }
            }
        }
    }
}

}

@Composable
private fun PublicCatRow(
    title: String,
    cats: List<com.catlink.app.data.PublicCatItem>,
    onOpenPublicCat: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
            items(cats, key = { it.id }) { cat ->
                CommunityPublicCatCard(cat = cat, onClick = { onOpenPublicCat(cat.id) })
            }
        }
    }
}

@Composable
private fun CommunityPublicUserCard(
    user: com.catlink.app.data.PublicApi.PublicUserItem,
    following: Boolean,
    onOpen: () -> Unit,
    onFollow: () -> Unit,
    showFollow: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CatRadii.compact))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.compact))
            .clickable(onClick = onOpen)
            .padding(CatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        UserAvatar(
            avatarUrl = user.avatarUrl,
            contentDescription = user.nickname.ifBlank { user.username },
            modifier = Modifier.size(46.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
            Text(
                text = user.nickname.ifBlank { user.username },
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.username.isNotBlank()) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "公开猫咪 ${user.publicCats} · 动态 ${user.posts}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
            )
        }
        if (showFollow) {
            CatButton(
                text = if (following) "处理中" else if (user.followed) "已关注" else "关注",
                onClick = onFollow,
                style = if (user.followed) CatButtonStyle.Secondary else CatButtonStyle.Primary,
                enabled = !following,
                height = 36.dp,
            )
        }
    }
}

@Composable
private fun CommunityPublicCatCard(
    cat: com.catlink.app.data.PublicCatItem,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(116.dp)
            .clip(RoundedCornerShape(CatRadii.compact))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.compact))
            .clickable(onClick = onClick)
            .padding(CatSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs),
    ) {
        com.catlink.app.ui.components.CatAvatar(
            index = cat.id.hashCode().absoluteValue,
            coverUri = cat.coverUrl.ifBlank { null },
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
        )
        Text(cat.nickname, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val location = cat.distanceKm?.let { "${it}km" }
            ?: listOf(cat.districtCode, cat.community).firstOrNull(String::isNotBlank).orEmpty()
        if (location.isNotBlank()) {
            Text(location, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CommunityPublicCatListCard(
    cat: com.catlink.app.data.PublicCatItem,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val status = com.catlink.app.data.CatStatus.fromName(cat.statusCode.ifBlank { cat.catStatus })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CatRadii.control))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.control))
            .clickable(onClick = onClick)
            .padding(CatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        com.catlink.app.ui.components.CatAvatar(
            index = cat.id.hashCode().absoluteValue,
            coverUri = cat.coverUrl.ifBlank { null },
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(CatRadii.compact),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                Text(cat.nickname, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val colors = com.catlink.app.ui.components.catStatusColors(status)
                com.catlink.app.ui.components.StatusChip(status.label, container = colors.first, content = colors.second, compact = true)
            }
            val facts = listOf(cat.gender, cat.ageBand, cat.coat).filter(String::isNotBlank).joinToString(" · ")
            if (facts.isNotBlank()) Text(facts, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val location = cat.distanceKm?.let { "${it}km" }
                ?: listOf(cat.community, cat.street, cat.districtCode, cat.cityCode).firstOrNull(String::isNotBlank).orEmpty()
            val update = cat.latestTimeline?.body.orEmpty()
            val summary = listOf(location, update).filter(String::isNotBlank).joinToString(" · ")
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.labelSmall, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = scheme.outline)
    }
}

/** 帖子卡片：只展示公开字段，支持点赞与评论。 */
@Composable
private fun PostCard(
    post: com.catlink.app.data.CommunityFeedItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onOpenUser: (Long) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val extras = com.catlink.app.ui.theme.CatLinkThemeColors.extras
    val date = remember(post.createdAt) {
        java.time.Instant.ofEpochMilli(post.createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("M月d日"))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CatRadii.card))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.card))
            .padding(CatSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
            if (post.authorName.isNotBlank()) {
                Text(
                    post.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { post.authorAccountId.toLongOrNull()?.let(onOpenUser) }
                        .padding(vertical = 2.dp),
                )
            }
            Column(
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClick,
                ),
                verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (post.body.isNotBlank()) {
                Text(
                    post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 多图预览：支持历史分隔符，避免整串 URL 被当成一张图。
            com.catlink.app.ui.components.PostMediaPreview(
                mediaUrls = post.mediaUrls,
                contentDescription = post.title,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                val topicLabel = when (post.topic) {
                    "rescue" -> "救助"
                    "seeking_adoption" -> "求领养"
                    else -> "交流"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CatRadii.pill))
                        .background(extras.selection.copy(alpha = 0.34f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(topicLabel, style = MaterialTheme.typography.labelSmall, color = extras.onSelection)
                }
                if (post.cityCode.isNotBlank() || post.districtCode.isNotBlank()) {
                    Text(
                        com.catlink.app.data.RegionData.locationLabel(post.cityCode, post.districtCode, ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.outline,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
            }
            }
            // 互动：点赞和评论可直接操作，正文区域进入详情。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CatRadii.pill))
                        .clickable(onClick = onLike)
                        .heightIn(min = CatSizes.touchTarget)
                        .padding(horizontal = CatSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (post.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "点赞",
                        tint = if (post.liked) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(" ${post.likeCount}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CatRadii.pill))
                        .clickable(onClick = onComment)
                        .heightIn(min = CatSizes.touchTarget)
                        .padding(horizontal = CatSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "评论", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(" ${post.commentCount}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(CatSizes.touchTarget)) {
                    Icon(Icons.Outlined.Share, "分享", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedSelector(
    selected: CommunityFeed,
    onSelect: (CommunityFeed) -> Unit,
    topic: TopicFilter,
    onTopicSelect: (TopicFilter) -> Unit,
    nearbyDistrict: String,
) {
    val scheme = MaterialTheme.colorScheme
    val labels = mapOf(
        CommunityFeed.PUBLIC to "公开",
        CommunityFeed.FOLLOWING to "关注",
        CommunityFeed.LATEST to "最新",
        CommunityFeed.NEARBY to if (nearbyDistrict.isBlank()) "附近" else "附近·$nearbyDistrict",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CatRadii.pill))
            .background(scheme.surfaceContainerLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityFeed.entries.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .weight(if (item == CommunityFeed.NEARBY) 1.35f else 0.8f)
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(CatRadii.pill))
                    .background(if (active) scheme.surfaceContainerLowest else Color.Transparent)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    labels[item] ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TopicDropdown(topic, onTopicSelect, Modifier.weight(0.8f))
    }
}

/** 分类下拉：图标按钮 + 与按钮等宽的无缝拼接菜单。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TopicDropdown(
    topic: TopicFilter,
    onSelect: (TopicFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 当前选中值 + 展开箭头（点击弹出统一下拉）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(com.catlink.app.ui.theme.CatRadii.pill))
                .background(if (topic == TopicFilter.ALL) Color.Transparent else scheme.surfaceContainerLowest)
                .clickable { expanded = true }
                .heightIn(min = 32.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(if (topic == TopicFilter.ALL) "全部" else topic.label, style = MaterialTheme.typography.labelSmall, color = if (topic == TopicFilter.ALL) scheme.onSurfaceVariant else scheme.onSurface)
            Text("▾", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
        }
        com.catlink.app.ui.components.CatDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = TopicFilter.entries.map { t ->
                com.catlink.app.ui.components.CatDropdownItem(
                    label = t.label,
                    selected = t == topic,
                    onClick = { onSelect(t) },
                )
            },
        )
    }
}

@Composable
private fun CommunityEmptyState(
    feed: CommunityFeed,
    topic: TopicFilter,
    signedIn: Boolean,
    onSignIn: () -> Unit,
) {
    val extras = CatLinkThemeColors.extras
    val message = when (feed) {
        CommunityFeed.PUBLIC -> "暂时没有公开猫咪。"
        CommunityFeed.FOLLOWING -> "关注后，公开动态会显示在这里。"
        CommunityFeed.LATEST -> "暂时没有最新动态。"
        CommunityFeed.NEARBY -> "附近暂时没有公开动态。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CatSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(extras.mint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (topic == TopicFilter.SEEKING_ADOPTION) Icons.Outlined.Pets else Icons.Outlined.Groups,
                contentDescription = null,
                tint = extras.onMint,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            text = if (signedIn) "还没有相关内容" else "登录后连接救助社区",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!signedIn && feed == CommunityFeed.FOLLOWING) {
            CatButton(
                text = "登录或注册",
                onClick = onSignIn,
                style = CatButtonStyle.Secondary,
                height = 40.dp,
                modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
            )
        }
    }
}

/** 系统分享帖子文本。 */
private fun sharePost(context: android.content.Context, post: com.catlink.app.data.CommunityFeedItem) {
    val topic = when (post.topic) {
        "rescue" -> "救助"
        "seeking_adoption" -> "求领养"
        else -> "交流"
    }
    val text = "【有猫腻 · $topic】${post.title}\n${post.body.take(120)}\n— 来自有猫腻流浪猫救助社区"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
}

/** 社区状态统一使用品牌图标，避免系统 emoji 在不同设备表现不一致。 */
@Composable
private fun CommunityLogoState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.catlink.app.ui.components.BrandLogoAvatar(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
        }
    }
}
