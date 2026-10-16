package com.catlink.app.data

import com.catlink.app.data.db.CatEntity

internal fun CatEntity.withCanonicalStatus(): CatEntity {
    val original = status.trim()
    val main = CatStatus.fromNameOrNull(original)
    val canonical = main ?: CatStatus.RESCUE
    val reason = when (canonical) {
        CatStatus.ARCHIVED -> ArchiveReason.fromNameOrNull(archiveReason)
            ?: ArchiveReason.fromLegacyStatus(original)
            ?: ArchiveReason.OTHER
        else -> null
    }
    return copy(
        status = canonical.code,
        archiveReason = reason?.code.orEmpty(),
        legacyStatus = legacyStatus.ifBlank { if (main == null) original else "" },
    )
}
