package com.catlink.app.data

import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.ContractEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.data.db.MediaEntity
import com.catlink.app.data.db.SyncConflictEntity
import com.catlink.app.data.db.SyncFingerprintEntity
import com.catlink.app.data.db.SyncOutboxEntity
import com.catlink.app.data.db.SyncRevisionEntity
import com.catlink.app.data.db.TimelineEntity
import com.catlink.app.data.db.TodoEntity
import com.catlink.app.data.db.VaultDao
import com.catlink.app.data.db.SyncDao
import com.catlink.app.data.db.CaseNoteEntity
import com.catlink.app.network.AuthApiClient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class VaultSyncRequest(
    val cursor: Long = 0,
    val records: List<VaultSyncRecord> = emptyList(),
)

@Serializable
data class VaultSyncRecord(
    val recordId: String,
    val recordType: String,
    val keyVersion: Int,
    val revision: Long,
    val serverRevision: Long = 0,
    val ciphertext: String,
    val deleted: Boolean = false,
)

@Serializable
data class VaultSyncAck(
    val recordId: String,
    val recordType: String,
    val revision: Long,
    val status: String,
)

@Serializable
data class VaultSyncResponse(
    val cursor: Long,
    val records: List<VaultSyncRecord> = emptyList(),
    val acks: List<VaultSyncAck> = emptyList(),
)

/**
 * E2EE 云同步引擎：
 * - push：outbox 中的密文变更按 revision 单调提交到服务端 opaque vault；
 * - pull：应用远端新记录；本地有未推送变更且 revision 落后时记冲突；
 * - 实体类型分发到 [VaultDao] 对应 upsert/delete。
 */
class SyncEngine(
    private val dao: VaultDao,
    private val syncDao: SyncDao,
    private val vault: VaultManager,
    private val api: AuthApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) {
    data class SyncReport(val pushed: Int, val pulled: Int, val conflicts: Int, val cursor: Long)

    /** 启用云端 vault（幂等）。 */
    suspend fun enableVault(): Result<Unit> {
        val key = vault.ensureVault()
        return api.postForUnit("/vault/enable", json.encodeToString(mapOf("keyVersion" to key.kekVersion)), authenticated = true)
    }

    /** 捕获所有业务表相对上次已知状态的差异；首次调用即建立完整 outbox。 */
    suspend fun captureLocalChanges(): Result<Int> = runCatching {
        val media = dao.mediaSnapshot()
        media.filter { it.syncState != AlbumSyncState.UPLOADED.value || it.remoteUrl.isBlank() }
            .forEach { item ->
                syncDao.outboxByEntity("media", item.id)?.let { syncDao.deleteOutbox(it.id) }
            }
        val snapshots = buildList {
            dao.catsSnapshot().forEach { add(Triple("cat", it.id, json.encodeToString(it))) }
            dao.medicalsSnapshot().forEach { add(Triple("medical", it.id, json.encodeToString(it))) }
            dao.adoptersSnapshot().forEach { add(Triple("adopter", it.id, json.encodeToString(it))) }
            dao.casesSnapshot().forEach { add(Triple("case", it.id, json.encodeToString(it))) }
            dao.contractsSnapshot().forEach { add(Triple("contract", it.id, json.encodeToString(it))) }
            dao.followUpsSnapshot().forEach { add(Triple("followup", it.id, json.encodeToString(it))) }
            dao.timelineSnapshot().forEach { add(Triple("timeline", it.id, json.encodeToString(it))) }
            dao.todosSnapshot().forEach { add(Triple("todo", it.id, json.encodeToString(it))) }
            dao.caseNotesSnapshot().forEach { add(Triple("casenote", it.id, json.encodeToString(it))) }
            media
                .filter { it.syncState == AlbumSyncState.UPLOADED.value && it.remoteUrl.isNotBlank() }
                .forEach {
                    val portable = it.copy(uri = it.remoteUrl, localUri = "")
                    add(Triple("media", it.id, json.encodeToString(portable)))
                }
        }
        val currentKeys = snapshots.mapTo(mutableSetOf()) { "${it.first}:${it.second}" }
        var count = 0
        for ((type, id, plaintext) in snapshots) {
            val hash = VaultCrypto.sha256(plaintext.toByteArray(Charsets.UTF_8))
            val known = syncDao.fingerprint(type, id)
            val pending = syncDao.outboxByEntity(type, id)
            if (known?.payloadHash != hash && pending?.payloadHash != hash) {
                count += enqueue(type, id, plaintext)
            }
        }
        for (known in syncDao.fingerprints()) {
            val key = "${known.entityType}:${known.entityId}"
            if (key !in currentKeys && syncDao.outboxByEntity(known.entityType, known.entityId)?.op != "delete") {
                count += enqueue(known.entityType, known.entityId, "", deleted = true)
            }
        }
        count
    }

    /** 首次/手动全量快照：扫描全部业务表并为未同步差异建立 outbox。 */
    suspend fun enqueueInitialSnapshot(): Result<Int> = captureLocalChanges()

    /** 增量入队（业务写路径调用）。 */
    suspend fun enqueue(entityType: String, entityId: String, plaintext: String, deleted: Boolean = false): Int {
        val revision = nextRevision()
        val hash = VaultCrypto.sha256(plaintext.toByteArray(Charsets.UTF_8))
        val cipher = if (deleted) "" else vault.encryptEntity(entityType, entityId, plaintext.toByteArray(Charsets.UTF_8))
        // 同实体已有待推送变更：直接替换（保留最新）
        syncDao.outboxByEntity(entityType, entityId)?.let { existing ->
            syncDao.deleteOutbox(existing.id)
        }
        syncDao.insertOutbox(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                op = if (deleted) "delete" else "upsert",
                payloadJson = cipher,
                payloadHash = hash,
                revision = revision,
            )
        )
        return 1
    }

    /** 执行一次同步（push + pull）：总是发送 sync 请求（outbox 为空时仅拉取）。 */
    suspend fun syncOnce(): Result<SyncReport> {
        captureLocalChanges().getOrElse { return Result.failure(it) }
        val cursor = syncDao.revision(KEY_CURSOR)?.lastLocalRevision ?: 0L
        val pending = syncDao.pendingOutbox(MAX_BATCH).take(MAX_BATCH)
        val outbox = selectUploadBatch(pending).getOrElse { return Result.failure(it) }
        val pushedByEntity = outbox.associateBy { "${it.entityType}:${it.entityId}" }
        val keyVersion = vault.ensureVault().kekVersion
        val request = VaultSyncRequest(
            cursor = cursor,
            records = outbox.map {
                VaultSyncRecord(
                    recordId = it.entityId,
                    recordType = it.entityType,
                    keyVersion = keyVersion,
                    revision = it.revision,
                    ciphertext = it.payloadJson,
                    deleted = it.op == "delete",
                )
            },
        )
        val response = api.post("/vault/sync", json.encodeToString(request), authenticated = true) {
            json.decodeFromString<VaultSyncResponse>(it)
        }.getOrElse { return Result.failure(it) }

        // 只有服务端明确 accepted/already-applied 的记录才能确认出队。
        val acknowledged = response.acks
            .filter { it.status == "accepted" || it.status == "already-applied" }
            .associateBy { "${it.recordType}:${it.recordId}" }
        val conflictKeys = response.acks
            .filter { it.status == "conflict" }
            .mapTo(mutableSetOf()) { "${it.recordType}:${it.recordId}" }
        var pulled = 0
        var applyFailed = false
        for (record in response.records) {
            if (applyFailed) break
            val key = "${record.recordType}:${record.recordId}"
            val localPending = pushedByEntity[key]
            val echoedExactly = localPending != null &&
                localPending.revision == record.revision &&
                localPending.payloadJson == record.ciphertext &&
                (localPending.op == "delete") == record.deleted
            when {
                localPending != null && key in conflictKeys -> {
                    syncDao.insertConflict(
                        SyncConflictEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = record.recordType,
                            entityId = record.recordId,
                            localPayloadJson = localPending.payloadJson,
                            remotePayloadJson = record.ciphertext,
                            localHash = localPending.payloadHash,
                            remoteHash = "",
                            status = "pending",
                        )
                    )
                }
                echoedExactly -> {
                    // 服务端回显与本次 push 完全一致：视为确认，不重复解密应用。
                    syncDao.upsertRevision(SyncRevisionEntity(entityId = key, lastLocalRevision = record.revision))
                }
                localPending != null && localPending.revision >= record.revision -> {
                    syncDao.insertConflict(
                        SyncConflictEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = record.recordType,
                            entityId = record.recordId,
                            localPayloadJson = localPending.payloadJson,
                            remotePayloadJson = record.ciphertext,
                            localHash = localPending.payloadHash,
                            remoteHash = "",
                            status = "pending",
                        )
                    )
                    conflictKeys.add(key)
                }
                else -> {
                    val appliedHash = applyRecord(record)
                    if (appliedHash != null) {
                        pulled++
                        if (record.deleted) {
                            syncDao.deleteFingerprint(record.recordType, record.recordId)
                        } else {
                            syncDao.upsertFingerprint(SyncFingerprintEntity(record.recordType, record.recordId, appliedHash, record.revision))
                        }
                        syncDao.upsertRevision(
                            SyncRevisionEntity(
                                entityId = key,
                                lastLocalRevision = record.revision,
                            )
                        )
                    } else {
                        applyFailed = true
                    }
                }
            }
            ensureRevisionCounterAtLeast(record.revision)
        }
        // 仅删除有明确 ack，或完全一致回显且没有冲突的 outbox。
        outbox.filter {
            val key = "${it.entityType}:${it.entityId}"
            val ack = acknowledged[key]
            key !in conflictKeys && ((ack != null && ack.revision == it.revision) || response.records.any { record ->
                record.recordType == it.entityType && record.recordId == it.entityId &&
                    record.revision == it.revision && record.ciphertext == it.payloadJson &&
                    record.deleted == (it.op == "delete")
            })
        }.forEach {
            val key = "${it.entityType}:${it.entityId}"
            if (it.op == "delete") {
                syncDao.deleteFingerprint(it.entityType, it.entityId)
            } else {
                syncDao.upsertFingerprint(SyncFingerprintEntity(it.entityType, it.entityId, it.payloadHash, it.revision))
            }
            syncDao.deleteOutbox(it.id)
        }
        if (applyFailed) {
            return Result.failure(IllegalStateException("远端同步记录无法安全应用"))
        }
        val nextCursor = response.cursor
        saveCursor(nextCursor)
        val pushed = outbox.count {
            val ack = acknowledged["${it.entityType}:${it.entityId}"]
            ack != null && ack.revision == it.revision
        }
        return Result.success(
            SyncReport(pushed = pushed, pulled = pulled, conflicts = conflictKeys.size, cursor = nextCursor)
        )
    }

    private fun selectUploadBatch(pending: List<SyncOutboxEntity>): Result<List<SyncOutboxEntity>> = runCatching {
        val selected = mutableListOf<SyncOutboxEntity>()
        var bytes = 0
        for (item in pending) {
            val payloadBytes = item.payloadJson.toByteArray(Charsets.UTF_8).size
            val itemBytes = payloadBytes + REQUEST_RECORD_OVERHEAD_BYTES
            require(payloadBytes <= MAX_RECORD_CIPHERTEXT_BYTES) {
                "单条同步记录超过 512 KiB，请缩小档案内容"
            }
            if (selected.isNotEmpty() && bytes + itemBytes > MAX_UPLOAD_PAYLOAD_BYTES) break
            require(itemBytes <= MAX_UPLOAD_PAYLOAD_BYTES) { "单条同步记录超过上传安全上限" }
            selected += item
            bytes += itemBytes
        }
        selected
    }

    private suspend fun applyRecord(record: VaultSyncRecord): String? = runCatching {
        if (record.deleted) {
            deleteEntity(record.recordType, record.recordId)
            DELETED_HASH
        } else {
            val plain = vault.decryptEntity(record.recordType, record.recordId, record.ciphertext)
            upsertEntity(record.recordType, plain)
            VaultCrypto.sha256(plain)
        }
    }.getOrNull()

    private suspend fun upsertEntity(type: String, plain: ByteArray) {
        val text = String(plain, Charsets.UTF_8)
        when (type) {
            "cat" -> dao.upsertCat(json.decodeFromString<com.catlink.app.data.db.CatEntity>(text).withCanonicalStatus())
            "medical" -> dao.upsertMedical(json.decodeFromString(text))
            "adopter" -> dao.upsertAdopter(json.decodeFromString(text))
            "case" -> dao.upsertCase(json.decodeFromString(text))
            "contract" -> dao.upsertContract(json.decodeFromString(text))
            "followup" -> dao.upsertFollowUp(json.decodeFromString(text))
            "timeline" -> dao.upsertTimeline(json.decodeFromString(text))
            "todo" -> dao.upsertTodo(json.decodeFromString(text))
            "casenote" -> dao.upsertCaseNote(json.decodeFromString(text))
            "media" -> dao.upsertMedia(json.decodeFromString(text))
            else -> throw IllegalArgumentException("unknown entity type $type")
        }
    }

    private suspend fun deleteEntity(type: String, id: String) {
        when (type) {
            "cat" -> dao.deleteCat(id)
            "medical" -> dao.deleteMedicalById(id)
            "adopter" -> dao.softDeleteAdopter(id, System.currentTimeMillis())
            "case" -> dao.deleteCase(id)
            "contract" -> dao.deleteContractById(id)
            "followup" -> dao.deleteFollowUpById(id)
            "timeline" -> dao.deleteTimelineById(id)
            "todo" -> dao.deleteTodoById(id)
            "casenote" -> dao.deleteCaseNoteById(id)
            "media" -> dao.deleteMediaById(id)
            else -> throw IllegalArgumentException("unknown entity type $type")
        }
    }

    private suspend fun ensureRevisionCounterAtLeast(revision: Long) {
        val current = syncDao.revision(KEY_COUNTER)?.lastLocalRevision ?: 0L
        if (revision > current) {
            syncDao.upsertRevision(SyncRevisionEntity(entityId = KEY_COUNTER, lastLocalRevision = revision))
        }
    }

    private suspend fun nextRevision(): Long {
        val current = syncDao.revision(KEY_COUNTER)?.lastLocalRevision ?: 0L
        val next = current + 1
        syncDao.upsertRevision(SyncRevisionEntity(entityId = KEY_COUNTER, lastLocalRevision = next))
        return next
    }

    private suspend fun saveCursor(cursor: Long) {
        syncDao.upsertRevision(SyncRevisionEntity(entityId = KEY_CURSOR, lastLocalRevision = cursor))
    }

    private companion object {
        const val KEY_CURSOR = "global:cursor"
        const val KEY_COUNTER = "global:revision-counter"
        const val MAX_BATCH = 11
        const val MAX_UPLOAD_PAYLOAD_BYTES = 5 * 1024 * 1024
        const val MAX_RECORD_CIPHERTEXT_BYTES = 512 * 1024
        const val REQUEST_RECORD_OVERHEAD_BYTES = 512
        const val DELETED_HASH = "deleted"
    }
}
