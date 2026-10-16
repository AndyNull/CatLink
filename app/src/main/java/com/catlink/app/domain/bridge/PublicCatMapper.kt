package com.catlink.app.domain.bridge

import com.catlink.app.data.CatStatus
import com.catlink.app.data.MedicalCategory
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.domain.publicprofile.PublicCatDraft
import com.catlink.app.domain.publicprofile.PublicCatStatus
import com.catlink.app.domain.publicprofile.PublicHealthSummary
import com.catlink.app.domain.publicprofile.PublicMediaDraft
import com.catlink.app.domain.publicprofile.PublicRegion

/**
 * 私密猫档案投影为公开草稿的唯一白名单入口。
 *
 * 这里故意不接收领养人、合同、个案、回访、待办或原始 MediaEntity。
 * 调用方只能传入已完成脱敏处理的媒体令牌。
 */
object PublicCatMapper {
    fun toDraft(
        cat: CatEntity,
        medicals: List<MedicalEntity>,
        region: PublicRegion,
        sanitizedMedia: List<PublicMediaDraft>,
        publicStory: String = cat.story,
        selectedPersonalityTags: List<String> = parseTags(cat.personality),
    ): PublicCatDraft = PublicCatDraft(
        nickname = cat.nickname.trim().take(40),
        genderCode = cat.gender.trim().take(24),
        ageBandCode = cat.ageEstimate.trim().take(32),
        coatLabel = cat.coat.trim().take(40),
        personalityTags = selectedPersonalityTags
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(8),
        story = publicStory.trim().take(2_000),
        status = cat.status.toPublicStatus(),
        health = medicals.toPublicHealthSummary(),
        region = region,
        media = sanitizedMedia.take(9),
    )

    private fun List<MedicalEntity>.toPublicHealthSummary(): PublicHealthSummary {
        val categories = asSequence().map { MedicalCategory.fromName(it.category) }.toSet()
        return PublicHealthSummary(
            examined = categories.any { it == MedicalCategory.CHECKUP || it == MedicalCategory.EXAM },
            vaccinated = MedicalCategory.VACCINE in categories,
            dewormed = MedicalCategory.DEWORM in categories,
            spayed = MedicalCategory.SPAY in categories,
        )
    }

    private fun String.toPublicStatus(): PublicCatStatus = when (CatStatus.fromName(this)) {
        CatStatus.ADOPTABLE -> PublicCatStatus.ADOPTABLE
        CatStatus.ADOPTED -> PublicCatStatus.ADOPTED
        CatStatus.ARCHIVED -> PublicCatStatus.ARCHIVED
        CatStatus.RESCUE -> PublicCatStatus.RESCUE
        CatStatus.TREATING -> PublicCatStatus.TREATING
    }

    private fun parseTags(value: String): List<String> =
        value.split(',', '，', '/', '、').map(String::trim).filter(String::isNotBlank)
}
