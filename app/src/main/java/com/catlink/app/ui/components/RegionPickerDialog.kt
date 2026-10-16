package com.catlink.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.PublicApi
import com.catlink.app.data.RegionData
import com.catlink.app.data.RegionNode
import com.catlink.app.data.mergeLocationSuggestions
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull

/** 单一地点搜索：城市由 IP 预填，也可在同一弹窗内直接切换。 */
@Composable
fun RegionPickerDialog(
    initialProvince: String = "",
    initialCity: String = "",
    initialDistrict: String = "",
    initialStreet: String = "",
    initialCommunity: String = "",
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    locating: Boolean = false,
    onLocate: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (province: String, city: String, district: String, street: String, community: String, latitude: Double?, longitude: Double?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scheme = MaterialTheme.colorScheme
    val regionTree = remember { RegionData.tree(context.applicationContext) }
    var province by remember { mutableStateOf(initialProvince) }
    var city by remember { mutableStateOf(initialCity) }
    var district by remember { mutableStateOf(initialDistrict) }
    var street by remember { mutableStateOf(initialStreet) }
    var community by remember { mutableStateOf(initialCommunity) }
    var latitude by remember { mutableStateOf(initialLatitude) }
    var longitude by remember { mutableStateOf(initialLongitude) }
    var placeQuery by remember { mutableStateOf("") }
    var placeResults by remember { mutableStateOf<List<PublicApi.LocationSuggestion>>(emptyList()) }
    var placePage by remember { mutableStateOf(1) }
    var placeHasMore by remember { mutableStateOf(false) }
    var placeSearching by remember { mutableStateOf(false) }
    var placeLoadingMore by remember { mutableStateOf(false) }
    var placeSearchMessage by remember { mutableStateOf<String?>(null) }
    var searchNonce by remember { mutableStateOf(0) }
    var selectionConfirmed by remember { mutableStateOf(initialCity.isNotBlank()) }
    var selectedLabel by remember { mutableStateOf("") }
    var detectingCity by remember { mutableStateOf(initialCity.isBlank()) }
    val placeListState = rememberLazyListState()
    val selectedProvinceNode = regionTree.firstOrNull { it.name == province }
    val selectedCityNode = selectedProvinceNode?.children?.firstOrNull { it.name == city }
    val selectedDistrictNode = selectedCityNode?.children?.firstOrNull { it.name == district }

    // 新建档案优先使用 IP 粗定位；已有档案不被当前设备位置覆盖。
    LaunchedEffect(Unit) {
        if (city.isBlank()) {
            withTimeoutOrNull(2_500) { app.container.publicApi.ipLocation().getOrNull() }?.let { area ->
                if (city.isBlank()) {
                    province = area.province
                    city = area.city
                    district = area.district
                    street = area.street
                    community = area.community
                    latitude = area.latitude
                    longitude = area.longitude
                    selectionConfirmed = area.city.isNotBlank()
                }
            }
        }
        detectingCity = false
    }

    // 精确定位由父页面完成，结果回传后同步到当前弹窗。
    LaunchedEffect(initialProvince, initialCity, initialDistrict, initialStreet, initialCommunity, initialLatitude, initialLongitude) {
        if ((initialCity.isNotBlank() && initialCity != city) || initialDistrict != district || initialLatitude != latitude || initialLongitude != longitude) {
            province = initialProvince
            city = initialCity
            district = initialDistrict
            street = initialStreet
            community = initialCommunity
            latitude = initialLatitude
            longitude = initialLongitude
            selectionConfirmed = initialCity.isNotBlank()
        }
    }

    LaunchedEffect(placeQuery, city, district, placePage, searchNonce) {
        val query = placeQuery.trim()
        if (query.length < 2) {
            placeSearching = false
            placeLoadingMore = false
            placeResults = emptyList()
            placePage = 1
            placeHasMore = false
            placeSearchMessage = null
            return@LaunchedEffect
        }
        val requestedPage = placePage
        if (requestedPage == 1) delay(350)
        placeSearching = requestedPage == 1
        placeLoadingMore = requestedPage > 1
        placeSearchMessage = null
        val result = withTimeoutOrNull(7_000) {
            app.container.publicApi.searchLocationPage(
                keyword = query,
                city = city,
                district = district,
                page = requestedPage,
                pageSize = PLACE_PAGE_SIZE,
                cityCode = selectedCityNode?.code.orEmpty(),
                districtCode = selectedDistrictNode?.code.orEmpty(),
            )
        }
        if (result == null) {
            if (requestedPage == 1) placeResults = emptyList()
            placeHasMore = false
            placeSearchMessage = "搜索超时，可重试或检查服务端地图配置"
        } else {
            val response = result.getOrNull()
            if (response != null) {
                val previousSize = if (requestedPage == 1) 0 else placeResults.size
                val merged = mergeLocationSuggestions(if (requestedPage == 1) emptyList() else placeResults, response.items)
                placeResults = merged
                placeHasMore = response.hasMore && merged.size > previousSize && (response.total <= 0 || merged.size < response.total)
                if (requestedPage == 1) placeListState.scrollToItem(0)
            } else {
                if (requestedPage == 1) placeResults = emptyList()
                placeHasMore = false
            }
            placeSearchMessage = when {
                result.isFailure -> result.exceptionOrNull()?.message ?: "地图服务暂不可用"
                placeResults.isEmpty() -> "未找到地点，换个关键词试试"
                else -> null
            }
        }
        placeSearching = false
        placeLoadingMore = false
    }

    LaunchedEffect(placeListState, placeResults.size, placeHasMore, placeSearching, placeLoadingMore) {
        snapshotFlow { placeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (shouldLoadNextLocationPage(lastVisible, placeResults.size, placeHasMore, placeSearching || placeLoadingMore)) {
                    placePage++
                }
            }
    }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.sm)) {
                RegionSelector(
                    province = province,
                    city = city,
                    district = district,
                    provinces = regionTree,
                    cities = selectedProvinceNode?.children.orEmpty(),
                    districts = selectedCityNode?.children.orEmpty(),
                    detecting = detectingCity,
                    onProvinceSelected = { item ->
                        province = item.name
                        city = ""
                        district = ""
                        street = ""
                        community = ""
                        latitude = null
                        longitude = null
                        selectionConfirmed = false
                        selectedLabel = ""
                        placeQuery = ""
                        placeResults = emptyList()
                        placePage = 1
                        placeHasMore = false
                    },
                    onCitySelected = { item ->
                        city = item.name
                        district = ""
                        street = ""
                        community = ""
                        latitude = null
                        longitude = null
                        selectionConfirmed = city.isNotBlank()
                        selectedLabel = ""
                        placeQuery = ""
                        placeResults = emptyList()
                        placePage = 1
                        placeHasMore = false
                    },
                    onDistrictSelected = { item ->
                        district = item.name
                        street = ""
                        community = ""
                        latitude = null
                        longitude = null
                        selectionConfirmed = city.isNotBlank()
                        selectedLabel = ""
                        placeQuery = ""
                        placeResults = emptyList()
                        placePage = 1
                        placeHasMore = false
                    },
                )
                CatTextField(
                    value = placeQuery,
                    onValueChange = {
                        placeQuery = it.take(80)
                        placePage = 1
                        placeHasMore = false
                        placeResults = emptyList()
                        selectionConfirmed = false
                        selectedLabel = ""
                    },
                    label = "地点关键词",
                    placeholder = "输入小区、街道或地标",
                )
                if (placeSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在搜索…", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                } else if (placeSearchMessage != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(placeSearchMessage.orEmpty(), style = MaterialTheme.typography.labelSmall, color = scheme.error, modifier = Modifier.weight(1f))
                        if (placeQuery.trim().length >= 2) {
                            androidx.compose.material3.TextButton(onClick = { searchNonce++ }) { Text("重试") }
                        }
                    }
                }
                if (placeResults.isNotEmpty()) {
                    LazyColumn(
                        state = placeListState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    ) {
                        itemsIndexed(placeResults, key = { _, item -> item.stableKey() }) { index, item ->
                            LocationResultRow(item) {
                                province = item.province.ifBlank { province }
                                city = item.city.ifBlank { city }
                                district = item.district
                                street = item.street.ifBlank { item.address }
                                community = item.community
                                latitude = item.latitude
                                longitude = item.longitude
                                selectedLabel = item.label
                                selectionConfirmed = city.isNotBlank() && district.isNotBlank()
                                placeResults = emptyList()
                                placePage = 1
                                placeHasMore = false
                                placeQuery = ""
                            }
                            if (index < placeResults.lastIndex) HorizontalDivider(color = scheme.outlineVariant)
                        }
                        if (placeLoadingMore) {
                            item(key = "location-loading") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = CatSpacing.sm),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
                if (selectedLabel.isNotBlank()) {
                    Text("已识别：${locationSummary(province, city, district, street, community)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            CatDialogActions(
                confirmText = "确认",
                onConfirm = { onConfirm(province, city, district, street.trim(), community.trim(), latitude, longitude) },
                onDismiss = onDismiss,
                confirmEnabled = selectionConfirmed,
            )
        },
    )
}

@Composable
private fun RegionSelector(
    province: String,
    city: String,
    district: String,
    provinces: List<RegionNode>,
    cities: List<RegionNode>,
    districts: List<RegionNode>,
    detecting: Boolean,
    onProvinceSelected: (RegionNode) -> Unit,
    onCitySelected: (RegionNode) -> Unit,
    onDistrictSelected: (RegionNode) -> Unit,
) {
    var expandedLevel by remember { mutableStateOf<RegionLevel?>(null) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expandedLevel == null) 0f else 180f,
        animationSpec = tween(160),
        label = "region-arrow",
    )
    val activeLabel = when (expandedLevel) {
        RegionLevel.PROVINCE -> "省份"
        RegionLevel.CITY -> "城市"
        RegionLevel.DISTRICT -> "区县"
        null -> ""
    }
    fun open(level: RegionLevel) {
        expandedLevel = if (expandedLevel == level) null else level
        query = ""
        submittedQuery = ""
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorSize = it.size },
    ) {
        val summary = listOf(province, city, district)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" / ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(CatRadii.field))
                .border(CatSpacing.hairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CatRadii.field))
                .clickable { open(RegionLevel.PROVINCE) }
                .padding(horizontal = CatSpacing.sm, vertical = CatSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    summary.isNotBlank() -> summary
                    detecting -> "正在识别城市…"
                    else -> "选择省 / 市 / 区县"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "选择地点",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }
        if (expandedLevel != null && anchorSize.width > 0) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorSize.height + with(density) { CatSpacing.xs.roundToPx() }),
                onDismissRequest = { expandedLevel = null; query = ""; submittedQuery = "" },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                ),
            ) {
            Column(
                modifier = Modifier
                    .width(with(density) { anchorSize.width.toDp() })
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(CatRadii.control))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(CatSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
                ) {
                    CompactRegionSearchField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        placeholder = "搜索$activeLabel",
                        onSearch = { submittedQuery = query.trim() },
                        modifier = Modifier.weight(1f),
                    )
                    CatButton(
                        text = "搜索",
                        onClick = { submittedQuery = query.trim() },
                        height = 36.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    RegionColumn(
                        title = "省",
                        items = provinces,
                        selected = province,
                        enabled = true,
                        active = expandedLevel == RegionLevel.PROVINCE,
                        query = if (expandedLevel == RegionLevel.PROVINCE) submittedQuery else "",
                        onSelect = {
                            onProvinceSelected(it)
                            expandedLevel = RegionLevel.CITY
                            query = ""
                            submittedQuery = ""
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RegionColumnDivider()
                    RegionColumn(
                        title = "市",
                        items = cities,
                        selected = city,
                        enabled = province.isNotBlank(),
                        active = expandedLevel == RegionLevel.CITY,
                        query = if (expandedLevel == RegionLevel.CITY) submittedQuery else "",
                        onSelect = {
                            onCitySelected(it)
                            expandedLevel = RegionLevel.DISTRICT
                            query = ""
                            submittedQuery = ""
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RegionColumnDivider()
                    RegionColumn(
                        title = "区",
                        items = districts,
                        selected = district,
                        enabled = city.isNotBlank(),
                        active = expandedLevel == RegionLevel.DISTRICT,
                        query = if (expandedLevel == RegionLevel.DISTRICT) submittedQuery else "",
                        onSelect = {
                            onDistrictSelected(it)
                            expandedLevel = null
                            query = ""
                            submittedQuery = ""
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            }
        }
    }
}

private enum class RegionLevel { PROVINCE, CITY, DISTRICT }

@Composable
private fun CompactRegionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(CatRadii.control))
            .background(scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, scheme.outlineVariant, RoundedCornerShape(CatRadii.control))
            .padding(horizontal = CatSpacing.sm),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        cursorBrush = SolidColor(scheme.primary),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RegionColumnDivider() {
    Box(
        Modifier
            .padding(horizontal = CatSpacing.xxs)
            .width(CatSpacing.hairline)
            .height(160.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun RegionColumn(
    title: String,
    items: List<RegionNode>,
    selected: String,
    enabled: Boolean,
    active: Boolean,
    query: String,
    onSelect: (RegionNode) -> Unit,
    modifier: Modifier,
) {
    val filtered = items.filter { query.isBlank() || it.name.contains(query, true) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState())) {
            if (!enabled) {
                RegionColumnPlaceholder("先选${if (title == "市") "省" else "市"}")
            } else if (filtered.isEmpty()) {
                RegionColumnPlaceholder("无匹配")
            } else {
                filtered.forEachIndexed { index, item ->
                    val isSelected = item.name == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = CatSpacing.sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.name,
                            style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (index < filtered.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionColumnPlaceholder(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CatSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun locationSummary(province: String, city: String, district: String, street: String, community: String): String =
    listOf(province, city, district, street, community).map(String::trim).filter(String::isNotBlank).distinct().joinToString(" · ")

@Composable
private fun LocationResultRow(item: PublicApi.LocationSuggestion, onClick: () -> Unit) {
    val title = item.label.trim().ifBlank { item.address.trim() }
    val address = item.address.trim().ifBlank { item.regionLabel() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = CatSpacing.xs, vertical = CatSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        item.sourceLabel().takeIf(String::isNotBlank)?.let {
            Text("来源 · $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val PLACE_PAGE_SIZE = 20

internal fun shouldLoadNextLocationPage(lastVisible: Int, itemCount: Int, hasMore: Boolean, loading: Boolean): Boolean =
    hasMore && !loading && itemCount > 0 && lastVisible >= (itemCount - 3).coerceAtLeast(0)
