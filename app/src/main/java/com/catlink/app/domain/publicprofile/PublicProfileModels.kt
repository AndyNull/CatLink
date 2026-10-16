package com.catlink.app.domain.publicprofile

/**
 * 可公开数据的唯一猫咪模型。
 *
 * 该包不得依赖 Room 私密实体；网络层和社区层只能接收这里的模型。
 * 所有媒体都必须先完成 EXIF/GPS 清除、重编码和尺寸限制，再生成 [PublicMediaDraft]。
 */
data class PublicCatDraft(
    val nickname: String,
    val genderCode: String,
    val ageBandCode: String,
    val coatLabel: String,
    val personalityTags: List<String>,
    val story: String,
    val status: PublicCatStatus,
    val health: PublicHealthSummary,
    val region: PublicRegion,
    val media: List<PublicMediaDraft>,
)

data class PublicCatProfile(
    val id: String,
    val author: PublicRescuerSummary,
    val nickname: String,
    val genderCode: String,
    val ageBandCode: String,
    val coatLabel: String,
    val personalityTags: List<String>,
    val story: String,
    val status: PublicCatStatus,
    val health: PublicHealthSummary,
    val region: PublicRegion,
    val media: List<PublicMedia>,
    val publishedAt: Long,
    val updatedAt: Long,
)

data class PublicRescuerSummary(
    val id: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String,
    val region: PublicRegion,
)

data class PublicRegion(
    val cityCode: String,
    val cityName: String,
    val districtCode: String = "",
    val districtName: String = "",
)

data class PublicHealthSummary(
    val examined: Boolean = false,
    val vaccinated: Boolean = false,
    val dewormed: Boolean = false,
    val spayed: Boolean = false,
)

data class PublicMediaDraft(
    /** 脱敏媒体暂存区生成的随机令牌，不是原始 URI 或文件名。 */
    val sanitizedToken: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

data class PublicMedia(
    val id: String,
    val previewUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

enum class PublicCatStatus {
    RESCUE,
    TREATING,
    ADOPTABLE,
    ADOPTED,
    ARCHIVED,
}
