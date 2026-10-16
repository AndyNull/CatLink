package com.catlink.app.data

import com.catlink.app.network.AuthApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File

/** 服务端公开猫咪/社区响应 DTO（与 server 契约一致）。 */
@Serializable
data class PublicCatItem(
    val id: String,
    val nickname: String = "",
    val alias: String = "",
    val cityCode: String = "",
    val districtCode: String = "",
    val province: String = "",
    val street: String = "",
    val community: String = "",
    val distanceKm: Double? = null,
    val story: String = "",
    val gender: String = "",
    val ageBand: String = "",
    val coat: String = "",
    val personalityTags: String = "",
    val healthSummary: String = "",
    val weight: String = "",
    val dietRestrictions: String = "",
    val contactWithHumans: String = "",
    val contactWithCats: String = "",
    val catStatus: String = "",
    val statusReason: String = "",
    val archiveReason: String = "",
    val rescuedDateEpochDay: Long = 0,
    val statusDateEpochDay: Long? = null,
    val statusCode: String = "",
    val coverUrl: String = "",
    val photoUrls: List<String> = emptyList(),
    val ownerAccountId: String = "",
    val ownerName: String = "",
    val ownerUsername: String = "",
    val ownerAvatarUrl: String = "",
    val followCount: Int = 0,
    val locationName: String = "",
    val updatedAt: Long = 0,
    val latestTimeline: PublicCatLatestTimeline? = null,
)

@Serializable
data class PublicCatLatestTimeline(
    val id: String = "",
    val kind: String = "",
    val body: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class PublicCatPage(
    val items: List<PublicCatItem> = emptyList(),
    val nextCursor: String? = null,
)

data class PublicCatFilters(
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
    val community: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Int? = null,
    val status: String = "all",
    val keyword: String = "",
    val gender: String = "",
    val age: String = "",
    val sterilized: Boolean? = null,
    val updatedDays: Int? = null,
    val staleDays: Int? = null,
    val before: String = "",
    val limit: Int = 24,
)

@Serializable
data class RegionNode(
    val code: String = "",
    val name: String = "",
    val level: String = "",
    val children: List<RegionNode> = emptyList(),
)

@Serializable
data class RegionCatalog(
    val tag: String = "",
    val notModified: Boolean = false,
    val nodes: List<RegionNode> = emptyList(),
)

@Serializable
data class PublicCatInput(
    val id: String,
    val nickname: String,
    val alias: String = "",
    val story: String = "",
    val gender: String = "",
    val ageBand: String = "",
    val coat: String = "",
    val personalityTags: String = "",
    val healthSummary: String = "",
    val weight: String = "",
    val dietRestrictions: String = "",
    val contactWithHumans: String = "",
    val contactWithCats: String = "",
    val catStatus: String = "",
    val statusReason: String = "",
    val archiveReason: String = "",
    val rescuedDateEpochDay: Long = 0,
    val statusDateEpochDay: Long? = null,
    val statusCode: String = "adoptable",
    val cityCode: String = "",
    val districtCode: String = "",
    val province: String = "",
    val street: String = "",
    val community: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coverUrl: String = "",
    val photoUrls: List<String> = emptyList(),
    val visibleFields: List<String>? = null,
)

@Serializable
data class OwnedPublicCatState(
    val publicId: String = "",
    val id: String = "",
    val localId: String = "",
    val published: Boolean = false,
    val state: String = "",
    val visibleFields: List<String> = emptyList(),
)

@Serializable
data class OwnedPublicCatStates(val items: List<OwnedPublicCatState> = emptyList())

@Serializable
data class OwnedCatCover(
    val catId: String = "",
    val mediaId: String = "",
    val url: String = "",
)

@Serializable
data class OwnedCatCovers(val items: List<OwnedCatCover> = emptyList())

@Serializable
data class CommunityFeedItem(
    val id: String,
    val topic: String,
    val authorName: String = "",
    val title: String = "",
    val body: String = "",
    val authorAccountId: String = "",
    val cityCode: String = "",
    val districtCode: String = "",
    val mediaUrls: String = "",
    val catRef: String? = null,
    val liked: Boolean = false,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val createdAt: Long = 0,
)

@Serializable
data class CommunityFeedPage(
    val items: List<CommunityFeedItem> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class MediaLimits(
    val post: Int = 9,
    val catAlbum: Int = 30,
    val adoptionSite: Int = 9,
    val contract: Int = 9,
    val followUp: Int = 9,
    val chat: Int = 1,
    val maxBytes: Long = DEFAULT_MEDIA_MAX_BYTES,
    val categoryMaxBytes: Map<String, Long> = DEFAULT_MEDIA_CATEGORIES.associateWith { DEFAULT_MEDIA_MAX_BYTES },
    val allowedMimeTypes: List<String> = SUPPORTED_MEDIA_MIME_TYPES.toList(),
    val allowedExtensions: List<String> = SUPPORTED_MEDIA_EXTENSIONS.toList(),
)

private const val DEFAULT_MEDIA_MAX_BYTES = 25L * 1024 * 1024
private const val MAX_CONFIGURED_MEDIA_BYTES = 200L * 1024 * 1024
private val DEFAULT_MEDIA_CATEGORIES = setOf("cat", "contract", "followup", "avatar", "post", "chat", "misc")
private val SUPPORTED_MEDIA_MIME_TYPES = linkedSetOf("image/jpeg", "image/png", "image/webp", "application/pdf")
private val SUPPORTED_MEDIA_EXTENSIONS = linkedSetOf("jpg", "jpeg", "png", "webp", "pdf")

internal fun MediaLimits.validateUpload(category: String, filename: String, size: Long): String {
    val globalLimit = maxBytes.takeIf { it in 1..MAX_CONFIGURED_MEDIA_BYTES } ?: DEFAULT_MEDIA_MAX_BYTES
    val limit = categoryMaxBytes[category]?.takeIf { it in 1..globalLimit } ?: globalLimit
    require(size in 1..limit) { "文件不能超过 ${(limit + 1024 * 1024 - 1) / (1024 * 1024)}MB" }
    val extension = filename.substringAfterLast('.', "").lowercase()
    val extensions = allowedExtensions.map { it.trim().removePrefix(".").lowercase() }
        .filterTo(mutableSetOf()) { it in SUPPORTED_MEDIA_EXTENSIONS }
        .ifEmpty { SUPPORTED_MEDIA_EXTENSIONS }
    require(extension in extensions) { "不支持的文件类型" }
    val mimeType = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        else -> error("不支持的文件类型")
    }
    val mimeTypes = allowedMimeTypes.map { it.trim().lowercase() }
        .filterTo(mutableSetOf()) { it in SUPPORTED_MEDIA_MIME_TYPES }
        .ifEmpty { SUPPORTED_MEDIA_MIME_TYPES }
    require(mimeType in mimeTypes) { "不支持的文件类型" }
    return mimeType
}

internal fun base64DecodedSize(value: String): Long {
    val encoded = value.trim()
    val padding = encoded.takeLast(2).count { it == '=' }
    return encoded.length.toLong() * 3 / 4 - padding
}

/** 公开猫咪与社区 feed 客户端 API（Bearer 由 AuthApiClient 注入）。 */
class PublicApi(
    private val api: AuthApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) {
    private val cache = ResponseCache()
    private val mediaLimitsMutex = Mutex()
    private fun absoluteMediaUrls(value: String): String =
        value.split('|', ',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(api::resolveServerUrl)
            .filter(String::isNotBlank)
            .joinToString(",")

    private fun absoluteCat(item: PublicCatItem): PublicCatItem =
        item.copy(
            coverUrl = api.resolveServerUrl(item.coverUrl),
            photoUrls = item.photoUrls.map(api::resolveServerUrl).filter(String::isNotBlank),
            ownerAvatarUrl = api.resolveServerUrl(item.ownerAvatarUrl),
        )

    private fun absolutePost(item: CommunityFeedItem): CommunityFeedItem =
        item.copy(mediaUrls = absoluteMediaUrls(item.mediaUrls))

    @Serializable
    private data class NearbyAreaResponse(
        val city: String = "",
        val district: String = "",
        val attribution: String = "",
        val province: String = "",
        val street: String = "",
        val community: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    @Serializable
    data class LocationSuggestion(
        val id: String = "",
        val label: String = "",
        val address: String = "",
        val city: String = "",
        val district: String = "",
        val attribution: String = "",
        val source: String = "",
        val province: String = "",
        val street: String = "",
        val community: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) {
        fun stableKey(): String = id.trim().takeIf(String::isNotEmpty)?.let { "id:${it.lowercase()}" }
            ?: listOf(label, address, province, city, district, street, community, latitude, longitude)
                .joinToString("\u0000") { it.toString().trim().lowercase() }

        fun regionLabel(): String = listOf(province, city, district, street, community)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")

        fun sourceLabel(): String = source.trim().ifBlank { attribution.trim() }
    }

    data class LocationSearchPage(
        val items: List<LocationSuggestion>,
        val page: Int,
        val pageSize: Int,
        val total: Int,
        val hasMore: Boolean,
    )

    @Serializable
    private data class LocationSuggestions(
        val items: List<LocationSuggestion> = emptyList(),
        val page: Int = 0,
        val pageSize: Int = 0,
        val total: Int? = null,
        val hasMore: Boolean? = null,
    )

    suspend fun reverseLocation(latitude: Double, longitude: Double): Result<NearbyArea> =
        api.get("/location/reverse?lat=$latitude&lng=$longitude", authenticated = false) {
            json.decodeFromString<NearbyAreaResponse>(it).let { area -> area.toNearbyArea() }
        }

    suspend fun ipLocation(): Result<NearbyArea> =
        api.get("/location/ip", authenticated = false) {
            json.decodeFromString<NearbyAreaResponse>(it).let { area -> area.toNearbyArea() }
        }

    suspend fun searchLocations(keyword: String, city: String = "", district: String = ""): Result<List<LocationSuggestion>> {
        return searchLocationPage(keyword, city, district).map(LocationSearchPage::items)
    }

    suspend fun searchLocationPage(
        keyword: String,
        city: String = "",
        district: String = "",
        page: Int = 1,
        pageSize: Int = 20,
        cityCode: String = "",
        districtCode: String = "",
    ): Result<LocationSearchPage> {
        val normalizedKeyword = keyword.trim()
        val normalizedCity = city.trim()
        val normalizedDistrict = district.trim()
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(1, 20)
        val normalizedCityCode = cityCode.trim()
        val normalizedDistrictCode = districtCode.trim()
        val cacheKey = "location-search:${normalizedCity.lowercase()}:${normalizedDistrict.lowercase()}:$normalizedCityCode:$normalizedDistrictCode:${normalizedKeyword.lowercase()}:$normalizedPage:$normalizedPageSize"
        return cache.get(cacheKey, 5 * 60_000) {
            val cityQuery = normalizedCity.takeIf { it.isNotBlank() }?.let { "&city=${encode(it)}" }.orEmpty()
            val districtQuery = normalizedDistrict.takeIf { it.isNotBlank() }?.let { "&district=${encode(it)}" }.orEmpty()
            val cityCodeQuery = normalizedCityCode.takeIf { it.isNotBlank() }?.let { "&cityCode=${encode(it)}" }.orEmpty()
            val districtCodeQuery = normalizedDistrictCode.takeIf { it.isNotBlank() }?.let { "&districtCode=${encode(it)}" }.orEmpty()
            api.get("/location/search?q=${encode(normalizedKeyword)}$cityQuery$districtQuery$cityCodeQuery$districtCodeQuery&page=$normalizedPage&pageSize=$normalizedPageSize", authenticated = false) {
                json.decodeFromString<LocationSuggestions>(it).toPage(normalizedPage, normalizedPageSize)
            }
        }
    }

    private fun LocationSuggestions.toPage(requestedPage: Int, requestedPageSize: Int): LocationSearchPage {
        val responsePage = page.takeIf { it > 0 } ?: requestedPage
        val responsePageSize = pageSize.takeIf { it > 0 } ?: requestedPageSize
        val responseTotal = total ?: items.size
        return LocationSearchPage(
            items = items,
            page = responsePage,
            pageSize = responsePageSize,
            total = responseTotal,
            hasMore = hasMore ?: (responseTotal >= 0 && responsePage * responsePageSize < responseTotal),
        )
    }

    private fun NearbyAreaResponse.toNearbyArea() = NearbyArea(
        city, district, attribution, province, street, community, latitude, longitude,
    )

    suspend fun mediaLimits(forceRefresh: Boolean = false): Result<MediaLimits> =
        mediaLimitsMutex.withLock {
            cache.get("app:media-limits", 5 * 60_000, forceRefresh) {
                api.get("/app/media-limits", authenticated = false) { json.decodeFromString<MediaLimits>(it) }
            }
        }

    // ---------- 公开猫咪 ----------

    suspend fun publishCat(input: PublicCatInput): Result<Unit> =
        api.postForUnit("/public/cats", json.encodeToString(input.withServerMediaPaths()), authenticated = true).onSuccess {
            cache.invalidate("cats:")
            cache.invalidate("user:")
        }

    suspend fun updateCat(input: PublicCatInput): Result<Unit> =
        api.postForUnit("/public/cats/${input.id}", json.encodeToString(input.withServerMediaPaths()), authenticated = true).onSuccess {
            cache.invalidate("cat:${input.id}")
            cache.invalidate("cats:")
            cache.invalidate("user:")
        }

    suspend fun archiveCat(catId: String): Result<Unit> =
        api.delete("/public/cats/$catId", authenticated = true).onSuccess {
            cache.invalidate("cat:$catId")
            cache.invalidate("cats:")
            cache.invalidate("user:")
        }

    suspend fun ownedPublicCatState(catId: String): Result<OwnedPublicCatState> =
        api.get("/public/me/cats/${encode(catId)}/state", authenticated = true) {
            json.decodeFromString<OwnedPublicCatState>(it)
        }

    suspend fun ownedPublicCatStates(): Result<List<OwnedPublicCatState>> =
        api.get("/public/me/cats/states", authenticated = true) {
            json.decodeFromString<OwnedPublicCatStates>(it).items
        }

    suspend fun ownedCatCovers(): Result<List<OwnedCatCover>> =
        api.get("/media/entity/cat/covers", authenticated = true) {
            json.decodeFromString<OwnedCatCovers>(it).items
        }

    private fun PublicCatInput.withServerMediaPaths(): PublicCatInput = copy(
        coverUrl = api.resolveServerRelativePath(coverUrl).ifBlank { coverUrl },
        photoUrls = photoUrls.map { api.resolveServerRelativePath(it).ifBlank { it } },
    )

    suspend fun listPublicCats(filters: PublicCatFilters, forceRefresh: Boolean = false): Result<List<PublicCatItem>> =
        listPublicCatPage(filters, forceRefresh).map { it.items }

    suspend fun listPublicCats(city: String, status: String = "adoptable", district: String = "", forceRefresh: Boolean = false): Result<List<PublicCatItem>> =
        listPublicCats(PublicCatFilters(city = city, district = district, status = status), forceRefresh)

    suspend fun listPublicCatPage(filters: PublicCatFilters, forceRefresh: Boolean = false): Result<PublicCatPage> =
        cache.get("cats:$filters", 30_000, forceRefresh) { api.get(
            "/public/cats?province=${encode(filters.province)}&city=${encode(filters.city)}&district=${encode(filters.district)}&street=${encode(filters.street)}&community=${encode(filters.community)}&lat=${filters.latitude ?: ""}&lng=${filters.longitude ?: ""}&radiusKm=${filters.radiusKm ?: ""}&status=${encode(filters.status)}&keyword=${encode(filters.keyword)}&gender=${encode(filters.gender)}&age=${encode(filters.age)}&sterilized=${filters.sterilized?.toString().orEmpty()}&updatedDays=${filters.updatedDays ?: ""}&staleDays=${filters.staleDays ?: ""}&before=${encode(filters.before)}&limit=${filters.limit}",
            authenticated = false,
        ) {
            json.decodeFromString<PublicCatPage>(it).let { page ->
                page.copy(items = page.items.map(::absoluteCat).onEach { cat -> cache.put("cat:${cat.id}", cat, 120_000) })
            }
        } }

    @kotlinx.serialization.Serializable
    data class MediaUploadResult(val id: String = "", val url: String = "")

    /** 临时上传社区帖子图片；发布成功后由服务端事务确认，取消草稿时可按 ID 删除。 */
    suspend fun uploadTemporaryMedia(filename: String, dataBase64: String): Result<MediaUploadResult> =
        uploadTemporaryMedia("misc", filename, dataBase64)

    suspend fun uploadTemporaryPostMedia(filename: String, dataBase64: String): Result<MediaUploadResult> =
        uploadTemporaryMedia("post", filename, dataBase64)

    suspend fun uploadTemporaryPostMedia(file: File): Result<MediaUploadResult> =
        uploadMediaFile("post", file, temporary = true)

    suspend fun uploadTemporaryCatMedia(filename: String, dataBase64: String): Result<MediaUploadResult> =
        uploadTemporaryMedia("cat", filename, dataBase64)

    suspend fun uploadTemporaryCatMedia(file: File, catId: String = ""): Result<MediaUploadResult> =
        uploadMediaFile("cat", file, temporary = true, entityType = "cat", entityId = catId, usage = "cover")

    suspend fun uploadTemporaryCatAlbumMedia(file: File, catId: String): Result<MediaUploadResult> =
        uploadMediaFile("cat", file, temporary = true, entityType = "cat", entityId = catId, usage = "album")

    suspend fun uploadCatAlbumMedia(filename: String, dataBase64: String): Result<MediaUploadResult> =
        uploadMediaResult("cat", filename, dataBase64, temporary = false)

    suspend fun uploadCatAlbumMedia(file: File, catId: String): Result<MediaUploadResult> =
        uploadMediaFile("cat", file, temporary = false, entityType = "cat", entityId = catId, usage = "album")

    suspend fun uploadCatCover(file: File, catId: String): Result<MediaUploadResult> =
        uploadMediaFile("cat", file, temporary = false, entityType = "cat", entityId = catId, usage = "cover")

    suspend fun uploadCaseMedia(file: File, caseId: String): Result<MediaUploadResult> =
        uploadMediaFile("contract", file, temporary = false, entityType = "case", entityId = caseId, usage = "attachment")

    suspend fun uploadContractMedia(file: File, contractId: String): Result<MediaUploadResult> =
        uploadMediaFile("contract", file, temporary = false, entityType = "contract", entityId = contractId, usage = "attachment")

    suspend fun uploadFollowUpMedia(file: File, followUpId: String): Result<MediaUploadResult> =
        uploadMediaFile("followup", file, temporary = false, entityType = "followup", entityId = followUpId, usage = "attachment")

    suspend fun uploadChatMedia(file: File): Result<String> =
        uploadMediaFile("chat", file, temporary = false).map { it.url }

    private suspend fun uploadTemporaryMedia(category: String, filename: String, dataBase64: String): Result<MediaUploadResult> =
        uploadMediaResult(category, filename, dataBase64, temporary = true)

    private suspend fun uploadMediaResult(
        category: String,
        filename: String,
        dataBase64: String,
        temporary: Boolean,
    ): Result<MediaUploadResult> {
        mediaLimits().getOrDefault(MediaLimits()).validateUpload(category, filename, base64DecodedSize(dataBase64))
        return api.post(
            "/media/upload",
            json.encodeToString(MediaUploadInput(category, filename, dataBase64, temporary)),
            authenticated = true,
        ) { json.decodeFromString<MediaUploadResult>(it) }
    }

    private suspend fun uploadMediaFile(
        category: String,
        file: File,
        temporary: Boolean,
        entityType: String = "",
        entityId: String = "",
        usage: String = "",
    ): Result<MediaUploadResult> {
        val mediaType = mediaLimits().getOrDefault(MediaLimits()).validateUpload(category, file.name, file.length())
        return api.postFile(
            path = "/media/upload",
            fields = mapOf(
                "category" to category,
                "temporary" to temporary.toString(),
                "entityType" to entityType,
                "entityId" to entityId,
                "usage" to usage,
            ),
            file = file,
            filename = file.name,
            mediaType = mediaType,
        ) { json.decodeFromString<MediaUploadResult>(it) }
    }

    suspend fun deleteTemporaryMedia(mediaId: String): Result<Unit> =
        api.delete("/media/${encode(mediaId)}", authenticated = true)

    suspend fun deleteCatAlbumMedia(mediaId: String): Result<Unit> =
        api.delete("/media/${encode(mediaId)}", authenticated = true)

    /** 上传媒体到服务器（目录分级：cat/contract/avatar/misc），返回 URL 路径 */
    suspend fun uploadMedia(category: String, filename: String, dataBase64: String): Result<String> =
        uploadMediaResult(category, filename, dataBase64, temporary = false).map { it.url }

    suspend fun uploadMedia(category: String, file: File): Result<String> =
        uploadMediaFile(category, file, temporary = false).map { it.url }

    @kotlinx.serialization.Serializable
    data class MediaUploadInput(
        val category: String,
        val filename: String,
        val dataBase64: String,
        val temporary: Boolean = false,
        val entityType: String = "",
        val entityId: String = "",
        val usage: String = "",
    )

    suspend fun report(targetType: String, targetId: String, reason: String): Result<Unit> =
        api.postForUnit(
            "/reports",
            json.encodeToString(ReportInput(targetType, targetId, reason)),
            authenticated = true,
        )

    @kotlinx.serialization.Serializable
    data class ReportInput(val targetType: String, val targetId: String, val reason: String)

    /** 随机鼓励语句（海报/首页可用；服务端提供，失败回退本地库） */
    suspend fun encouragement(): Result<String> =
        api.get("/encouragements/random", authenticated = false) {
            json.decodeFromString<kotlinx.serialization.json.JsonObject>(it)["quote"]?.jsonPrimitive?.content.orEmpty()
        }

    suspend fun regionVersion(): Result<String> = api.get("/regions/version", authenticated = false) {
        json.decodeFromString<kotlinx.serialization.json.JsonObject>(it)["tag"]?.jsonPrimitive?.content.orEmpty()
    }

    suspend fun regions(tag: String): Result<RegionCatalog> = api.get(
        "/regions?tag=${encode(tag)}",
        authenticated = false,
    ) { json.decodeFromString(it) }

    suspend fun publicCat(catId: String, forceRefresh: Boolean = false): Result<PublicCatItem> =
        cache.get("cat:$catId", 120_000, forceRefresh) { api.get("/public/cats/$catId", authenticated = false) {
            absoluteCat(json.decodeFromString<PublicCatItem>(it))
        } }

    fun cachedPublicCat(catId: String): PublicCatItem? = cache.peek("cat:$catId")

    suspend fun feed(
        topic: String = "",
        city: String = "",
        district: String = "",
        authenticated: Boolean = false,
    ): Result<List<CommunityFeedItem>> =
        feedPage(topic, city, district, authenticated = authenticated).map { it.items }

    suspend fun feedPage(
        topic: String = "",
        city: String = "",
        district: String = "",
        keyword: String = "",
        updatedDays: Int? = null,
        before: String = "",
        limit: Int = 24,
        authenticated: Boolean = false,
        forceRefresh: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Int? = null,
    ): Result<CommunityFeedPage> =
        cache.get(feedCacheKey(topic, city, district, keyword, updatedDays, before, limit, authenticated, latitude, longitude, radiusKm), 30_000, forceRefresh) { api.get(
            "/community/feed?topic=${encode(topic)}&city=${encode(city)}&district=${encode(district)}&keyword=${encode(keyword)}&updatedDays=${updatedDays ?: ""}&before=${encode(before)}&limit=$limit&lat=${latitude ?: ""}&lng=${longitude ?: ""}&radiusKm=${radiusKm ?: ""}",
            authenticated = authenticated,
        ) { json.decodeFromString<CommunityFeedPage>(it).let { page -> page.copy(items = page.items.map(::absolutePost)) } } }

    fun cachedFeed(
        topic: String = "",
        city: String = "",
        district: String = "",
        authenticated: Boolean = false,
    ): List<CommunityFeedItem>? = cache.peek<CommunityFeedPage>(
        feedCacheKey(topic, city, district, "", null, "", 24, authenticated, null, null, null),
    )?.items

    private fun feedCacheKey(topic: String, city: String, district: String, keyword: String, updatedDays: Int?, before: String, limit: Int, authenticated: Boolean, latitude: Double?, longitude: Double?, radiusKm: Int?) =
        "feed:$topic:$city:$district:$keyword:$updatedDays:$before:$limit:$authenticated:$latitude:$longitude:$radiusKm"

    suspend fun followingFeed(topic: String = "", forceRefresh: Boolean = false): Result<List<CommunityFeedItem>> =
        cache.get("following:$topic", 30_000, forceRefresh) { api.get(
            "/community/feed/following?topic=${encode(topic)}",
            authenticated = true,
        ) { json.decodeFromString<CommunityFeedPage>(it).items.map(::absolutePost) } }

    fun cachedFollowingFeed(topic: String = ""): List<CommunityFeedItem>? = cache.peek("following:$topic")

    // ---------- 社区互动 ----------

    @Serializable
    data class PostInput(
        val id: String,
        val topic: String,
        val title: String,
        val body: String = "",
        val cityCode: String = "",
        val districtCode: String = "",
        val province: String = "",
        val street: String = "",
        val community: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val mediaUrls: String = "",
        val mediaIds: List<String> = emptyList(),
        val catRef: String? = null,
    )

    @Serializable
    data class CommentInput(val body: String, val replyToId: String? = null)

    suspend fun createPost(input: PostInput): Result<Unit> =
        api.postForUnit("/community/posts", json.encodeToString(input), authenticated = true).onSuccess {
            cache.invalidate("feed:")
            cache.invalidate("user:")
        }

    suspend fun likePost(postId: String): Result<Boolean> =
        api.post("/community/posts/$postId/like", null, authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["liked"]?.jsonPrimitive?.booleanOrNull ?: false
        }.onSuccess {
            cache.invalidate("post:$postId")
            cache.invalidate("feed:")
        }

    suspend fun bookmarkPost(postId: String): Result<Boolean> =
        api.post("/community/posts/$postId/bookmarks", null, authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["bookmarked"]?.jsonPrimitive?.booleanOrNull ?: false
        }

    suspend fun commentPost(postId: String, body: String, replyToId: String? = null): Result<Unit> =
        api.postForUnit("/community/posts/$postId/comments", json.encodeToString(CommentInput(body, replyToId)), authenticated = true).onSuccess {
            cache.invalidate("post:$postId")
            cache.invalidate("comments:$postId")
            cache.invalidate("feed:")
        }

    // ---------- 帖子详情 ----------

    @Serializable
    data class PostDetail(
    val authorName: String = "",
        val liked: Boolean = false,
        val likeCount: Long = 0,
        val commentCount: Long = 0,
        val id: String = "",
        val authorAccountId: String = "",
        val topic: String = "",
        val title: String = "",
        val body: String = "",
        val cityCode: String = "",
        val districtCode: String = "",
        val mediaUrls: String = "",
        val catRef: String? = null,
        val createdAt: Long = 0,
        val isOwner: Boolean = false,
    )

    @Serializable
    data class PostComment(
        val id: String = "",
        val postId: String = "",
        val authorAccountId: String = "",
        val authorName: String = "",
        val authorAvatar: String = "",
        val body: String = "",
        val replyToId: String? = null,
        val replyToName: String = "",
        val liked: Boolean = false,
        val likeCount: Int = 0,
        val replyCount: Int = 0,
        val createdAt: Long = 0,
    )

    @Serializable
    data class CommentLikeResult(val liked: Boolean = false, val likeCount: Int = 0)

    @Serializable
    data class CommentPage(val comments: List<PostComment> = emptyList())

    suspend fun postDetail(postId: String): Result<PostDetail> =
        cache.get("post:$postId", 30_000) { api.get("/community/posts/$postId") {
            json.decodeFromString<PostDetail>(it).let { item ->
                item.copy(mediaUrls = absoluteMediaUrls(item.mediaUrls))
            }
        } }

    suspend fun postComments(postId: String, authenticated: Boolean = false): Result<List<PostComment>> =
        cache.get("comments:$postId:$authenticated", 10_000) { api.get("/community/posts/$postId/comments", authenticated = authenticated) {
            json.decodeFromString<CommentPage>(it).comments.map { comment ->
                comment.copy(authorAvatar = api.resolveServerUrl(comment.authorAvatar))
            }
        } }

    suspend fun likeComment(commentId: String, postId: String): Result<CommentLikeResult> =
        api.post("/community/comments/$commentId/like", null, authenticated = true) {
            json.decodeFromString<CommentLikeResult>(it)
        }.onSuccess {
            cache.invalidate("comments:$postId")
        }

    // ---------- 全网猫咪关注 ----------

    suspend fun followCat(catId: String): Result<Boolean> =
        api.post("/public/cats/$catId/follow", null, authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["followed"]?.jsonPrimitive?.booleanOrNull ?: false
        }

    suspend fun unfollowCat(catId: String): Result<Unit> =
        api.delete("/public/cats/$catId/follow", authenticated = true)

    suspend fun followedCats(): Result<List<PublicCatItem>> =
        api.get("/public/me/followed-cats", authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["cats"]?.let { arr ->
                json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(PublicCatItem.serializer()), arr)
            } ?: emptyList()
        }

    suspend fun setUserFollow(accountId: String, followed: Boolean): Result<Boolean> =
        (if (followed) {
            api.putForUnit("/community/users/$accountId/follow", null, authenticated = true)
        } else {
            api.delete("/community/users/$accountId/follow", authenticated = true)
        }).map { followed }.onSuccess {
            cache.invalidate("public-users:")
            cache.invalidate("user:$accountId")
        }

    suspend fun toggleSafety(accountId: String, kind: String): Result<Boolean> =
        api.post("/community/users/$accountId/$kind", null, authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["active"]?.jsonPrimitive?.booleanOrNull ?: false
        }

    @Serializable
    data class SafetyRelation(val id: String = "", val userId: String = "", val targetId: String = "", val kind: String = "")

    suspend fun safetyRelations(): Result<List<SafetyRelation>> =
        api.get("/account/safety", authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["items"]?.let { value ->
                json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(SafetyRelation.serializer()), value)
            } ?: emptyList()
        }

    @Serializable
    data class TimelineEvent(val id: String = "", val catId: String = "", val kind: String = "", val body: String = "", val mediaUrls: String = "", val public: Boolean = true, val createdAt: Long = 0)

    suspend fun catTimeline(catId: String): Result<List<TimelineEvent>> =
        api.get("/public/cats/$catId/timeline", authenticated = false) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["items"]?.let { value ->
                json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(TimelineEvent.serializer()), value)
            } ?: emptyList()
        }

    suspend fun addCatTimeline(
        catId: String,
        clientId: String,
        kind: String,
        body: String,
        mediaUrls: String = "",
        public: Boolean = true,
    ): Result<Unit> = api.postForUnit(
        "/public/cats/$catId/timeline",
        json.encodeToString(TimelineEventInput(clientId, kind, body, mediaUrls, public)),
        authenticated = true,
    )

    @Serializable
    data class TimelineEventInput(
        val clientId: String,
        val kind: String,
        val body: String,
        val mediaUrls: String = "",
        val public: Boolean = true,
    )

    @Serializable
    data class MissingCase(val id: String = "", val catId: String = "", val ownerId: String = "", val latitudeGrid: Double = 0.0, val longitudeGrid: Double = 0.0, val radiusKm: Double = 5.0, val description: String = "", val status: String = "open")

    suspend fun missingCases(): Result<List<MissingCase>> = api.get("/missing-cases", authenticated = false) { body ->
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["items"]?.let { value ->
            json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(MissingCase.serializer()), value)
        } ?: emptyList()
    }

    suspend fun createMissingCase(catId: String, latitude: Double, longitude: Double, radiusKm: Double, description: String): Result<Unit> =
        api.postForUnit("/missing-cases", json.encodeToString(MissingCaseInput(catId, latitude, longitude, radiusKm, description)), authenticated = true)

    @Serializable
    data class MissingCaseInput(val catId: String, val latitude: Double, val longitude: Double, val radiusKm: Double, val description: String)

    suspend fun reportMissingSighting(caseId: String, latitude: Double, longitude: Double, note: String): Result<Unit> =
        api.postForUnit("/missing-cases/$caseId/sightings", json.encodeToString(MissingSightingInput(latitude, longitude, note)), authenticated = true)

    @Serializable
    data class MissingSightingInput(val latitude: Double, val longitude: Double, val note: String)

    suspend fun closeMissingCase(caseId: String): Result<Unit> = api.putForUnit("/missing-cases/$caseId/close", null, authenticated = true)

    @Serializable
    data class RescueTask(val id: String = "", val catId: String = "", val ownerId: String = "", val assigneeId: String = "", val kind: String = "", val note: String = "", val status: String = "open", val createdAt: Long = 0)

    suspend fun rescueTasks(): Result<List<RescueTask>> = api.get("/rescue-tasks", authenticated = true) { body ->
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["items"]?.let { value ->
            json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(RescueTask.serializer()), value)
        } ?: emptyList()
    }

    suspend fun createRescueTask(input: RescueTaskInput): Result<Unit> = api.postForUnit("/rescue-tasks", json.encodeToString(input), authenticated = true)

    @Serializable
    data class RescueTaskInput(val catId: String, val assigneeId: String = "", val kind: String, val note: String = "", val dueAt: Long? = null)

    suspend fun updateRescueTask(taskId: String, status: String): Result<Unit> = api.putForUnit("/rescue-tasks/$taskId", json.encodeToString(mapOf("status" to status)), authenticated = true)

    suspend fun requestIdentityBadge(kind: String, displayName: String): Result<Unit> = api.postForUnit("/account/identity", json.encodeToString(mapOf("kind" to kind, "displayName" to displayName)), authenticated = true)

    suspend fun registerPushDevice(provider: String, token: String): Result<Unit> = api.postForUnit("/account/push-devices", json.encodeToString(mapOf("provider" to provider, "token" to token)), authenticated = true)

    suspend fun reportContent(targetType: String, targetId: String, reason: String = ""): Result<Unit> =
        api.postForUnit(
            "/community/reports",
            json.encodeToString(mapOf("targetType" to targetType, "targetId" to targetId, "reason" to reason)),
            authenticated = true,
        )

    suspend fun submitModerationAppeal(actionId: String, reason: String): Result<Unit> =
        api.postForUnit(
            "/moderation-actions/${encode(actionId)}/appeals",
            json.encodeToString(mapOf("reason" to reason)),
            authenticated = true,
        )

    // ---------- 广告位（开屏/社区；后台未配置时返回空，App 无广告） ----------

    @Serializable
    data class AdItem(
        val id: String,
        val slot: String = "launch",
        val name: String = "",
        val imageUrl: String = "",
        val linkUrl: String = "",
        val onTime: Long? = null,
        val offTime: Long? = null,
        val heightMode: String = "ratio",
        val heightValue: Float = 42.857f,
    )

    /** 按广告位拉取当前启用的广告（游客可读；无配置返回空列表） */
    suspend fun adConfig(slot: String): Result<List<AdItem>> =
        api.get("/ad/config?slot=${encode(slot)}", authenticated = false) {
            if (it.isBlank()) emptyList() else json.decodeFromString<List<AdItem>>(it).map { ad ->
                ad.copy(imageUrl = api.resolveServerUrl(ad.imageUrl))
            }
        }

    // ---------- 站内通知 ----------

    @Serializable
    data class NotificationItem(
        val id: String,
        val kind: String,
        val title: String,
        val body: String = "",
        val refType: String = "",
        val refId: String = "",
        val read: Boolean = false,
    val isRead: Boolean = false,
        val createdAt: Long = 0,
        val type: String = "",
        val link: String = "",
    )

    @Serializable
    data class NotificationPage(
        val items: List<NotificationItem> = emptyList(),
        val cursor: String = "0",
    )

    @Serializable
    data class VaultSyncEvent(val cursor: Long = 0)

    @Serializable
    data class NotificationEvent(
        val id: String,
        val kind: String = "",
        val title: String = "",
        val body: String = "",
        val refType: String = "",
        val refId: String = "",
        val createdAt: Long = 0,
        val type: String = "",
        val link: String = "",
    ) {
        fun toItem() = NotificationItem(
            id = id,
            kind = kind,
            type = type,
            title = title,
            body = body,
            refType = refType,
            refId = refId,
            link = link,
            createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    suspend fun notifications(): Result<NotificationPage> =
        api.get("/account/notifications", authenticated = true) {
            json.decodeFromString<NotificationPage>(it)
        }

    suspend fun notificationStream(
        afterId: String = "",
        onEvent: (NotificationEvent) -> Unit,
        onVaultChanged: (VaultSyncEvent) -> Unit = {},
    ): Result<Unit> =
        api.streamSse("/account/notifications/stream?afterId=${encode(afterId)}") { event, data ->
            when (event) {
                "catlink_notification" -> runCatching { json.decodeFromString<NotificationEvent>(data) }.getOrNull()
                        ?.takeIf { it.id.isNotBlank() && it.id.length <= 100 }
                        ?.also { item ->
                            if (item.kind == "private") item.refId.substringAfterLast('/').takeIf(String::isNotBlank)
                                ?.let { cache.invalidate("messages:$it") }
                        }
                        ?.let(onEvent)
                "catlink_vault_sync" -> runCatching { json.decodeFromString<VaultSyncEvent>(data) }.getOrNull()
                    ?.takeIf { it.cursor > 0 }
                    ?.let(onVaultChanged)
            }
        }

    suspend fun markNotificationsRead(id: String? = null): Result<Unit> =
        api.postForUnit(
            "/account/notifications/read",
            id?.let { json.encodeToString(mapOf("id" to it)) },
            authenticated = true,
        )

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")


    // ---------- App 版本更新 ----------

    @Serializable
    data class VersionDetail(
        val versionName: String = "",
        val versionCode: Int = 0,
        val updateContent: String = "",
        val downloadUrl: String = "",
        val forced: Int = 0,
    )

    @Serializable
    data class VersionInfo(
        val hasUpdate: Boolean = false,
        val latest: VersionDetail? = null,
        val current: Int = 0,
    )

    /** 检查更新（关于页调用） */
    suspend fun checkUpdate(currentCode: Int): Result<VersionInfo> =
        api.get("/version/check?current=$currentCode", authenticated = false) {
            if (it.isBlank()) VersionInfo() else json.decodeFromString<VersionInfo>(it)
        }



    // ---------- 用户公开档案（脱敏） ----------

    @Serializable
    data class PublicUserItem(
        val accountId: String = "",
        val nickname: String = "",
        val username: String = "",
        val avatarUrl: String = "",
        val publicCats: Int = 0,
        val posts: Int = 0,
        val followed: Boolean = false,
        val title: String = "",
        val avatarFrame: String = "",
        val profileBackground: String = "",
    )

    @Serializable
    data class PublicUserPage(
        val items: List<PublicUserItem> = emptyList(),
        val total: Int = 0,
        val page: Int = 1,
        val pageSize: Int = 20,
    )

    @Serializable
    data class UserPublicProfile(
        val accountId: String = "",
        val nickname: String = "",
        val avatarUrl: String = "",
        val username: String = "",
        val publicCats: Int = 0,
        val posts: Int = 0,
        val badges: List<String> = emptyList(),
        val badgeItems: List<PublicBadge> = emptyList(),
        val title: String = "",
        val avatarFrame: String = "",
        val profileBackground: String = "",
    )

    @Serializable
    data class PublicBadge(
        val definitionId: Long = 0,
        val year: Int = 0,
        val kind: String = "",
        val rank: Int = 0,
        val score: Long = 0,
        val badgeKey: String = "",
        val tier: String = "",
        val title: String = "",
        val description: String = "",
        val iconUrl: String = "",
        val iconMediaId: Long = 0,
        val expiresAt: Long? = null,
    )

    @Serializable
    data class PublicBadgesPage(val items: List<PublicBadge> = emptyList())

    @Serializable
    data class LeaderboardEntry(
        val rank: Int = 0,
        val accountId: String = "",
        val nickname: String = "",
        val avatarUrl: String = "",
        val score: Long = 0,
    )

    @Serializable
    data class AnnualLeaderboard(
        val kind: String = "",
        val year: Int = 0,
        val items: List<LeaderboardEntry> = emptyList(),
    )

    @Serializable data class UserPublicCatsPage(val items: List<PublicCatItem> = emptyList(), val total: Int = 0)
    @Serializable data class UserPublicPostsPage(val items: List<CommunityFeedItem> = emptyList(), val total: Int = 0)

    suspend fun publicUsers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        authenticated: Boolean = false,
    ): Result<PublicUserPage> {
        val query = keyword.trim()
        return cache.get("public-users:$query:$page:$pageSize:$authenticated", 30_000) {
            api.get(
                "/users/public?keyword=${encode(query)}&page=$page&pageSize=$pageSize",
                authenticated = authenticated,
            ) {
                json.decodeFromString<PublicUserPage>(it).let { result ->
                    result.copy(items = result.items.map { user ->
                        user.copy(
                            avatarUrl = api.resolveServerUrl(user.avatarUrl),
                            avatarFrame = user.avatarFrame.takeIf { it.startsWith("annual-") }
                                ?: api.resolveServerUrl(user.avatarFrame),
                            profileBackground = api.resolveServerUrl(user.profileBackground),
                        )
                    })
                }
            }
        }
    }

    /** 用户公开主页（不含任何敏感信息） */
    suspend fun userPublic(accountId: Long): Result<UserPublicProfile> =
        cache.get("user:$accountId", 60_000) { api.get("/users/$accountId/public", authenticated = false) {
            if (it.isBlank()) UserPublicProfile() else json.decodeFromString<UserPublicProfile>(it).let { profile ->
                profile.copy(
                    avatarUrl = api.resolveServerUrl(profile.avatarUrl),
                    avatarFrame = profile.avatarFrame.takeIf { it.startsWith("annual-") }
                        ?: api.resolveServerUrl(profile.avatarFrame),
                    profileBackground = api.resolveServerUrl(profile.profileBackground),
                    badgeItems = profile.badgeItems.map { badge ->
                        badge.copy(iconUrl = api.resolveServerUrl(badge.iconUrl))
                    },
                )
            }
        } }

    suspend fun userPublicBadges(accountId: Long): Result<List<PublicBadge>> =
        cache.get("user-badges:$accountId", 60_000) {
            api.get("/users/$accountId/public/badges", authenticated = false) {
                json.decodeFromString<PublicBadgesPage>(it).items.map { badge ->
                    badge.copy(iconUrl = api.resolveServerUrl(badge.iconUrl))
                }
            }
        }

    suspend fun annualLeaderboard(kind: String, year: Int): Result<AnnualLeaderboard> =
        cache.get("leaderboard:$kind:$year", 60_000) {
            api.get("/leaderboards/${encode(kind)}?year=$year", authenticated = false) {
                json.decodeFromString<AnnualLeaderboard>(it).let { value ->
                    value.copy(items = value.items.map { row -> row.copy(avatarUrl = api.resolveServerUrl(row.avatarUrl)) })
                }
            }
        }

    suspend fun userPublicCats(accountId: Long, page: Int = 1): Result<UserPublicCatsPage> =
        cache.get("user-cats:$accountId:$page", 60_000) { api.get("/users/$accountId/public/cats?page=$page&pageSize=30", authenticated = false) {
            json.decodeFromString<UserPublicCatsPage>(it).let { value ->
                value.copy(items = value.items.map(::absoluteCat).onEach { cat ->
                    cache.put("cat:${cat.id}", cat, 120_000)
                })
            }
        } }

    suspend fun userPublicPosts(accountId: Long, page: Int = 1): Result<UserPublicPostsPage> =
        cache.get("user-posts:$accountId:$page", 60_000) { api.get("/users/$accountId/public/posts?page=$page&pageSize=30", authenticated = false) {
            json.decodeFromString<UserPublicPostsPage>(it).let { value -> value.copy(items = value.items.map(::absolutePost)) }
        } }

    suspend fun deletePost(postId: String): Result<Unit> =
        api.delete("/community/posts/$postId", authenticated = true).onSuccess {
            cache.invalidate("post:$postId")
            cache.invalidate("comments:$postId")
            cache.invalidate("feed:")
            cache.invalidate("user:")
        }



    // ---------- 用户私聊 ----------

    @Serializable
    data class ConversationItem(
        val id: String = "",
        val otherId: String = "",
        val otherName: String = "",
        val otherAvatar: String = "",
        val lastMessage: String = "",
        val lastAt: Long = 0,
        val unreadCount: Int = 0,
    )

    @Serializable
    data class ChatMessage(val id: String = "", val conversationId: String = "", val senderId: String = "", val body: String = "", val type: String = "text", val mediaUrl: String = "", val read: Boolean = false, val createdAt: Long = 0)

    @Serializable
    data class MessagePage(val items: List<ChatMessage> = emptyList())

    suspend fun startConversation(userId: Long): Result<String> =
        api.post("/conversations/start", json.encodeToString(mapOf("userId" to userId.toString())), authenticated = true) {
            json.decodeFromString<kotlinx.serialization.json.JsonObject>(it)["id"]?.toString()?.trim('"') ?: ""
        }

    suspend fun conversationMessages(conversationId: String): Result<List<ChatMessage>> =
        cache.get("messages:$conversationId", 15_000) { api.get("/conversations/$conversationId/messages", authenticated = true) {
            json.decodeFromString<MessagePage>(it).items.map { message -> message.copy(mediaUrl = api.resolveServerUrl(message.mediaUrl)) }
        } }

    @Serializable
    data class MessageInput(val body: String = "", val mediaUrl: String = "")

    suspend fun sendMessage(conversationId: String, body: String = "", mediaUrl: String = ""): Result<ChatMessage> =
        api.post("/conversations/$conversationId/messages", json.encodeToString(MessageInput(body, mediaUrl)), authenticated = true) {
            json.decodeFromString<ChatMessage>(it).let { message -> message.copy(mediaUrl = api.resolveServerUrl(message.mediaUrl)) }
        }.onSuccess { cache.invalidate("messages:$conversationId") }

    suspend fun conversationList(): Result<List<ConversationItem>> =
        api.get("/conversations", authenticated = true) {
            val page = json.decodeFromString<Map<String, List<ConversationItem>>>(it)
            page["items"]?.map { it.copy(otherAvatar = api.resolveServerUrl(it.otherAvatar)) } ?: emptyList()
        }

}

internal fun mergeLocationSuggestions(
    current: List<PublicApi.LocationSuggestion>,
    incoming: List<PublicApi.LocationSuggestion>,
): List<PublicApi.LocationSuggestion> {
    val merged = LinkedHashMap<String, PublicApi.LocationSuggestion>(current.size + incoming.size)
    current.forEach { merged[it.stableKey()] = it }
    incoming.forEach { merged.putIfAbsent(it.stableKey(), it) }
    return merged.values.toList()
}
