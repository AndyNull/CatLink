package com.catlink.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // ---------- 猫咪 ----------
    @Query("SELECT * FROM cats WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeCats(): Flow<List<CatEntity>>

    @Query("SELECT * FROM cats WHERE id = :id")
    fun observeCat(id: String): Flow<CatEntity?>

    @Query("SELECT * FROM cats WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<CatEntity>>

    @Query("SELECT * FROM cats WHERE deletedAt IS NOT NULL")
    suspend fun trashedCats(): List<CatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCat(cat: CatEntity)

    @Query("SELECT * FROM cats WHERE id = :id")
    suspend fun catById(id: String): CatEntity?

    @Query("UPDATE cats SET deletedAt = NULL WHERE deletedAt IS NOT NULL")
    suspend fun restoreAllFromTrash()

    @Query("DELETE FROM cats WHERE id = :id")
    suspend fun deleteCat(id: String)

    /** 级联永久删除（事务保护，中途失败不留孤儿记录） */
    @androidx.room.Transaction
    suspend fun purgeCatCascade(id: String) {
        deleteTimelineForCat(id)
        deleteMedicalsForCat(id)
        deleteTodosForCat(id)
        deleteMediaForCat(id)
        deleteContractsForCat(id)
        deleteFollowUpsForCat(id)
        deleteCaseNotesForCat(id)
        deleteCasesForCat(id)
        deleteVersionsForCat(id)
        deleteRelationsForCat(id)
        deleteCat(id)
    }

    /** 回收站过期清理：删除超过保留期的 tombstone */
    @Query("DELETE FROM cats WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeExpiredTrash(before: Long)

    // ---------- 备份快照 ----------
    @Query("SELECT * FROM cats")
    suspend fun catsSnapshot(): List<CatEntity>

    @Query("SELECT * FROM medical_records")
    suspend fun medicalsSnapshot(): List<MedicalEntity>

    @Query("SELECT * FROM adopters")
    suspend fun adoptersSnapshot(): List<AdopterEntity>

    @Query("SELECT * FROM adoption_cases")
    suspend fun casesSnapshot(): List<CaseEntity>

    @Query("SELECT * FROM contracts")
    suspend fun contractsSnapshot(): List<ContractEntity>

    @Query("SELECT * FROM follow_ups")
    suspend fun followUpsSnapshot(): List<FollowUpEntity>

    @Query("SELECT * FROM timeline_events")
    suspend fun timelineSnapshot(): List<TimelineEntity>

    @Query("SELECT * FROM todos")
    suspend fun todosSnapshot(): List<TodoEntity>

    @Query("SELECT * FROM case_notes")
    suspend fun caseNotesSnapshot(): List<CaseNoteEntity>

    @Query("SELECT * FROM media")
    suspend fun mediaSnapshot(): List<MediaEntity>

    // ---------- 医疗 ----------
    @Query("SELECT * FROM medical_records WHERE catId = :catId ORDER BY dateEpochDay DESC")
    fun observeMedicals(catId: String): Flow<List<MedicalEntity>>

    @Query("SELECT * FROM medical_records")
    fun observeAllMedicals(): Flow<List<MedicalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedical(medical: MedicalEntity)

    @Delete
    suspend fun deleteMedical(medical: MedicalEntity)

    @Query("DELETE FROM medical_records WHERE catId = :catId")
    suspend fun deleteMedicalsForCat(catId: String)

    // ---------- 领养人 ----------
    @Query("SELECT * FROM adopters WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAdopters(): Flow<List<AdopterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdopter(adopter: AdopterEntity)

    @Query("SELECT * FROM adopters WHERE id = :id")
    suspend fun adopterById(id: String): AdopterEntity?

    @Query("UPDATE adopters SET deletedAt = :now WHERE id = :id")
    suspend fun softDeleteAdopter(id: String, now: Long)

    // ---------- 沟通记录 ----------
    @Query("SELECT * FROM case_notes WHERE caseId = :caseId ORDER BY noteDateEpochDay DESC, createdAt DESC")
    fun observeCaseNotes(caseId: String): Flow<List<CaseNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaseNote(note: CaseNoteEntity)

    // ---------- 相册媒体 ----------
    @Query("SELECT * FROM media WHERE catId = :catId ORDER BY sortOrder DESC, createdAt DESC")
    fun observeMedia(catId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE catId = :catId ORDER BY sortOrder DESC, createdAt DESC")
    suspend fun mediaForCat(catId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun mediaById(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE syncState != 'uploaded' ORDER BY createdAt")
    suspend fun pendingMedia(): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: MediaEntity)

    @Query("DELETE FROM media WHERE catId = :catId")
    suspend fun deleteMediaForCat(catId: String)

    // ---------- 个案 ----------
    @Query("SELECT * FROM adoption_cases WHERE catId = :catId ORDER BY createdAt DESC")
    fun observeCases(catId: String): Flow<List<CaseEntity>>

    @Query("SELECT * FROM adoption_cases")
    fun observeAllCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM adoption_cases WHERE id = :id")
    suspend fun caseById(id: String): CaseEntity?

    @Query("SELECT * FROM adoption_cases WHERE id = :id")
    fun observeCaseById(id: String): Flow<CaseEntity?>

    @Query("SELECT * FROM adoption_cases WHERE catId = :catId")
    fun observeCasesForCatFlow(catId: String): Flow<List<CaseEntity>>

    @Query("DELETE FROM adoption_cases WHERE catId = :catId")
    suspend fun deleteCasesForCat(catId: String)

    @Query("SELECT * FROM adoption_cases WHERE catId = :catId")
    suspend fun casesForCat(catId: String): List<CaseEntity>

    @Query("DELETE FROM adoption_cases WHERE id = :id")
    suspend fun deleteCase(id: String)

    @Query("DELETE FROM contracts WHERE caseId = :caseId")
    suspend fun deleteContractsForCase(caseId: String)

    @Query("DELETE FROM follow_ups WHERE caseId = :caseId")
    suspend fun deleteFollowUpsForCase(caseId: String)

    @Query("DELETE FROM case_notes WHERE caseId = :caseId")
    suspend fun deleteCaseNotesForCase(caseId: String)

    @Query("DELETE FROM case_notes WHERE caseId IN (SELECT id FROM adoption_cases WHERE catId = :catId)")
    suspend fun deleteCaseNotesForCat(catId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCase(entity: CaseEntity)

    // ---------- 合同 ----------
    @Query("SELECT * FROM contracts WHERE caseId = :caseId ORDER BY createdAt DESC")
    fun observeContracts(caseId: String): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun contractById(id: String): ContractEntity?

    @Query("SELECT * FROM contracts WHERE caseId = :caseId")
    suspend fun contractsForCase(caseId: String): List<ContractEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContract(contract: ContractEntity)

    @Query("DELETE FROM contracts WHERE caseId IN (SELECT id FROM adoption_cases WHERE catId = :catId)")
    suspend fun deleteContractsForCat(catId: String)

    // ---------- 回访 ----------
    @Query("SELECT * FROM follow_ups WHERE caseId = :caseId ORDER BY planDateEpochDay ASC")
    fun observeFollowUps(caseId: String): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups")
    fun observeAllFollowUps(): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowUp(followUp: FollowUpEntity)

    @Query("DELETE FROM follow_ups WHERE caseId IN (SELECT id FROM adoption_cases WHERE catId = :catId)")
    suspend fun deleteFollowUpsForCat(catId: String)

    // ---------- 时间线 ----------
    @Query("SELECT * FROM timeline_events WHERE catId = :catId ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeTimeline(catId: String): Flow<List<TimelineEntity>>

    @Query("SELECT * FROM timeline_events")
    fun observeAllTimeline(): Flow<List<TimelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeline(event: TimelineEntity)

    @Query("DELETE FROM timeline_events WHERE catId = :catId")
    suspend fun deleteTimelineForCat(catId: String)

    // ---------- 待办 ----------
    @Query(
        "SELECT * FROM todos WHERE done = 0 ORDER BY (dueEpochDay IS NULL), dueEpochDay ASC, " +
            "CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END"
    )
    fun observeActiveTodos(): Flow<List<TodoEntity>>

    @Query(
        "SELECT * FROM todos ORDER BY done ASC, (dueEpochDay IS NULL), dueEpochDay ASC, " +
            "CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END"
    )
    fun observeTodos(): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET done = :done WHERE id = :id")
    suspend fun setTodoDone(id: String, done: Boolean)

    /** 完成某只猫的回访类待办（回访完成联动） */
    @Query("UPDATE todos SET done = 1 WHERE catId = :catId AND done = 0 AND title LIKE '%回访%'")
    suspend fun completeFollowUpTodos(catId: String)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: String)

    /** 清空全部数据（注销本地资料库） */
    @Query("DELETE FROM cats")
    suspend fun clearCats()

    @Query("DELETE FROM medical_records")
    suspend fun clearMedicals()

    @Query("DELETE FROM adopters")
    suspend fun clearAdopters()

    @Query("DELETE FROM adoption_cases")
    suspend fun clearCases()

    @Query("DELETE FROM contracts")
    suspend fun clearContracts()

    @Query("DELETE FROM follow_ups")
    suspend fun clearFollowUps()

    @Query("DELETE FROM timeline_events")
    suspend fun clearTimeline()

    @Query("DELETE FROM todos")
    suspend fun clearTodos()

    @Query("DELETE FROM case_notes")
    suspend fun clearCaseNotes()

    @Query("DELETE FROM cat_versions")
    suspend fun clearAllVersions()

    @Query("DELETE FROM cat_relations")
    suspend fun clearAllRelations()

    @Query("DELETE FROM media")
    suspend fun clearMedia()

    @androidx.room.Transaction
    suspend fun clearAll() {
        clearTimeline()
        clearMedicals()
        clearTodos()
        clearMedia()
        clearContracts()
        clearFollowUps()
        clearCases()
        clearCaseNotes()
        clearAdopters()
        clearCats()
        clearAllVersions()
        clearAllRelations()
    }

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodoById(id: String)

    @Query("DELETE FROM todos WHERE catId = :catId")
    suspend fun deleteTodosForCat(catId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodo(todo: TodoEntity)

    @Delete
    suspend fun deleteTodo(todo: TodoEntity)

    // ================= 快照与事务方法（v4+） =================

    @Query("SELECT * FROM adoption_cases WHERE catId = :catId AND stage NOT IN ('REJECTED','WITHDRAWN','RETURNED','CLOSED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeAdoptionForCat(catId: String): CaseEntity?

    @Query("SELECT * FROM cats WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedCatsBefore(before: Long): List<CatEntity>

    @Transaction
    suspend fun restoreCatAtomic(id: String, timeline: TimelineEntity): Boolean {
        val cat = catById(id) ?: return false
        if (cat.deletedAt == null) return false
        upsertCat(cat.copy(deletedAt = null))
        upsertTimeline(timeline)
        return true
    }

    @Transaction
    suspend fun purgeCatIfDeletedBefore(id: String, before: Long): Boolean {
        val cat = catById(id) ?: return false
        if (cat.deletedAt == null || cat.deletedAt >= before) return false
        purgeCatCascade(id)
        return true
    }

    @Transaction
    suspend fun removeAdoptionAndRestoreAtomic(
        caseId: String,
        catId: String,
        followUps: List<FollowUpEntity>,
        cat: CatEntity,
        timeline: TimelineEntity,
    ) {
        // 退养归档：个案置 WITHDRAWN，合同/回访/沟通记录全部保留（弃用但留存历史）
        updateCaseStageOnly(caseId, com.catlink.app.data.CaseStage.WITHDRAWN.name)
        deleteTodosForCat(catId)
        upsertCat(cat)
        upsertTimeline(timeline)
    }

    @Query("UPDATE adoption_cases SET stage = :stage, updatedAt = :now WHERE id = :caseId")
    suspend fun updateCaseStageOnly(caseId: String, stage: String, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun createAdoptionAtomic(
        adoption: CaseEntity,
        cat: CatEntity,
        timeline: TimelineEntity,
        followUp: FollowUpEntity,
        todo: TodoEntity,
    ): String {
        upsertCase(adoption)
        upsertCat(cat)
        upsertTimeline(timeline)
        upsertFollowUp(followUp)
        upsertTodo(todo)
        return adoption.id
    }

    @Query("UPDATE todos SET done = 1 WHERE subtitle LIKE 'followup:' || :followUpId || '|%' AND done = 0")
    suspend fun completeTodoForFollowUp(followUpId: String): Int

    @Query("SELECT id FROM todos WHERE catId = :catId AND dueEpochDay = :dueEpochDay AND title = :datedTitle AND done = 0 LIMIT 1")
    suspend fun legacyFollowUpTodoId(catId: String, dueEpochDay: Long, datedTitle: String): String?

    @Query("UPDATE todos SET done = 1 WHERE id = :id")
    suspend fun completeTodoById(id: String)

    @Query("DELETE FROM contracts WHERE id = :id")
    suspend fun deleteContractById(id: String)

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun deleteFollowUpById(id: String)

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteTimelineById(id: String)

    @Query("DELETE FROM case_notes WHERE id = :id")
    suspend fun deleteCaseNoteById(id: String)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM medical_records WHERE id = :id")
    suspend fun deleteMedicalById(id: String)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteMedia(id: String)

    // ---------- 档案版本快照 ----------
    @Query("SELECT * FROM cat_versions WHERE catId = :catId ORDER BY versionNo DESC")
    fun observeVersionsForCat(catId: String): Flow<List<CatVersionEntity>>

    @Query("SELECT * FROM cat_versions WHERE catId = :catId ORDER BY versionNo DESC")
    suspend fun versionsForCat(catId: String): List<CatVersionEntity>

    @Query("SELECT COALESCE(MAX(versionNo), 0) FROM cat_versions WHERE catId = :catId")
    suspend fun latestVersionNo(catId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatVersion(version: CatVersionEntity)

    @Query("SELECT * FROM cat_versions WHERE id = :id")
    suspend fun versionById(id: String): CatVersionEntity?

    @Query("DELETE FROM cat_versions WHERE catId = :catId")
    suspend fun deleteVersionsForCat(catId: String)

    // ---------- 猫咪亲属关系 ----------
    /** 双向查询：主猫或关联猫包含该猫的所有关系 */
    @Query("SELECT * FROM cat_relations WHERE catId = :catId OR relatedCatId = :catId ORDER BY createdAt DESC")
    fun observeRelationsForCat(catId: String): Flow<List<CatRelationEntity>>

    @Query("SELECT * FROM cat_relations WHERE catId = :catId OR relatedCatId = :catId ORDER BY createdAt DESC")
    suspend fun relationsForCat(catId: String): List<CatRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatRelation(relation: CatRelationEntity)

    @Query("DELETE FROM cat_relations WHERE id = :id")
    suspend fun deleteCatRelation(id: String)

    @Query("DELETE FROM cat_relations WHERE catId = :catId OR relatedCatId = :catId")
    suspend fun deleteRelationsForCat(catId: String)

    /** 批量取猫（关系图头像与昵称） */
    @Query("SELECT * FROM cats WHERE id IN (:ids)")
    suspend fun catsByIds(ids: List<String>): List<CatEntity>

    // ---------- 医疗 ----------
    @Query("SELECT COALESCE(SUM(CAST(REPLACE(cost, ',', '') AS REAL)), 0) FROM medical_records WHERE catId = :catId")
    suspend fun totalMedicalCost(catId: String): Double

    @Query("SELECT * FROM cat_versions")
    suspend fun versionsSnapshot(): List<CatVersionEntity>

    @Query("SELECT * FROM cat_relations")
    suspend fun relationsSnapshot(): List<CatRelationEntity>

    suspend fun snapshot(): VaultSnapshot = VaultSnapshot(
        cats = catsSnapshot(),
        medicals = medicalsSnapshot(),
        adopters = adoptersSnapshot(),
        cases = casesSnapshot(),
        contracts = contractsSnapshot(),
        followUps = followUpsSnapshot(),
        timeline = timelineSnapshot(),
        todos = todosSnapshot(),
        caseNotes = caseNotesSnapshot(),
        media = mediaSnapshot(),
        versions = versionsSnapshot(),
        relations = relationsSnapshot(),
    )
}


/** 全库一致快照（备份/导出/同步共用）。 */
data class VaultSnapshot(
    val cats: List<CatEntity>,
    val medicals: List<MedicalEntity>,
    val adopters: List<AdopterEntity>,
    val cases: List<CaseEntity>,
    val contracts: List<ContractEntity>,
    val followUps: List<FollowUpEntity>,
    val timeline: List<TimelineEntity>,
    val todos: List<TodoEntity>,
    val caseNotes: List<CaseNoteEntity>,
    val media: List<MediaEntity>,
    val versions: List<CatVersionEntity> = emptyList(),
    val relations: List<CatRelationEntity> = emptyList(),
)
