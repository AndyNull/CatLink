package com.catlink.app.data

import android.content.Context
import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.ContractEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.data.db.CatVersionEntity
import com.catlink.app.data.db.CatRelationEntity
import com.catlink.app.data.db.TimelineEntity
import com.catlink.app.data.db.TodoEntity
import com.catlink.app.data.db.VaultDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

class VaultRepository(
    private val dao: VaultDao,
    private val context: Context,
) {

    // ================= 查询 =================

    fun observeCats(): Flow<List<CatEntity>> = dao.observeCats()

    fun observeCat(id: String): Flow<CatEntity?> = dao.observeCat(id)

    fun observeTrash(): Flow<List<CatEntity>> = dao.observeTrash()

    fun observeMedicals(catId: String): Flow<List<MedicalEntity>> = dao.observeMedicals(catId)
    fun observeAllMedicals(): Flow<List<MedicalEntity>> = dao.observeAllMedicals()

    fun observeAdopters(): Flow<List<AdopterEntity>> = dao.observeAdopters()

    fun observeCases(catId: String): Flow<List<CaseEntity>> = dao.observeCases(catId)
    fun observeAllCases(): Flow<List<CaseEntity>> = dao.observeAllCases()

    /** 该猫当前有效领养档案：忽略存量已拒绝/已撤回申请。 */
    suspend fun activeCaseForCat(catId: String): CaseEntity? = dao.activeAdoptionForCat(catId)

    /** 删除领养档案（级联删除合同/回访/沟通记录）；无其他关联时猫咪回到待领养。 */
    suspend fun deleteCase(caseId: String) {
        val case = dao.caseById(caseId) ?: return
        val related = listOf(case)
        val caseFollowUps = dao.observeFollowUps(caseId).first()
        val privateMedia = buildList {
            related.forEach { adoption ->
                add(adoption.handoverPhotoUri)
                dao.observeContracts(adoption.id).first().forEach { contract ->
                    add(contract.signaturePhotoUri)
                    add(contract.handoverPhotoUri)
                }
            }
        }
        val now = System.currentTimeMillis()
        val cat = dao.catById(case.catId) ?: return
        dao.removeAdoptionAndRestoreAtomic(
            caseId = caseId,
            catId = case.catId,
            followUps = caseFollowUps,
            cat = cat.copy(
                status = CatStatus.ADOPTABLE.code,
                archiveReason = "",
                statusDateEpochDay = LocalDate.now().toEpochDay(),
                statusReason = "领养档案已删除",
                updatedAt = now,
            ),
            timeline = TimelineEntity(
                id = UUID.randomUUID().toString(),
                catId = case.catId,
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = "取消领养关联",
                detail = "领养档案已删除，猫咪恢复待领养",
                kind = EventKind.STATUS.name,
                createdAt = now,
            ),
        )
        MediaFiles.deletePrivate(context, privateMedia)
    }

    fun observeContracts(caseId: String): Flow<List<ContractEntity>> = dao.observeContracts(caseId)

    fun observeFollowUps(caseId: String): Flow<List<FollowUpEntity>> = dao.observeFollowUps(caseId)
    fun observeAllFollowUps(): Flow<List<FollowUpEntity>> = dao.observeAllFollowUps()

    fun observeTimeline(catId: String): Flow<List<TimelineEntity>> = dao.observeTimeline(catId)

    fun observeActiveTodos(): Flow<List<TodoEntity>> = dao.observeActiveTodos()

    fun observeTodos(): Flow<List<TodoEntity>> = dao.observeTodos()

    // ================= 猫咪 =================

    suspend fun createCat(
        nickname: String,
        alias: String,
        status: CatStatus,
        gender: String,
        ageEstimate: String,
        coat: String,
        weight: String,
        personality: String,
        story: String,
        rescuedDate: LocalDate,
        coverColorIndex: Int,
        coverUri: String = "",
        tags: String = "",
        cityName: String = "",
        districtName: String = "",
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsertCat(
            CatEntity(
                id = id,
                nickname = nickname,
                alias = alias,
                status = status.code,
                healthStatus = HealthStatus.UNKNOWN.name,
                gender = gender,
                ageEstimate = ageEstimate,
                coat = coat,
                weight = weight,
                personality = personality,
                story = story,
                rescuedDateEpochDay = rescuedDate.toEpochDay(),
                coverColorIndex = coverColorIndex,
                coverUri = coverUri,
                tags = tags,
                cityName = cityName,
                districtName = districtName,
            )
        )
        addTimeline(id, rescuedDate, "救助建档", if (story.isBlank()) "记录了一位新朋友" else story, EventKind.RESCUE)
        return id
    }

    suspend fun updateCat(
        id: String,
        nickname: String,
        alias: String,
        gender: String,
        ageEstimate: String,
        coat: String,
        weight: String,
        personality: String,
        story: String,
        rescuedDate: LocalDate,
        healthStatus: HealthStatus,
        tags: String = "",
        dietRestrictions: String = "",
        contactWithHumans: String = "",
        contactWithCats: String = "",
        cityName: String = "",
        districtName: String = "",
    ) {
        val cat = dao.catById(id) ?: return
        val updated = cat.copy(
            nickname = nickname,
            alias = alias,
            gender = gender,
            ageEstimate = ageEstimate,
            coat = coat,
            weight = weight,
            personality = personality,
            story = story,
            rescuedDateEpochDay = rescuedDate.toEpochDay(),
            healthStatus = healthStatus.name,
            tags = tags,
            dietRestrictions = dietRestrictions,
            contactWithHumans = contactWithHumans,
            contactWithCats = contactWithCats,
            cityName = cityName,
            districtName = districtName,
            updatedAt = System.currentTimeMillis(),
        )
        snapshotOldVersion(cat, updated, "资料编辑")
        dao.upsertCat(updated)
    }

    /** 猫咪内容变更时自动备份旧版本（全量快照，便于回溯历史档案） */
    private suspend fun snapshotOldVersion(old: CatEntity, new: CatEntity, reason: String) {
        val changed = old.copy(updatedAt = new.updatedAt) != new
        if (!changed) return
        val summary = buildList {
            if (old.weight != new.weight) add("称重 ${old.weight} → ${new.weight}")
            if (old.status != new.status) add("状态 ${old.status} → ${new.status}")
            if (old.healthStatus != new.healthStatus) add("健康 ${old.healthStatus} → ${new.healthStatus}")
            if (old.personality != new.personality) add("性格更新")
            if (old.tags != new.tags) add("标签更新")
            if (old.nickname != new.nickname) add("改名 ${old.nickname} → ${new.nickname}")
        }.take(3).joinToString("；")
        dao.insertCatVersion(
            CatVersionEntity(
                id = java.util.UUID.randomUUID().toString(),
                catId = old.id,
                versionNo = dao.latestVersionNo(old.id) + 1,
                snapshotJson = snapshotJson(old),
                changeSummary = summary.ifBlank { "资料更新" },
                reason = reason,
                adopterSummary = currentAdopterSummary(old.id),
            )
        )
    }

    /** 当前关联领养人摘要（用于版本快照记录变更时的领养情况） */
    private suspend fun currentAdopterSummary(catId: String): String {
        val cases = dao.casesForCat(catId)
        val active = cases.firstOrNull {
            it.adopterId.isNotBlank() &&
                it.stage !in setOf(CaseStage.REJECTED.name, CaseStage.WITHDRAWN.name)
        } ?: return ""
        val adopter = active.adopterId.takeIf { it.isNotBlank() }?.let { dao.adopterById(it) }
        val stage = CaseStage.fromName(active.stage).label
        return buildString {
            adopter?.let { append(it.name) }
            if (adopter?.phone.isNullOrBlank().not()) append(" · ${adopter?.phone}")
            if (stage.isNotBlank()) append(" · $stage")
        }
    }

    private fun snapshotJson(cat: CatEntity): String = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }.encodeToString(CatEntity.serializer(), cat)

    /** 更新封面照片 */
    suspend fun catById(id: String): com.catlink.app.data.db.CatEntity? = dao.catById(id)

    suspend fun catsSnapshot(): List<com.catlink.app.data.db.CatEntity> = dao.catsSnapshot()

    suspend fun updateCatCover(id: String, uri: String) {
        val cat = dao.catById(id) ?: return
        dao.upsertCat(cat.copy(coverUri = uri, updatedAt = System.currentTimeMillis()))
    }

    /** 相册添加照片 */
    suspend fun addCatMedia(
        catId: String,
        uri: String,
        localUri: String = "",
        remoteUrl: String = "",
        serverMediaId: String = "",
        syncState: String = "local",
        sortOrder: Long = System.currentTimeMillis(),
    ): com.catlink.app.data.db.MediaEntity {
        val media = com.catlink.app.data.db.MediaEntity(
            id = UUID.randomUUID().toString(),
            catId = catId,
            uri = uri,
            localUri = localUri,
            remoteUrl = remoteUrl,
            serverMediaId = serverMediaId,
            syncState = syncState,
            sortOrder = sortOrder,
        )
        dao.upsertMedia(media)
        return media
    }

    fun observeMedia(catId: String) = dao.observeMedia(catId)

    suspend fun catMedia(catId: String) = dao.mediaForCat(catId)

    suspend fun mediaById(id: String) = dao.mediaById(id)

    suspend fun pendingCatMedia() = dao.pendingMedia()

    suspend fun updateMedia(media: com.catlink.app.data.db.MediaEntity) = dao.upsertMedia(media)

    /** 状态流转：归档原因必填；已领养只能由领养流程触发。 */
    suspend fun changeCatStatus(
        id: String,
        status: CatStatus,
        reason: String = "",
        archiveReason: ArchiveReason? = null,
        date: LocalDate = LocalDate.now(),
    ): Result<Unit> = runCatching {
        val cat = dao.catById(id) ?: error("猫咪档案不存在")
        val from = CatStatus.fromName(cat.status)
        val currentArchiveReason = ArchiveReason.fromNameOrNull(cat.archiveReason)
        require(CatStatus.canTransition(from, status, currentArchiveReason)) { "不允许从${from.label}变更为${status.label}" }
        require(status != CatStatus.ADOPTED) { "已领养状态只能由领养流程更新" }
        require(status != CatStatus.ARCHIVED || archiveReason != null) { "请选择归档原因" }
        dao.upsertCat(
            cat.copy(
                status = status.code,
                statusDateEpochDay = date.toEpochDay(),
                statusReason = reason,
                archiveReason = if (status == CatStatus.ARCHIVED) archiveReason!!.code else "",
                updatedAt = System.currentTimeMillis(),
            )
        )
        val archiveLabel = archiveReason?.takeIf { status == CatStatus.ARCHIVED }?.let { "（${it.label}）" }.orEmpty()
        val detail = "${from.label} → ${status.label}$archiveLabel" + (if (reason.isNotBlank()) "：$reason" else "")
        addTimeline(id, date, "状态更新", detail, EventKind.STATUS)
    }

    /** 软删除 → 回收站（保留 30 天由用户手动确认） */
    suspend fun softDeleteCat(id: String) {
        val cat = dao.catById(id) ?: return
        dao.upsertCat(cat.copy(deletedAt = System.currentTimeMillis()))
    }

    /** 恢复：事务内确认 tombstone 仍存在，避免与过期清理竞态。 */
    suspend fun restoreCat(id: String): Boolean {
        val now = System.currentTimeMillis()
        return dao.restoreCatAtomic(
            id,
            TimelineEntity(
                id = UUID.randomUUID().toString(),
                catId = id,
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = "从回收站恢复",
                detail = "档案已恢复",
                kind = EventKind.STATUS.name,
                createdAt = now,
            ),
        )
    }

    /** 永久删除：先收集白名单私有媒体，事务级联删除成功后再清理文件。 */
    suspend fun purgeCat(id: String) {
        val media = collectPrivateMediaForCat(id)
        dao.purgeCatCascade(id)
        MediaFiles.deletePrivate(context, media)
    }

    /** 一键恢复全部回收站档案 */
    suspend fun restoreAllFromTrash(): Int {
        var restored = 0
        dao.trashedCats().forEach { if (restoreCat(it.id)) restored++ }
        return restored
    }

    /** 一键清空回收站（彻底删除，不可恢复） */
    suspend fun purgeAllTrash() {
        dao.trashedCats().forEach { purgeCat(it.id) }
    }

    /** 自动清理超过保留期的回收站档案，复用同一媒体与级联路径。 */
    suspend fun purgeExpiredTrash(before: Long) {
        dao.trashedCatsBefore(before).forEach { cat ->
            val media = collectPrivateMediaForCat(cat.id)
            if (dao.purgeCatIfDeletedBefore(cat.id, before)) {
                MediaFiles.deletePrivate(context, media)
            }
        }
    }

    private suspend fun collectPrivateMediaForCat(catId: String): List<String> = buildList {
        dao.catById(catId)?.coverUri?.let(::add)
        dao.observeMedia(catId).first().forEach {
            add(it.uri)
            add(it.localUri)
        }
        dao.casesForCat(catId).forEach { adoption ->
            add(adoption.handoverPhotoUri)
            dao.contractsForCase(adoption.id).forEach { contract ->
                add(contract.signaturePhotoUri)
                add(contract.handoverPhotoUri)
            }
        }
    }

    // ================= 医疗 =================

    suspend fun addMedical(
        catId: String,
        date: LocalDate,
        category: MedicalCategory,
        title: String,
        detail: String,
        hospital: String,
        cost: String,
        nextReminder: LocalDate?,
        weight: String = "",
        behaviorNote: String = "",
        result: String = "",
    ) {
        dao.upsertMedical(
            MedicalEntity(
                id = UUID.randomUUID().toString(),
                catId = catId,
                dateEpochDay = date.toEpochDay(),
                category = category.name,
                title = title,
                detail = detail,
                hospital = hospital,
                cost = cost,
                weight = weight,
                behaviorNote = behaviorNote,
                result = result,
                nextReminderEpochDay = nextReminder?.toEpochDay(),
            )
        )
        addTimeline(catId, date, "医疗记录", title, EventKind.HEALTH)
        // 完成疫苗/驱虫/绝育后自动补充档案标签（产品文档 6.3 → 6.1 标签联动）
        val cat = dao.catById(catId)
        if (cat != null && category in setOf(MedicalCategory.VACCINE, MedicalCategory.DEWORM, MedicalCategory.SPAY)) {
            val catTags = cat.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            when (category) {
                MedicalCategory.VACCINE -> catTags.add("已疫苗")
                MedicalCategory.DEWORM -> catTags.add("已驱虫")
                MedicalCategory.SPAY -> catTags.add("已绝育")
                else -> Unit
            }
            dao.upsertCat(cat.copy(tags = catTags.joinToString(","), updatedAt = System.currentTimeMillis()))
        }
        // 称重/行为变化同步到猫咪档案（基于标签联动后的最新档案，避免覆盖）
        val latestCat = dao.catById(catId) ?: cat
        if (latestCat != null && (weight.isNotBlank() || behaviorNote.isNotBlank() || result.isNotBlank())) {
            var updatedCat = latestCat.copy(updatedAt = System.currentTimeMillis())
            if (weight.isNotBlank()) updatedCat = updatedCat.copy(weight = weight)
            if (behaviorNote.isNotBlank()) updatedCat = updatedCat.copy(personality = behaviorNote)
            if (result.isNotBlank()) {
                val healthy = result.contains("恢复", ignoreCase = true) || result.contains("痊愈", ignoreCase = true)
                if (healthy) updatedCat = updatedCat.copy(healthStatus = HealthStatus.OK.name)
            }
            if (updatedCat != latestCat.copy(updatedAt = updatedCat.updatedAt)) {
                snapshotOldVersion(latestCat, updatedCat, "医疗记录更新")
                dao.upsertCat(updatedCat)
            }
        }
        if (nextReminder != null) {
            dao.upsertTodo(
                TodoEntity(
                    id = UUID.randomUUID().toString(),
                    title = "$title · 复查提醒",
                    subtitle = "来自健康记录",
                    dueEpochDay = nextReminder.toEpochDay(),
                    priority = Priority.NORMAL.name,
                    catId = catId,
                )
            )
        }
    }

    suspend fun removeMedical(medical: MedicalEntity) = dao.deleteMedical(medical)

    suspend fun removeMedia(media: com.catlink.app.data.db.MediaEntity) {
        dao.deleteMedia(media.id)
        runCatching { com.catlink.app.data.MediaFiles.deletePrivate(context, listOf(media.uri, media.localUri)) }
    }

    // ================= 档案版本快照 =================

    suspend fun catVersions(catId: String): List<CatVersionEntity> = dao.versionsForCat(catId)

    suspend fun catVersionById(id: String): CatVersionEntity? = dao.versionById(id)

    // ================= 猫咪亲属关系 =================

    suspend fun catRelations(catId: String): List<CatRelationEntity> = dao.relationsForCat(catId)

    /** 新增关系（双向可见：A 的母亲=B 时，B 的关系列表自动包含 A） */
    suspend fun addCatRelation(catId: String, relatedCatId: String, type: CatRelationType, note: String = "") {
        if (catId == relatedCatId) return
        val existing = dao.relationsForCat(catId)
        if (existing.any { it.relatedCatId == relatedCatId }) return
        // 反向交叉去重（B 已记录指向 A 的关系时不再重复）
        val reverse = dao.relationsForCat(relatedCatId)
        if (reverse.any { it.relatedCatId == catId }) return
        dao.insertCatRelation(
            CatRelationEntity(
                id = UUID.randomUUID().toString(),
                catId = catId,
                relatedCatId = relatedCatId,
                relationType = type.name,
                note = note,
            )
        )
    }

    suspend fun removeCatRelation(relationId: String) = dao.deleteCatRelation(relationId)

    /** 某猫的关系（含反向），附对方猫信息，供关系图使用 */
    suspend fun relationGraph(catId: String): List<Pair<CatRelationEntity, CatEntity?>> {
        val relations = dao.relationsForCat(catId)
        val otherIds = relations.map { if (it.catId == catId) it.relatedCatId else it.catId }
        val others = dao.catsByIds(otherIds).associateBy { it.id }
        return relations.map { it to others[if (it.catId == catId) it.relatedCatId else it.catId] }
    }

    /** 某猫累计医疗消费（元） */
    suspend fun totalMedicalCost(catId: String): Double = dao.totalMedicalCost(catId)

    // ================= 领养人 =================

    suspend fun saveAdopter(
        id: String?,
        name: String,
        phone: String,
        wechat: String,
        qq: String,
        email: String,
        idCard: String,
        address: String,
        notes: String,
        backupContact: String = "",
        preferredContact: String = "",
        residence: String = "",
        petExperience: String = "",
        familyMembers: String = "",
        contactAuthorized: Boolean = false,
    ) {
        val existing = id?.let { dao.adopterById(it) }
        dao.upsertAdopter(
            AdopterEntity(
                id = id ?: UUID.randomUUID().toString(),
                name = name,
                phone = phone,
                wechat = wechat,
                qq = qq,
                email = email,
                idCard = idCard,
                address = address,
                backupContact = backupContact,
                preferredContact = preferredContact,
                residence = residence,
                petExperience = petExperience,
                familyMembers = familyMembers,
                contactAuthorized = contactAuthorized,
                authorizedAt = if (contactAuthorized) (existing?.authorizedAt ?: System.currentTimeMillis()) else existing?.authorizedAt,
                notes = notes,
            )
        )
    }

    suspend fun deleteAdopter(id: String) = dao.softDeleteAdopter(id, System.currentTimeMillis())

    // ================= 领养档案 =================

    /** 关联领养人即领养成立；创建过程为 Room 事务，重复点击只返回已有档案。 */
    /** 退养：个案归档（保留领养人信息），猫咪回到待领养，允许再次新建领养档案 */
    suspend fun returnCat(catId: String, caseId: String, reason: String = "", date: LocalDate = LocalDate.now()) {
        val case = dao.caseById(caseId) ?: return
        if (case.catId != catId) return
        updateCaseStage(case, CaseStage.RETURNED, "退养：${reason.ifBlank { "领养人退养" }}")
    }

    suspend fun createCase(catId: String, adopterId: String?, source: String, notes: String): String {
        require(!adopterId.isNullOrBlank()) { "领养人不能为空" }
        val existing = dao.activeAdoptionForCat(catId)
        if (existing != null) return existing.id
        val cat = dao.catById(catId) ?: error("猫咪档案不存在")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val plan = LocalDate.now().plusDays(7)
        val followUpId = UUID.randomUUID().toString()
        val adoption = CaseEntity(
            id = id,
            catId = catId,
            adopterId = adopterId,
            stage = CaseStage.CLOSED.name,
            source = source,
            notes = notes,
            handoverDateEpochDay = LocalDate.now().toEpochDay(),
            createdAt = now,
            updatedAt = now,
        )
        return dao.createAdoptionAtomic(
            adoption = adoption,
            cat = cat.copy(
                status = CatStatus.ADOPTED.code,
                archiveReason = "",
                statusDateEpochDay = LocalDate.now().toEpochDay(),
                statusReason = "已建立领养档案",
                updatedAt = now,
            ),
            timeline = TimelineEntity(
                id = UUID.randomUUID().toString(),
                catId = catId,
                dateEpochDay = LocalDate.now().toEpochDay(),
                title = "领养完成",
                detail = "已关联领养人并建立领养档案",
                kind = EventKind.ADOPTION.name,
                createdAt = now,
            ),
            followUp = FollowUpEntity(
                id = followUpId,
                caseId = id,
                planDateEpochDay = plan.toEpochDay(),
                status = FollowUpStatus.PENDING.name,
                method = "电话",
                createdAt = now,
            ),
            todo = TodoEntity(
                id = UUID.randomUUID().toString(),
                title = "首次回访 · 领养后第 7 天",
                subtitle = "followup:$followUpId|case:$id|确认猫咪适应情况",
                dueEpochDay = plan.toEpochDay(),
                priority = Priority.NORMAL.name,
                catId = catId,
                createdAt = now,
            ),
        )
    }

    /**
     * 阶段推进（产品文档 7.2，6 步主流程）：
     * APPLIED→SCREENING→CONTRACT→HANDOVER→FOLLOW_UP→CLOSED
     * 进入 SCREENING 前必须关联领养人；进入 HANDOVER 前必须存在 PREPARED 合同；
     * FOLLOW_UP 触发猫咪状态与首个回访。
     */
    suspend fun advanceCase(caseId: String) {
        val case = dao.caseById(caseId) ?: return
        val next = when (CaseStage.fromName(case.stage)) {
            CaseStage.APPLIED -> CaseStage.SCREENING
            CaseStage.SCREENING -> CaseStage.CONTRACT
            CaseStage.CONTRACT -> CaseStage.HANDOVER
            CaseStage.HANDOVER -> CaseStage.FOLLOW_UP
            CaseStage.FOLLOW_UP -> CaseStage.CLOSED
            else -> return
        }
        if (next == CaseStage.SCREENING && case.adopterId.isBlank()) return
        if (next == CaseStage.HANDOVER) {
            val hasPrepared = dao.contractsForCase(case.id).any { it.status == ContractStatus.PREPARED.name }
            if (!hasPrepared) return
        }
        updateCaseStage(case, next)
    }

    /** 设置交接日期（进入 HANDOVER_SCHEDULED 后使用） */
    suspend fun updateCaseHandoverDate(caseId: String, date: LocalDate) {
        val case = dao.caseById(caseId) ?: return
        dao.upsertCase(case.copy(handoverDateEpochDay = date.toEpochDay(), updatedAt = System.currentTimeMillis()))
    }

    /** 记录沟通/评估事件（咨询、家访、审核） */
    suspend fun addCaseNote(caseId: String, type: String, content: String) {
        dao.upsertCaseNote(
            com.catlink.app.data.db.CaseNoteEntity(
                id = UUID.randomUUID().toString(),
                caseId = caseId,
                noteDateEpochDay = LocalDate.now().toEpochDay(),
                type = type,
                content = content,
            )
        )
    }

    suspend fun observeCaseNotes(caseId: String) = dao.observeCaseNotes(caseId)

    suspend fun rejectCase(caseId: String, reason: String = "审核未通过") {
        val case = dao.caseById(caseId) ?: return
        updateCaseStage(case, CaseStage.REJECTED, reason)
        dao.upsertCase(dao.caseById(caseId)!!.copy(rejectReason = reason))
    }

    suspend fun withdrawCase(caseId: String, reason: String = "候选人撤回") {
        val case = dao.caseById(caseId) ?: return
        updateCaseStage(case, CaseStage.WITHDRAWN, reason)
        dao.upsertCase(dao.caseById(caseId)!!.copy(withdrawReason = reason))
    }

    private suspend fun updateCaseStage(case: CaseEntity, next: CaseStage, note: String = "") {
        dao.upsertCase(case.copy(stage = next.name, updatedAt = System.currentTimeMillis()))
        val cat = dao.catById(case.catId) ?: return
        when (next) {
            CaseStage.FOLLOW_UP -> {
                // 交接完成：猫咪进入已领养，创建时间线与首个回访（产品文档 8.4）
                dao.upsertCat(cat.copy(
                    status = CatStatus.ADOPTED.code,
                    archiveReason = "",
                    statusDateEpochDay = LocalDate.now().toEpochDay(),
                    statusReason = "个案交接完成",
                    updatedAt = System.currentTimeMillis(),
                ))
                addTimeline(case.catId, LocalDate.now(), "领养完成", "个案交接完成，进入回访期", EventKind.ADOPTION)
                scheduleFirstFollowUp(case)
            }
            CaseStage.RETURNED -> {
                // 退养：猫咪回到待领养（保留上一任领养信息在个案中），允许再次开放领养
                dao.upsertCat(cat.copy(
                    status = CatStatus.ADOPTABLE.code,
                    archiveReason = "",
                    statusDateEpochDay = LocalDate.now().toEpochDay(),
                    statusReason = "退养后重新开放领养",
                    updatedAt = System.currentTimeMillis(),
                ))
                addTimeline(case.catId, LocalDate.now(), "退养归档", "原领养信息已保留，猫咪重新开放领养", EventKind.ADOPTION)
            }
            CaseStage.CLOSED -> {
                dao.upsertCat(cat.copy(
                    status = CatStatus.ARCHIVED.code,
                    archiveReason = ArchiveReason.OTHER.code,
                    statusDateEpochDay = LocalDate.now().toEpochDay(),
                    statusReason = "领养个案回访结束",
                    updatedAt = System.currentTimeMillis(),
                ))
                addTimeline(case.catId, LocalDate.now(), "回访关闭", "个案归档", EventKind.FOLLOW_UP)
            }
            CaseStage.REJECTED, CaseStage.WITHDRAWN -> {
                // 申请未通过：猫咪回到待领养（含合同签署后已领养的情况）
                if (CatStatus.fromName(cat.status) == CatStatus.ADOPTED) {
                    dao.upsertCat(cat.copy(
                        status = CatStatus.ADOPTABLE.code,
                        archiveReason = "",
                        statusDateEpochDay = LocalDate.now().toEpochDay(),
                        statusReason = "领养申请${next.label}",
                        updatedAt = System.currentTimeMillis(),
                    ))
                }
                addTimeline(case.catId, LocalDate.now(), "个案${next.label}", note, EventKind.STATUS)
            }
            else -> Unit
        }
    }

    private suspend fun scheduleFirstFollowUp(case: CaseEntity) {
        val plan = LocalDate.now().plusDays(7)
        val followUpId = UUID.randomUUID().toString()
        dao.upsertFollowUp(
            FollowUpEntity(
                id = followUpId,
                caseId = case.id,
                planDateEpochDay = plan.toEpochDay(),
                status = FollowUpStatus.PENDING.name,
                method = "电话",
            )
        )
        dao.upsertTodo(
            TodoEntity(
                id = UUID.randomUUID().toString(),
                title = "首次回访 · 领养后第 7 天",
                subtitle = "followup:$followUpId|case:${case.id}|确认猫咪适应情况",
                dueEpochDay = plan.toEpochDay(),
                priority = Priority.NORMAL.name,
                catId = case.catId,
            )
        )
    }

    suspend fun addFollowUp(caseId: String, planDate: LocalDate, method: String) {
        val case = dao.caseById(caseId) ?: return
        val followUpId = UUID.randomUUID().toString()
        dao.upsertFollowUp(
            FollowUpEntity(
                id = followUpId,
                caseId = caseId,
                planDateEpochDay = planDate.toEpochDay(),
                status = FollowUpStatus.PENDING.name,
                method = method,
            )
        )
        dao.upsertTodo(
            TodoEntity(
                id = UUID.randomUUID().toString(),
                title = "回访 · ${planDate.monthValue}月${planDate.dayOfMonth}日",
                subtitle = "followup:$followUpId|case:${case.id}|$method",
                dueEpochDay = planDate.toEpochDay(),
                priority = Priority.NORMAL.name,
                catId = case.catId,
            )
        )
    }

    /** 完成回访：记录猫咪状态，高风险自动提升首页待办优先级（产品文档 6.7） */
    suspend fun completeFollowUp(
        followUp: FollowUpEntity,
        method: String,
        note: String,
        risk: Int,
        statusAtVisit: String = "",
        doneDate: LocalDate = LocalDate.now(),
    ) {
        dao.upsertFollowUp(
            followUp.copy(
                status = FollowUpStatus.DONE.name,
                doneDateEpochDay = doneDate.toEpochDay(),
                method = method,
                note = note,
                riskLevel = risk,
                statusAtVisit = statusAtVisit,
            )
        )
        val case = dao.caseById(followUp.caseId) ?: return
        val completed = dao.completeTodoForFollowUp(followUp.id)
        if (completed == 0) {
            val date = LocalDate.ofEpochDay(followUp.planDateEpochDay)
            dao.legacyFollowUpTodoId(
                catId = case.catId,
                dueEpochDay = followUp.planDateEpochDay,
                datedTitle = "回访 · ${date.monthValue}月${date.dayOfMonth}日",
            )?.let { dao.completeTodoById(it) }
        }
        val detail = buildString {
            if (statusAtVisit.isNotBlank()) append("状态：$statusAtVisit；")
            if (note.isNotBlank()) append(note)
            if (method.isNotBlank() && note.isBlank()) append(method)
        }
        addTimeline(case.catId, doneDate, "回访完成", detail.ifBlank { method }, EventKind.FOLLOW_UP)
        if (risk >= 2) {
            dao.upsertTodo(
                TodoEntity(
                    id = UUID.randomUUID().toString(),
                    title = "高风险回访 · 需跟进",
                    subtitle = note.take(30),
                    priority = Priority.HIGH.name,
                    catId = case.catId,
                )
            )
        }
    }

    // ================= 合同（产品文档 7.3） =================

    /** 准备合同：绑定个案、冻结快照（初版记录填写内容摘要） */
    suspend fun prepareContract(caseId: String, notes: String = "") {
        dao.upsertContract(
            ContractEntity(
                id = UUID.randomUUID().toString(),
                caseId = caseId,
                status = ContractStatus.PREPARED.name,
                notes = notes,
            )
        )
    }

    /** 签署合同：只记录签署时间、文件哈希与签署照片；领养状态由领养人关联决定。 */
    suspend fun signContract(contractId: String, fileHash: String = "", signaturePhotoUri: String = "") {
        val contract = dao.contractById(contractId) ?: return
        dao.upsertContract(
            contract.copy(
                status = ContractStatus.SIGNED.name,
                signedDateEpochDay = LocalDate.now().toEpochDay(),
                fileHash = fileHash,
                signaturePhotoUri = signaturePhotoUri,
            )
        )
        dao.caseById(contract.caseId)?.let { case ->
            addTimeline(case.catId, LocalDate.now(), "合同签署", "领养合同已完成签署", EventKind.ADOPTION)
        }
    }

    /** 记录交接现场照片 */
    suspend fun updateCaseHandoverPhoto(caseId: String, uri: String) {
        val case = dao.caseById(caseId) ?: return
        dao.upsertCase(case.copy(handoverPhotoUri = uri, updatedAt = System.currentTimeMillis()))
    }

    /** 合同作废：仅记录档案状态和原因，不改变领养关联。 */
    suspend fun voidContract(contractId: String, reason: String = "") {
        val contract = dao.contractById(contractId) ?: return
        if (contract.status == ContractStatus.VOIDED.name) return
        dao.upsertContract(
            contract.copy(
                status = ContractStatus.VOIDED.name,
                notes = contract.notes + if (reason.isNotBlank()) "\n作废原因：$reason" else "",
            )
        )
        dao.caseById(contract.caseId)?.let { case ->
            addTimeline(case.catId, LocalDate.now(), "合同作废", reason.ifBlank { "原合同已作废" }, EventKind.ADOPTION)
        }
    }

    /** 清空本地资料库（注销/重置，需在确认后调用） */
    suspend fun clearAllData() = dao.clearAll()

    // ================= 待办 =================
    suspend fun toggleTodo(id: String, done: Boolean) = dao.setTodoDone(id, done)

    suspend fun deleteTodo(id: String) = dao.deleteTodo(id)

    suspend fun addTodo(title: String, subtitle: String, due: LocalDate?, priority: Priority) {
        dao.upsertTodo(
            TodoEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                subtitle = subtitle,
                dueEpochDay = due?.toEpochDay(),
                priority = priority.name,
            )
        )
    }

    suspend fun deleteTodo(todo: TodoEntity) = dao.deleteTodo(todo)

    // ================= 时间线 =================

    suspend fun addTimeline(catId: String, date: LocalDate, title: String, detail: String, kind: EventKind) {
        dao.upsertTimeline(
            TimelineEntity(
                id = UUID.randomUUID().toString(),
                catId = catId,
                dateEpochDay = date.toEpochDay(),
                title = title,
                detail = detail,
                kind = kind.name,
            )
        )
    }

    // ================= 统计（产品文档 9.2 口径） =================

    fun observeStats(): Flow<HomeStats> = combine(
        dao.observeCats(),
        dao.observeAllFollowUps(),
        dao.observeAllMedicals(),
        dao.observeAllTimeline(),
        dao.observeAllCases(),
    ) { cats, followUps, medicals, timelines, cases ->
        computeStats(cats, followUps, medicals, timelines, cases)
    }

    private fun computeStats(
        cats: List<CatEntity>,
        followUps: List<FollowUpEntity>,
        medicals: List<MedicalEntity>,
        timelines: List<TimelineEntity>,
        cases: List<CaseEntity>,
    ): HomeStats {
        val adopted = cats.count { CatStatus.fromName(it.status) == CatStatus.ADOPTED }
        val ready = cats.count { CatStatus.fromName(it.status) == CatStatus.ADOPTABLE }
        val treating = cats.count { CatStatus.fromName(it.status) == CatStatus.TREATING }
        val done = followUps.count { it.status == FollowUpStatus.DONE.name }
        val onTime = followUps.count { f ->
            f.status == FollowUpStatus.DONE.name &&
                f.doneDateEpochDay != null && f.doneDateEpochDay <= f.planDateEpochDay
        }
        val catIds = cats.map { it.id }.toSet()
        val vaccinated = catIds.count { cid -> medicals.any { it.catId == cid && it.category == MedicalCategory.VACCINE.name } }
        val dewormed = catIds.count { cid -> medicals.any { it.catId == cid && it.category == MedicalCategory.DEWORM.name } }
        val spayed = catIds.count { cid -> medicals.any { it.catId == cid && it.category == MedicalCategory.SPAY.name } }
        val today = LocalDate.now()
        val days = timelines.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
        var streak = 0
        var cursor = if (today in days) today else today.minusDays(1)
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }

        // 平均待领养时长：已完成个案的 交接日期 - 猫咪进入待领养/匹配的日期
        val waitDays = mutableListOf<Long>()
        cases.forEach { case ->
            val stage = CaseStage.fromName(case.stage)
            if (stage in setOf(CaseStage.FOLLOW_UP, CaseStage.CLOSED)) {
                val handover = case.handoverDateEpochDay ?: return@forEach
                val cat = cats.firstOrNull { it.id == case.catId } ?: return@forEach
                val readyDate = cat.statusDateEpochDay ?: cat.rescuedDateEpochDay
                if (handover > readyDate) waitDays.add(handover - readyDate)
            }
        }
        val avgWait = if (waitDays.isEmpty()) null else (waitDays.average().toInt())

        return HomeStats(
            totalCats = cats.size,
            rescued = cats.size,
            adopted = adopted,
            readyForAdoption = ready,
            pendingFollowUp = followUps.count { it.status == FollowUpStatus.PENDING.name },
            treating = treating,
            followUpDone = done,
            followUpTotal = followUps.size,
            followUpOnTime = onTime,
            vaccinated = vaccinated,
            dewormed = dewormed,
            spayed = spayed,
            streakDays = streak,
            avgWaitDays = avgWait,
            waitSampleCount = waitDays.size,
            lastModifiedAt = maxOf(
                cats.maxOfOrNull { it.updatedAt } ?: 0L,
                timelines.maxOfOrNull { it.createdAt } ?: 0L,
                followUps.maxOfOrNull { it.createdAt } ?: 0L,
            ),
        )
    }
}
