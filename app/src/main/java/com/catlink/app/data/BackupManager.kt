package com.catlink.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.VaultDao
import com.catlink.app.data.db.VaultSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.nio.ByteBuffer
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密资料库备份（产品文档 9.3）：
 * 全部私密实体序列化为 JSON → AES-256/GCM（口令派生密钥）→ 写入用户选择的文件。
 * 导入时校验口令，失败不写入原资料库。
 */
object BackupManager {

    private const val LEGACY_ITERATIONS = 120_000
    private const val ITERATIONS = 310_000
    private const val MAX_PLAIN_BACKUP_BYTES = 16L * 1024 * 1024
    private const val MAX_BACKUP_CONTAINER_BYTES = MAX_PLAIN_BACKUP_BYTES + 128L * 1024
    private const val MAX_LEGACY_CONTAINER_BYTES = 128L * 1024 * 1024
    private const val MAX_JSON_DEPTH = 16
    private const val MAX_ENTITY_COUNT = 50_000
    private const val MAX_STRING_CHARS = 65_536
    private val BACKUP_MAGIC = byteArrayOf('C'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())
    private const val TAG_LEN = 128
    private val operationMutex = Mutex()

    data class Result(val ok: Boolean, val message: String, val imported: Int = 0)

    suspend fun export(context: Context, dao: VaultDao, uri: Uri, password: String): Result {
        if (password.length < 12) return Result(false, "口令至少 12 位")
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val encrypted = buildEncryptedBackup(dao, password)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
                        ?: return@withLock Result(false, "无法写入文件")
                    Result(true, "备份已导出")
                } catch (e: Exception) {
                    Result(false, "导出失败：${e.message}")
                }
            }
        }
    }

    /** 导出到本地文件（远端同步用） */
    suspend fun exportToFile(dao: VaultDao, file: java.io.File, password: String): Result {
        if (password.length < 12) return Result(false, "口令至少 12 位")
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val encrypted = buildEncryptedBackup(dao, password)
                    file.parentFile?.mkdirs()
                    file.writeBytes(encrypted)
                    Result(true, "备份已生成")
                } catch (e: Exception) {
                    Result(false, "导出失败：${e.message}")
                }
            }
        }
    }

    suspend fun exportSnapshotToFile(snapshot: VaultSnapshot, file: java.io.File, password: String): Result {
        if (password.length < 12) return Result(false, "口令至少 12 位")
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val encrypted = buildEncryptedBackup(snapshot, password)
                    file.parentFile?.mkdirs()
                    file.writeBytes(encrypted)
                    Result(true, "备份已生成")
                } catch (e: Exception) {
                    Result(false, "导出失败：${e.message}")
                }
            }
        }
    }

    private suspend fun buildEncryptedBackup(dao: VaultDao, password: String): ByteArray =
        buildEncryptedBackup(dao.snapshot(), password)

    private fun buildEncryptedBackup(snapshot: VaultSnapshot, password: String): ByteArray {
        val json = JSONObject().apply {
            put("app", "CatLink")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("cats", JSONArray().also { arr -> snapshot.cats.forEach { arr.put(entityJson(it)) } })
            put("medicals", JSONArray().also { arr -> snapshot.medicals.forEach { arr.put(medicalJson(it)) } })
        put("catVersions", JSONArray().also { arr -> snapshot.versions.forEach { arr.put(versionJson(it)) } })
        put("catRelations", JSONArray().also { arr -> snapshot.relations.forEach { arr.put(relationJson(it)) } })
            put("adopters", JSONArray().also { arr -> snapshot.adopters.forEach { arr.put(adopterJson(it)) } })
            put("cases", JSONArray().also { arr -> snapshot.cases.forEach { arr.put(caseJson(it)) } })
            put("contracts", JSONArray().also { arr -> snapshot.contracts.forEach { arr.put(contractJson(it)) } })
            put("followUps", JSONArray().also { arr -> snapshot.followUps.forEach { arr.put(followUpJson(it)) } })
            put("timeline", JSONArray().also { arr -> snapshot.timeline.forEach { arr.put(timelineJson(it)) } })
            put("todos", JSONArray().also { arr -> snapshot.todos.forEach { arr.put(todoJson(it)) } })
            put("caseNotes", JSONArray().also { arr -> snapshot.caseNotes.forEach { arr.put(caseNoteJson(it)) } })
            put("media", JSONArray().also { arr -> snapshot.media.forEach { arr.put(mediaJson(it)) } })
        }
        val plain = json.toString().toByteArray(Charsets.UTF_8)
        require(plain.size.toLong() <= MAX_PLAIN_BACKUP_BYTES) { "资料备份超过 16MB，请先清理无用记录" }
        return encrypt(plain, password)
    }

    private fun caseNoteJson(n: com.catlink.app.data.db.CaseNoteEntity) = JSONObject().apply {
        put("id", n.id); put("caseId", n.caseId); put("date", n.noteDateEpochDay)
        put("type", n.type); put("content", n.content); put("createdAt", n.createdAt)
    }

    private fun mediaJson(m: com.catlink.app.data.db.MediaEntity) = JSONObject().apply {
        put("id", m.id); put("catId", m.catId); put("uri", m.uri)
        put("localUri", m.localUri); put("remoteUrl", m.remoteUrl); put("serverMediaId", m.serverMediaId)
        put("syncState", m.syncState); put("sortOrder", m.sortOrder); put("createdAt", m.createdAt)
    }

    /** 从本地文件导入（远端同步用） */
    suspend fun importFromFile(db: AppDatabase, file: java.io.File, password: String): Result {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val prefix = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
                    val limit = containerLimit(prefix)
                    require(file.length() in 1..limit) { "备份文件大小异常" }
                    requireMemoryFor(file.length())
                    val bytes = file.readBytes()
                    val decrypted = decrypt(bytes, password) ?: return@withLock Result(false, "口令错误或文件已损坏")
                    restore(db, decrypted, plainLimit(prefix))
                } catch (e: Exception) {
                    Result(false, "导入失败：${e.message}")
                }
            }
        }
    }

    suspend fun import(context: Context, db: AppDatabase, uri: Uri, password: String): Result {
        val temporary = File(context.cacheDir, "catlink_import_${System.nanoTime()}.clb")
        return try {
            withContext(Dispatchers.IO) {
                val declared = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                require(declared < 0 || declared in 1..MAX_LEGACY_CONTAINER_BYTES) { "备份文件大小异常" }
                val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
                input.use { source ->
                    temporary.outputStream().use { output -> NetworkEndpoint.copyBounded(source, output, MAX_LEGACY_CONTAINER_BYTES) }
                }
            }
            importFromFile(db, temporary, password)
        } catch (error: Throwable) {
            Result(false, "导入失败：${error.message ?: "无法读取备份"}")
        } finally {
            temporary.delete()
        }
    }

    private fun containerLimit(prefix: ByteArray): Long =
        if (prefix.size >= 4 && prefix.copyOfRange(0, 4).contentEquals(BACKUP_MAGIC)) MAX_BACKUP_CONTAINER_BYTES
        else MAX_LEGACY_CONTAINER_BYTES

    fun legacyContainerLimit(): Long = MAX_LEGACY_CONTAINER_BYTES

    private fun plainLimit(prefix: ByteArray): Long =
        if (prefix.size >= 4 && prefix.copyOfRange(0, 4).contentEquals(BACKUP_MAGIC)) MAX_PLAIN_BACKUP_BYTES
        else minOf(64L * 1024 * 1024, availableHeapBytes() / 6L)

    private fun availableHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    private fun requireMemoryFor(containerBytes: Long) {
        require(containerBytes > 0 && availableHeapBytes() >= containerBytes * 4L) { "可用内存不足，无法安全解析该旧版备份" }
    }

    suspend fun validateFile(file: File, password: String): Result = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                val prefix = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
                require(file.length() in 1..containerLimit(prefix)) { "备份文件大小异常" }
                requireMemoryFor(file.length())
                val decrypted = decrypt(file.readBytes(), password) ?: return@withLock Result(false, "口令错误或文件已损坏")
                parseAndValidate(decrypted, plainLimit(prefix))
                Result(true, "备份校验通过")
            } catch (error: Throwable) {
                Result(false, error.message ?: "备份校验失败")
            }
        }
    }

    private fun parseAndValidate(decrypted: ByteArray, maxPlainBytes: Long): JSONObject {
        require(decrypted.size.toLong() <= maxPlainBytes) { "备份内容超过当前格式或设备的安全大小限制" }
        val text = String(decrypted, Charsets.UTF_8)
        validateJsonText(text)
        val json = JSONObject(text).also(::validateJsonShape)
        require(json.optString("app") == "CatLink") { "不是 CatLink 备份文件" }
        val version = json.optInt("version", 1)
        require(version in 1..2) { "备份版本 $version 暂不受支持，请升级 CatLink" }
        // 实际调用全部实体解析器，确保破坏性恢复前类型/必填字段完整。
        json.optJSONArray("cats")?.let { a -> for (i in 0 until a.length()) catFromJson(a.getJSONObject(i)) }
        json.optJSONArray("medicals")?.let { a -> for (i in 0 until a.length()) medicalFromJson(a.getJSONObject(i)) }
        json.optJSONArray("catVersions")?.let { a -> for (i in 0 until a.length()) versionFromJson(a.getJSONObject(i)) }
        json.optJSONArray("catRelations")?.let { a -> for (i in 0 until a.length()) relationFromJson(a.getJSONObject(i)) }
        json.optJSONArray("adopters")?.let { a -> for (i in 0 until a.length()) adopterFromJson(a.getJSONObject(i)) }
        json.optJSONArray("cases")?.let { a -> for (i in 0 until a.length()) caseFromJson(a.getJSONObject(i)) }
        json.optJSONArray("contracts")?.let { a -> for (i in 0 until a.length()) contractFromJson(a.getJSONObject(i)) }
        json.optJSONArray("followUps")?.let { a -> for (i in 0 until a.length()) followUpFromJson(a.getJSONObject(i)) }
        json.optJSONArray("timeline")?.let { a -> for (i in 0 until a.length()) timelineFromJson(a.getJSONObject(i)) }
        json.optJSONArray("todos")?.let { a -> for (i in 0 until a.length()) todoFromJson(a.getJSONObject(i)) }
        json.optJSONArray("caseNotes")?.let { a -> for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); o.getString("id"); o.getString("caseId")
        } }
        json.optJSONArray("media")?.let { a -> for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); o.getString("id"); o.getString("catId")
        } }
        return json
    }

    private suspend fun restore(db: AppDatabase, decrypted: ByteArray, maxPlainBytes: Long): Result {
        val json = try {
            parseAndValidate(decrypted, maxPlainBytes)
        } catch (error: StackOverflowError) {
            return Result(false, "备份结构过于复杂")
        } catch (error: OutOfMemoryError) {
            return Result(false, "备份内容过大，无法安全解析")
        } catch (error: Throwable) {
            return Result(false, "备份格式无效：${error.message ?: "无法解析"}")
        }
        var count = 0
        db.withTransaction {
            val dao = db.vaultDao()
            dao.clearAll()
            json.optJSONArray("cats")?.let { arr ->
                for (i in 0 until arr.length()) {
                    dao.upsertCat(catFromJson(arr.getJSONObject(i)))
                    count++
                }
            }
            json.optJSONArray("medicals")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertMedical(medicalFromJson(arr.getJSONObject(i)))
    json.optJSONArray("catVersions")?.let { arr -> for (i in 0 until arr.length()) dao.insertCatVersion(versionFromJson(arr.getJSONObject(i))) }
    json.optJSONArray("catRelations")?.let { arr -> for (i in 0 until arr.length()) dao.insertCatRelation(relationFromJson(arr.getJSONObject(i))) }
            }
            json.optJSONArray("adopters")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertAdopter(adopterFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("cases")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertCase(caseFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("contracts")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertContract(contractFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("followUps")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertFollowUp(followUpFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("timeline")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertTimeline(timelineFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("todos")?.let { arr ->
                for (i in 0 until arr.length()) dao.upsertTodo(todoFromJson(arr.getJSONObject(i)))
            }
            json.optJSONArray("caseNotes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    dao.upsertCaseNote(
                        com.catlink.app.data.db.CaseNoteEntity(
                            id = o.getString("id"),
                            caseId = o.getString("caseId"),
                            noteDateEpochDay = o.optLong("date", 0L),
                            type = o.optString("type", "其他"),
                            content = o.optString("content", ""),
                            createdAt = o.optLong("createdAt", 0L),
                        )
                    )
                }
            }
            json.optJSONArray("media")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    dao.upsertMedia(
                        com.catlink.app.data.db.MediaEntity(
                            id = o.getString("id"),
                            catId = o.getString("catId"),
                            uri = o.optString("uri", ""),
                            localUri = o.optString("localUri", ""),
                            remoteUrl = o.optString("remoteUrl", ""),
                            serverMediaId = o.optString("serverMediaId", ""),
                            syncState = o.optString("syncState", "local"),
                            sortOrder = o.optLong("sortOrder", o.optLong("createdAt", 0L)),
                            createdAt = o.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }
        return Result(true, "恢复完成", imported = count)
    }

    private fun validateJsonText(text: String) {
        var depth = 0
        var inString = false
        var escaped = false
        var stringLength = 0
        text.forEach { ch ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> { inString = false; stringLength = 0 }
                    else -> {
                        stringLength++
                        require(stringLength <= MAX_STRING_CHARS) { "备份字段文本过长" }
                    }
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{', '[' -> { depth++; require(depth <= MAX_JSON_DEPTH) { "备份嵌套过深" } }
                    '}', ']' -> { depth--; require(depth >= 0) { "备份结构不完整" } }
                }
            }
        }
        require(!inString && depth == 0) { "备份结构不完整" }
    }

    private fun validateJsonShape(root: JSONObject) {
        val arrays = listOf("cats", "medicals", "adopters", "cases", "contracts", "followUps", "timeline", "todos", "caseNotes", "media", "catVersions", "catRelations")
        arrays.forEach { name ->
            val array = root.optJSONArray(name) ?: return@forEach
            require(array.length() <= MAX_ENTITY_COUNT) { "$name 记录数量超过限制" }
            for (index in 0 until array.length()) {
                val entity = array.optJSONObject(index) ?: error("$name 包含无效记录")
                var fields = 0
                val keys = entity.keys()
                while (keys.hasNext()) {
                    val key = keys.next(); fields++
                    require(fields <= 64) { "$name 记录字段过多" }
                    val value = entity.opt(key)
                    if (value is String) require(value.length <= MAX_STRING_CHARS) { "$name 字段过长" }
                }
            }
        }
    }

    // ---------- 序列化 ----------

    private fun entityJson(e: com.catlink.app.data.db.CatEntity) = JSONObject().apply {
        put("id", e.id); put("nickname", e.nickname); put("alias", e.alias)
        put("status", e.status); put("healthStatus", e.healthStatus)
        put("gender", e.gender); put("ageEstimate", e.ageEstimate); put("coat", e.coat)
        put("weight", e.weight); put("personality", e.personality); put("story", e.story)
        put("dietRestrictions", e.dietRestrictions); put("contactWithHumans", e.contactWithHumans); put("contactWithCats", e.contactWithCats)
        e.statusDateEpochDay?.let { put("statusDateEpochDay", it) }; put("statusReason", e.statusReason)
        put("archiveReason", e.archiveReason); put("legacyStatus", e.legacyStatus)
        put("rescuedDateEpochDay", e.rescuedDateEpochDay); put("tags", e.tags)
        put("coverColorIndex", e.coverColorIndex); put("coverUri", e.coverUri)
        e.deletedAt?.let { put("deletedAt", it) }
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
    }

    private fun versionJson(e: com.catlink.app.data.db.CatVersionEntity) = JSONObject().apply {
        put("id", e.id); put("catId", e.catId); put("versionNo", e.versionNo)
        put("snapshotJson", e.snapshotJson); put("changeSummary", e.changeSummary)
        put("reason", e.reason); put("createdAt", e.createdAt)
    }

    private fun relationJson(e: com.catlink.app.data.db.CatRelationEntity) = JSONObject().apply {
        put("id", e.id); put("catId", e.catId); put("relatedCatId", e.relatedCatId)
        put("relationType", e.relationType); put("note", e.note); put("createdAt", e.createdAt)
    }

    private fun medicalJson(e: com.catlink.app.data.db.MedicalEntity) = JSONObject().apply {
        put("id", e.id); put("catId", e.catId); put("dateEpochDay", e.dateEpochDay)
        put("category", e.category); put("title", e.title); put("detail", e.detail)
        put("hospital", e.hospital); put("cost", e.cost)
        put("weight", e.weight); put("behaviorNote", e.behaviorNote); put("result", e.result)
        e.nextReminderEpochDay?.let { put("nextReminderEpochDay", it) }
        put("createdAt", e.createdAt)
    }

    private fun adopterJson(e: com.catlink.app.data.db.AdopterEntity) = JSONObject().apply {
        put("id", e.id); put("name", e.name); put("phone", e.phone)
        put("wechat", e.wechat); put("qq", e.qq); put("email", e.email)
        put("idCard", e.idCard); put("address", e.address)
        put("backupContact", e.backupContact); put("preferredContact", e.preferredContact)
        put("residence", e.residence); put("petExperience", e.petExperience); put("familyMembers", e.familyMembers)
        put("contactAuthorized", e.contactAuthorized); e.authorizedAt?.let { put("authorizedAt", it) }
        put("notes", e.notes); e.deletedAt?.let { put("deletedAt", it) }; put("createdAt", e.createdAt)
    }

    private fun caseJson(e: com.catlink.app.data.db.CaseEntity) = JSONObject().apply {
        put("id", e.id); put("catId", e.catId); put("adopterId", e.adopterId); put("stage", e.stage)
        put("source", e.source); put("notes", e.notes)
        put("rejectReason", e.rejectReason); put("withdrawReason", e.withdrawReason)
        e.handoverDateEpochDay?.let { put("handoverDateEpochDay", it) }
        if (e.handoverPhotoUri.isNotBlank()) put("handoverPhotoUri", e.handoverPhotoUri)
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
    }

    private fun contractJson(e: com.catlink.app.data.db.ContractEntity) = JSONObject().apply {
        put("id", e.id); put("caseId", e.caseId); put("status", e.status)
        e.signedDateEpochDay?.let { put("signedDateEpochDay", it) }
        put("fileHash", e.fileHash)
        if (e.signaturePhotoUri.isNotBlank()) put("signaturePhotoUri", e.signaturePhotoUri)
        if (e.handoverPhotoUri.isNotBlank()) put("handoverPhotoUri", e.handoverPhotoUri)
        put("notes", e.notes); put("createdAt", e.createdAt)
    }

    private fun followUpJson(e: com.catlink.app.data.db.FollowUpEntity) = JSONObject().apply {
        put("id", e.id); put("caseId", e.caseId); put("planDateEpochDay", e.planDateEpochDay)
        put("status", e.status); e.doneDateEpochDay?.let { put("doneDateEpochDay", it) }
        put("method", e.method); put("note", e.note); put("riskLevel", e.riskLevel)
        put("statusAtVisit", e.statusAtVisit); put("createdAt", e.createdAt)
    }

    private fun timelineJson(e: com.catlink.app.data.db.TimelineEntity) = JSONObject().apply {
        put("id", e.id); put("catId", e.catId); put("dateEpochDay", e.dateEpochDay)
        put("title", e.title); put("detail", e.detail); put("kind", e.kind); put("createdAt", e.createdAt)
    }

    private fun todoJson(e: com.catlink.app.data.db.TodoEntity) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("subtitle", e.subtitle)
        e.dueEpochDay?.let { put("dueEpochDay", it) }
        put("priority", e.priority); put("done", e.done)
        e.catId?.let { put("catId", it) }
        put("createdAt", e.createdAt)
    }

    private fun catFromJson(o: JSONObject) = com.catlink.app.data.db.CatEntity(
        id = o.getString("id"), nickname = o.getString("nickname"), alias = o.optString("alias"),
        status = o.optString("status"), healthStatus = o.optString("healthStatus"),
        gender = o.optString("gender"), ageEstimate = o.optString("ageEstimate"), coat = o.optString("coat"),
        weight = o.optString("weight"), personality = o.optString("personality"), story = o.optString("story"),
        dietRestrictions = o.optString("dietRestrictions"), contactWithHumans = o.optString("contactWithHumans"), contactWithCats = o.optString("contactWithCats"),
        statusDateEpochDay = if (o.has("statusDateEpochDay")) o.getLong("statusDateEpochDay") else null,
        statusReason = o.optString("statusReason"),
        archiveReason = o.optString("archiveReason"), legacyStatus = o.optString("legacyStatus"),
        rescuedDateEpochDay = o.getLong("rescuedDateEpochDay"), tags = o.optString("tags"),
        coverColorIndex = o.optInt("coverColorIndex"), coverUri = o.optString("coverUri"),
        deletedAt = if (o.has("deletedAt")) o.getLong("deletedAt") else null,
        createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
    ).withCanonicalStatus()

    private fun versionFromJson(o: JSONObject) = com.catlink.app.data.db.CatVersionEntity(
        id = o.getString("id"), catId = o.getString("catId"), versionNo = o.getInt("versionNo"),
        snapshotJson = o.optString("snapshotJson"), changeSummary = o.optString("changeSummary"),
        reason = o.optString("reason"), createdAt = o.optLong("createdAt"),
    )

    private fun relationFromJson(o: JSONObject) = com.catlink.app.data.db.CatRelationEntity(
        id = o.getString("id"), catId = o.getString("catId"), relatedCatId = o.getString("relatedCatId"),
        relationType = o.optString("relationType"), note = o.optString("note"), createdAt = o.optLong("createdAt"),
    )

    private fun medicalFromJson(o: JSONObject) = com.catlink.app.data.db.MedicalEntity(
        id = o.getString("id"), catId = o.getString("catId"), dateEpochDay = o.getLong("dateEpochDay"),
        category = o.optString("category"), title = o.optString("title"), detail = o.optString("detail"),
        hospital = o.optString("hospital"), cost = o.optString("cost"),
        weight = o.optString("weight"), behaviorNote = o.optString("behaviorNote"), result = o.optString("result"),
        nextReminderEpochDay = if (o.has("nextReminderEpochDay")) o.getLong("nextReminderEpochDay") else null,
        createdAt = o.optLong("createdAt"),
    )

    private fun adopterFromJson(o: JSONObject) = com.catlink.app.data.db.AdopterEntity(
        id = o.getString("id"), name = o.optString("name"), phone = o.optString("phone"),
        wechat = o.optString("wechat"), qq = o.optString("qq"), email = o.optString("email"),
        idCard = o.optString("idCard"), address = o.optString("address"),
        backupContact = o.optString("backupContact"), preferredContact = o.optString("preferredContact"),
        residence = o.optString("residence"), petExperience = o.optString("petExperience"), familyMembers = o.optString("familyMembers"),
        contactAuthorized = o.optBoolean("contactAuthorized", false),
        authorizedAt = if (o.has("authorizedAt")) o.getLong("authorizedAt") else null,
        notes = o.optString("notes"),
        deletedAt = if (o.has("deletedAt")) o.getLong("deletedAt") else null,
        createdAt = o.optLong("createdAt"),
    )

    private fun caseFromJson(o: JSONObject) = com.catlink.app.data.db.CaseEntity(
        id = o.getString("id"), catId = o.getString("catId"), adopterId = o.optString("adopterId"),
        stage = o.optString("stage"), source = o.optString("source"), notes = o.optString("notes"),
        handoverDateEpochDay = if (o.has("handoverDateEpochDay")) o.getLong("handoverDateEpochDay") else null,
        handoverPhotoUri = o.optString("handoverPhotoUri"),
        rejectReason = o.optString("rejectReason"), withdrawReason = o.optString("withdrawReason"),
        createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
    )

    private fun contractFromJson(o: JSONObject) = com.catlink.app.data.db.ContractEntity(
        id = o.getString("id"), caseId = o.getString("caseId"), status = o.optString("status"),
        signedDateEpochDay = if (o.has("signedDateEpochDay")) o.getLong("signedDateEpochDay") else null,
        fileHash = o.optString("fileHash"),
        signaturePhotoUri = o.optString("signaturePhotoUri"),
        handoverPhotoUri = o.optString("handoverPhotoUri"),
        notes = o.optString("notes"), createdAt = o.optLong("createdAt"),
    )

    private fun followUpFromJson(o: JSONObject) = com.catlink.app.data.db.FollowUpEntity(
        id = o.getString("id"), caseId = o.getString("caseId"), planDateEpochDay = o.getLong("planDateEpochDay"),
        status = o.optString("status"),
        doneDateEpochDay = if (o.has("doneDateEpochDay")) o.getLong("doneDateEpochDay") else null,
        method = o.optString("method"), note = o.optString("note"), riskLevel = o.optInt("riskLevel"),
        statusAtVisit = o.optString("statusAtVisit"),
        createdAt = o.optLong("createdAt"),
    )

    private fun timelineFromJson(o: JSONObject) = com.catlink.app.data.db.TimelineEntity(
        id = o.getString("id"), catId = o.getString("catId"), dateEpochDay = o.getLong("dateEpochDay"),
        title = o.optString("title"), detail = o.optString("detail"), kind = o.optString("kind"),
        createdAt = o.optLong("createdAt"),
    )

    private fun todoFromJson(o: JSONObject) = com.catlink.app.data.db.TodoEntity(
        id = o.getString("id"), title = o.optString("title"), subtitle = o.optString("subtitle"),
        dueEpochDay = if (o.has("dueEpochDay")) o.getLong("dueEpochDay") else null,
        priority = o.optString("priority"), done = o.optBoolean("done"),
        catId = if (o.has("catId")) o.getString("catId") else null,
        createdAt = o.optLong("createdAt"),
    )

    // ---------- AES-256/GCM ----------

    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val ct = cipher.doFinal(data)
        return BACKUP_MAGIC + ByteBuffer.allocate(4).putInt(ITERATIONS).array() + salt + cipher.iv + ct
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray? {
        return try {
            val v2 = data.size >= 4 && data.copyOfRange(0, 4).contentEquals(BACKUP_MAGIC)
            val offset = if (v2) 8 else 0
            val iterations = if (v2) ByteBuffer.wrap(data, 4, 4).int else LEGACY_ITERATIONS
            require(iterations in LEGACY_ITERATIONS..1_000_000)
            if (data.size < offset + 16 + 12 + 16) return null
            val salt = data.copyOfRange(offset, offset + 16)
            val iv = data.copyOfRange(offset + 16, offset + 28)
            val ct = data.copyOfRange(offset + 28, data.size)
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN, iv))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
