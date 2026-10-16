package com.catlink.app.data

import com.catlink.app.data.db.PublicDraftEntity
import com.catlink.app.data.db.SyncDao
import kotlinx.coroutines.flow.Flow

/**
 * 公开草稿：将猫咪档案的脱敏白名单字段保存为草稿，逐只确认后发布到服务端。
 * 只复制允许公开的字段，绝不引用私密字段（领养人/合同/费用等）。
 */
class PublicDraftRepository(
    private val syncDao: SyncDao,
    private val publicApi: PublicApi,
) {
    data class DraftInput(
        val catId: String,
        val publicNickname: String,
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
        val coverMediaId: String? = null,
        val photoUrls: String = "",
        val mediaUploadState: String? = null,
    )

    fun observeDrafts(): Flow<List<PublicDraftEntity>> = syncDao.allDrafts()

    suspend fun draftForCat(catId: String): PublicDraftEntity? = syncDao.draftByCat(catId)

    suspend fun saveDraft(input: DraftInput): Result<Unit> = runCatching {
        syncDao.upsertDraft(
            PublicDraftEntity(
                id = input.catId,
                catId = input.catId,
                publicNickname = input.publicNickname,
                alias = input.alias,
                story = input.story,
                gender = input.gender,
                ageBand = input.ageBand,
                coat = input.coat,
                personalityTags = input.personalityTags,
                healthSummary = input.healthSummary,
                weight = input.weight,
                dietRestrictions = input.dietRestrictions,
                contactWithHumans = input.contactWithHumans,
                contactWithCats = input.contactWithCats,
                catStatus = input.catStatus,
                statusReason = input.statusReason,
                archiveReason = input.archiveReason,
                rescuedDateEpochDay = input.rescuedDateEpochDay,
                statusDateEpochDay = input.statusDateEpochDay,
                statusCode = input.statusCode,
                coverMediaId = input.coverMediaId,
                photoUrls = input.photoUrls,
                mediaUploadState = input.mediaUploadState,
                cityCode = input.cityCode,
                districtCode = input.districtCode,
                publishState = "draft",
            )
        )
    }.map { }

    /** 发布草稿到服务端；成功后标记 published。 */
    suspend fun publishDraft(catId: String): Result<Unit> {
        val draft = syncDao.draftByCat(catId) ?: return Result.failure(IllegalStateException("草稿不存在"))
        if (draft.publicNickname.isBlank()) return Result.failure(IllegalStateException("公开昵称不能为空"))
        val result = publicApi.publishCat(
            PublicCatInput(
                id = draft.catId,
                nickname = draft.publicNickname,
                alias = draft.alias,
                story = draft.story,
                gender = draft.gender,
                ageBand = draft.ageBand,
                coat = draft.coat,
                personalityTags = draft.personalityTags,
                healthSummary = draft.healthSummary,
                weight = draft.weight,
                dietRestrictions = draft.dietRestrictions,
                contactWithHumans = draft.contactWithHumans,
                contactWithCats = draft.contactWithCats,
                catStatus = draft.catStatus,
                statusReason = draft.statusReason,
                archiveReason = draft.archiveReason,
                rescuedDateEpochDay = draft.rescuedDateEpochDay,
                statusDateEpochDay = draft.statusDateEpochDay,
                statusCode = draft.statusCode,
                cityCode = draft.cityCode.orEmpty(),
                districtCode = draft.districtCode.orEmpty(),
                coverUrl = draft.coverMediaId ?: "",
                photoUrls = draft.photoUrls.split(',').map(String::trim).filter(String::isNotBlank),
            )
        )
        return result.onSuccess {
            syncDao.upsertDraft(draft.copy(publishState = "published"))
        }
    }

    /** 下架已发布档案（服务端 archive + 本地标记）。 */
    suspend fun archiveDraft(catId: String): Result<Unit> {
        val draft = syncDao.draftByCat(catId) ?: return Result.failure(IllegalStateException("草稿不存在"))
        return publicApi.archiveCat(catId).onSuccess {
            syncDao.upsertDraft(draft.copy(publishState = "archived"))
        }
    }

    /** 由猫咪档案生成默认草稿（白名单字段复制）。 */
    fun draftFromCat(cat: com.catlink.app.data.db.CatEntity): DraftInput = DraftInput(
        catId = cat.id,
        publicNickname = cat.nickname,
        alias = cat.alias,
        story = cat.story,
        gender = cat.gender,
        ageBand = cat.ageEstimate,
        coat = cat.coat,
        personalityTags = listOf(cat.personality, cat.tags).filter(String::isNotBlank).joinToString(" · "),
        healthSummary = HealthStatus.fromName(cat.healthStatus).label,
        weight = cat.weight,
        dietRestrictions = cat.dietRestrictions,
        contactWithHumans = cat.contactWithHumans,
        contactWithCats = cat.contactWithCats,
        catStatus = CatStatus.fromName(cat.status).label,
        statusReason = cat.statusReason,
        archiveReason = cat.archiveReason,
        rescuedDateEpochDay = cat.rescuedDateEpochDay,
        statusDateEpochDay = cat.statusDateEpochDay,
        statusCode = CatStatus.fromName(cat.status).code,
        cityCode = cat.cityName,
        districtCode = cat.districtName,
        coverMediaId = cat.coverUri,
    )
}
