package com.catlink.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatStatusTest {
    @Test
    fun `主状态固定为五项稳定代码`() {
        assertEquals(
            listOf("rescue", "treating", "adoptable", "adopted", "archived"),
            CatStatus.entries.map(CatStatus::code),
        )
    }

    @Test
    fun `旧状态映射不再产生额外主状态`() {
        assertEquals(CatStatus.RESCUE, CatStatus.fromName("INTAKE"))
        assertEquals(CatStatus.TREATING, CatStatus.fromName("RECOVERING"))
        assertEquals(CatStatus.ADOPTABLE, CatStatus.fromName("MATCHING"))
        assertEquals(CatStatus.ADOPTED, CatStatus.fromName("FOLLOW_UP"))
        assertEquals(CatStatus.ARCHIVED, CatStatus.fromName("LOST_CONTACT"))
        assertEquals(ArchiveReason.LOST, ArchiveReason.fromLegacyStatus("LOST_CONTACT"))
        assertEquals(ArchiveReason.DECEASED, ArchiveReason.fromLegacyStatus("DECEASED"))
    }

    @Test
    fun `合法和非法流转边界明确`() {
        assertTrue(CatStatus.canTransition(CatStatus.RESCUE, CatStatus.TREATING))
        assertTrue(CatStatus.canTransition(CatStatus.ADOPTABLE, CatStatus.ADOPTED))
        assertFalse(CatStatus.canTransition(CatStatus.RESCUE, CatStatus.ADOPTED))
        assertFalse(CatStatus.canTransition(CatStatus.ADOPTED, CatStatus.TREATING))
        assertTrue(CatStatus.canTransition(CatStatus.ARCHIVED, CatStatus.ADOPTABLE, ArchiveReason.LOST))
        assertFalse(CatStatus.canTransition(CatStatus.ARCHIVED, CatStatus.ADOPTABLE, ArchiveReason.DECEASED))
    }

    @Test
    fun `同步和备份旧值写入前正规化并保留未知值`() {
        val lost = com.catlink.app.data.db.CatEntity(
            id = "lost", nickname = "小猫", status = "LOST_CONTACT", healthStatus = "UNKNOWN", rescuedDateEpochDay = 1,
        ).withCanonicalStatus()
        assertEquals(CatStatus.ARCHIVED.code, lost.status)
        assertEquals(ArchiveReason.LOST.code, lost.archiveReason)

        val unknown = lost.copy(status = "CUSTOM_STAGE", archiveReason = "").withCanonicalStatus()
        assertEquals(CatStatus.RESCUE.code, unknown.status)
        assertEquals("CUSTOM_STAGE", unknown.legacyStatus)
    }
}
