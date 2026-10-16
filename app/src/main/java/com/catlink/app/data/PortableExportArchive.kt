package com.catlink.app.data

import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CaseNoteEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.CatRelationEntity
import com.catlink.app.data.db.CatVersionEntity
import com.catlink.app.data.db.ContractEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.data.db.MediaEntity
import com.catlink.app.data.db.MedicalEntity
import com.catlink.app.data.db.TimelineEntity
import com.catlink.app.data.db.TodoEntity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
internal data class PortableExportPreferences(
    val theme: String,
    val localNickname: String,
    val aiEndpoint: String,
    val aiModel: String,
    val dailyEncouragementDate: String,
    val dailyEncouragementText: String,
    val dailyEncouragementSource: String,
)

@Serializable
internal data class PortableExportAccount(
    val email: String = "",
    val emailVerified: Boolean = false,
)

@Serializable
internal data class PortableAiMessage(val role: String, val content: String)

@Serializable
internal data class PortableAiSession(
    val id: String,
    val title: String,
    val messages: List<PortableAiMessage>,
    val createdAt: Long,
    val updatedAt: Long,
    val summary: String,
    val summarizedCount: Int,
)

@Serializable
internal data class PortablePublicDraft(
    val id: String,
    val catId: String,
    val publicNickname: String,
    val alias: String,
    val story: String,
    val gender: String,
    val ageBand: String,
    val coat: String,
    val personalityTags: String,
    val healthSummary: String,
    val weight: String,
    val dietRestrictions: String,
    val contactWithHumans: String,
    val contactWithCats: String,
    val catStatus: String,
    val statusReason: String,
    val archiveReason: String,
    val rescuedDateEpochDay: Long,
    val statusDateEpochDay: Long?,
    val statusCode: String,
    val coverMediaId: String?,
    val photoUrls: String,
    val mediaUploadState: String?,
    val cityCode: String?,
    val districtCode: String?,
    val publishState: String,
    val serverCatId: String?,
    val updatedAt: Long,
)

@Serializable
internal data class ExportedMedia(
    val sourceReference: String,
    val archivePath: String,
    val included: Boolean,
    val sizeBytes: Long = 0,
    val sha256: String = "",
)

@Serializable
internal data class PortablePersonalData(
    val account: PortableExportAccount = PortableExportAccount(),
    val cats: List<CatEntity> = emptyList(),
    val medicalRecords: List<MedicalEntity> = emptyList(),
    val catVersions: List<CatVersionEntity> = emptyList(),
    val catRelations: List<CatRelationEntity> = emptyList(),
    val adopters: List<AdopterEntity> = emptyList(),
    val adoptionCases: List<CaseEntity> = emptyList(),
    val contracts: List<ContractEntity> = emptyList(),
    val followUps: List<FollowUpEntity> = emptyList(),
    val timelineEvents: List<TimelineEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val caseNotes: List<CaseNoteEntity> = emptyList(),
    val mediaRecords: List<MediaEntity> = emptyList(),
    val publicDrafts: List<PortablePublicDraft> = emptyList(),
    val aiSessions: List<PortableAiSession> = emptyList(),
    val preferences: PortableExportPreferences,
    val mediaFiles: List<ExportedMedia> = emptyList(),
)

internal data class ArchiveMediaSource(
    val sourceReference: String,
    val archivePath: String,
    val open: () -> InputStream?,
)

internal data class PortableExportRequest(
    val appVersion: String,
    val exportedAtMillis: Long,
    val data: PortablePersonalData,
    val mediaSources: List<ArchiveMediaSource>,
    val serverAccountData: JsonElement? = null,
)

internal data class PortableExportResult(
    val entryCount: Int,
    val mediaCount: Int,
    val missingMediaCount: Int,
    val mediaBytes: Long,
    val serverDataIncluded: Boolean,
)

@Serializable
private data class PortableDataDocument(
    val format: String = "youmaoni-personal-data",
    val formatVersion: Int = 1,
    val appName: String = "有猫腻",
    val appVersion: String,
    val exportedAt: String,
    val data: PortablePersonalData,
)

@Serializable
private data class ArchiveFileRecord(val path: String, val sizeBytes: Long, val sha256: String)

@Serializable
private data class ArchiveDomain(val name: String, val records: Int, val csv: String)

@Serializable
private data class ArchiveManifest(
    val format: String = "youmaoni-personal-data",
    val formatVersion: Int = 1,
    val appName: String = "有猫腻",
    val appVersion: String,
    val exportedAt: String,
    val plaintext: Boolean = true,
    val domains: List<ArchiveDomain>,
    val mediaIncluded: Int,
    val mediaMissing: Int,
    val mediaBytes: Long,
    val serverDataFile: String? = null,
    val files: List<ArchiveFileRecord>,
)

@Serializable
private data class AiSessionRow(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val summary: String,
    val summarizedCount: Int,
    val messageCount: Int,
)

@Serializable
private data class AiMessageRow(
    val sessionId: String,
    val position: Int,
    val role: String,
    val content: String,
)

private data class CsvTable(
    val path: String,
    val headers: List<String>,
    val records: Int,
    val writeRows: (Writer) -> Unit,
)

internal object PortableExportArchive {
    private const val MAX_MEDIA_BYTES = 512L * 1024 * 1024
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(output: OutputStream, request: PortableExportRequest): PortableExportResult {
        val exportedAt = Instant.ofEpochMilli(request.exportedAtMillis).toString()
        val records = mutableListOf<ArchiveFileRecord>()
        val media = mutableListOf<ExportedMedia>()
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            request.mediaSources.forEach { source -> media += writeMedia(zip, source, request.exportedAtMillis, records) }
            val data = request.data.copy(mediaFiles = media)
            val document = PortableDataDocument(
                appVersion = request.appVersion,
                exportedAt = exportedAt,
                data = data,
            )
            writeEntry(zip, "data/personal-data.json", request.exportedAtMillis, records) {
                json.encodeToStream(PortableDataDocument.serializer(), document, it)
            }
            request.serverAccountData?.let { serverData ->
                writeEntry(zip, "data/server-account-data.json", request.exportedAtMillis, records) {
                    json.encodeToStream(JsonElement.serializer(), serverData, it)
                }
            }

            val tables = buildTables(data)
            tables.forEach { table -> writeCsv(zip, table, request.exportedAtMillis, records) }
            val domains = tables.map { ArchiveDomain(it.path.substringAfterLast('/').removeSuffix(".csv"), it.records, it.path) }
            writeEntry(zip, "summary/index.html", request.exportedAtMillis, records) { writeHtml(it, data, domains) }
            writeEntry(zip, "README.txt", request.exportedAtMillis, records) {
                writeReadme(it, media, request.serverAccountData != null)
            }

            writeEntry(zip, "checksums.sha256", request.exportedAtMillis, records) { output ->
                OutputStreamWriter(output, Charsets.UTF_8).apply {
                    records.forEach { append(it.sha256).append("  ").append(it.path).append('\n') }
                    flush()
                }
            }
            val manifest = ArchiveManifest(
                appVersion = request.appVersion,
                exportedAt = exportedAt,
                domains = domains,
                mediaIncluded = media.count(ExportedMedia::included),
                mediaMissing = media.count { !it.included },
                mediaBytes = media.sumOf(ExportedMedia::sizeBytes),
                serverDataFile = request.serverAccountData?.let { "data/server-account-data.json" },
                files = records.toList(),
            )
            writeEntry(zip, "manifest.json", request.exportedAtMillis, null) {
                json.encodeToStream(ArchiveManifest.serializer(), manifest, it)
            }
        }
        return PortableExportResult(
            entryCount = records.size + 1,
            mediaCount = media.count(ExportedMedia::included),
            missingMediaCount = media.count { !it.included },
            mediaBytes = media.sumOf(ExportedMedia::sizeBytes),
            serverDataIncluded = request.serverAccountData != null,
        )
    }

    private fun writeMedia(
        zip: ZipOutputStream,
        source: ArchiveMediaSource,
        timestamp: Long,
        records: MutableList<ArchiveFileRecord>,
    ): ExportedMedia {
        require(source.archivePath.startsWith("media/") && ".." !in source.archivePath && '\\' !in source.archivePath) {
            "媒体归档路径无效"
        }
        val input = runCatching(source.open).getOrNull()
            ?: return ExportedMedia(source.sourceReference, source.archivePath, included = false)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        zip.putNextEntry(entry(source.archivePath, timestamp))
        BufferedInputStream(input).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                zip.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                size += count
                require(size <= MAX_MEDIA_BYTES) { "单个媒体超过 512MB" }
            }
        }
        zip.closeEntry()
        val hash = digest.digest().hex()
        records += ArchiveFileRecord(source.archivePath, size, hash)
        return ExportedMedia(source.sourceReference, source.archivePath, true, size, hash)
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        path: String,
        timestamp: Long,
        records: MutableList<ArchiveFileRecord>?,
        write: (OutputStream) -> Unit,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val output = CountingOutputStream(DigestOutputStream(zip, digest))
        zip.putNextEntry(entry(path, timestamp))
        write(output)
        output.flush()
        zip.closeEntry()
        records?.add(ArchiveFileRecord(path, output.count, digest.digest().hex()))
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }
    }

    private fun entry(path: String, timestamp: Long) = ZipEntry(path).apply { time = timestamp }

    private fun buildTables(data: PortablePersonalData): List<CsvTable> {
        return listOf(
            table("tables/account.csv", listOf(data.account), PortableExportAccount.serializer()),
            table("tables/cats.csv", data.cats, CatEntity.serializer()),
            table("tables/medical-records.csv", data.medicalRecords, MedicalEntity.serializer()),
            table("tables/cat-versions.csv", data.catVersions, CatVersionEntity.serializer()),
            table("tables/cat-relations.csv", data.catRelations, CatRelationEntity.serializer()),
            table("tables/adopters.csv", data.adopters, AdopterEntity.serializer()),
            table("tables/adoption-cases.csv", data.adoptionCases, CaseEntity.serializer()),
            table("tables/contracts.csv", data.contracts, ContractEntity.serializer()),
            table("tables/follow-ups.csv", data.followUps, FollowUpEntity.serializer()),
            table("tables/timeline-events.csv", data.timelineEvents, TimelineEntity.serializer()),
            table("tables/todos.csv", data.todos, TodoEntity.serializer()),
            table("tables/case-notes.csv", data.caseNotes, CaseNoteEntity.serializer()),
            table("tables/media-records.csv", data.mediaRecords, MediaEntity.serializer()),
            table("tables/public-drafts.csv", data.publicDrafts, PortablePublicDraft.serializer()),
            table("tables/ai-sessions.csv", data.aiSessions.size, AiSessionRow.serializer()) {
                data.aiSessions.asSequence().map {
                    AiSessionRow(it.id, it.title, it.createdAt, it.updatedAt, it.summary, it.summarizedCount, it.messages.size)
                }
            },
            table("tables/ai-messages.csv", data.aiSessions.sumOf { it.messages.size }, AiMessageRow.serializer()) {
                data.aiSessions.asSequence().flatMap { session ->
                    session.messages.asSequence().mapIndexed { index, message ->
                        AiMessageRow(session.id, index, message.role, message.content)
                    }
                }
            },
            table("tables/preferences.csv", listOf(data.preferences), PortableExportPreferences.serializer()),
            table("tables/media-files.csv", data.mediaFiles, ExportedMedia.serializer()),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> table(path: String, values: List<T>, serializer: KSerializer<T>): CsvTable {
        return table(path, values.size, serializer) { values.asSequence() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> table(
        path: String,
        records: Int,
        serializer: KSerializer<T>,
        values: () -> Sequence<T>,
    ): CsvTable {
        val headers = (0 until serializer.descriptor.elementsCount).map(serializer.descriptor::getElementName)
        return CsvTable(path, headers, records) { writer ->
            values().forEach { value ->
                val row = json.encodeToJsonElement(serializer, value).jsonObject
                writer.appendLine(headers.joinToString(",") { name -> csvCell(row[name].csvValue()) })
            }
        }
    }

    private fun writeCsv(
        zip: ZipOutputStream,
        table: CsvTable,
        timestamp: Long,
        records: MutableList<ArchiveFileRecord>,
    ) = writeEntry(zip, table.path, timestamp, records) { output ->
        OutputStreamWriter(output, Charsets.UTF_8).apply {
            append('\uFEFF')
            appendLine(table.headers.joinToString(",") { csvCell(it) })
            table.writeRows(this)
            flush()
        }
    }

    private fun JsonPrimitive?.contentOrEmpty(): String = this?.content.orEmpty()

    private fun JsonElement?.csvValue(): String = when (this) {
        null, JsonNull -> ""
        is JsonPrimitive -> contentOrEmpty()
        else -> toString()
    }

    internal fun csvCell(value: String): String {
        val normalized = value.replace("\r", " ").replace("\n", " ")
        val first = normalized.firstOrNull { !it.isWhitespace() }
        val safe = if (first in setOf('=', '+', '-', '@')) "'$normalized" else normalized
        return if (safe.any { it == ',' || it == '"' }) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private fun writeHtml(output: OutputStream, data: PortablePersonalData, domains: List<ArchiveDomain>) {
        OutputStreamWriter(output, Charsets.UTF_8).apply {
            append("""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>有猫腻个人数据摘要</title>
<style>body{font-family:sans-serif;max-width:960px;margin:32px auto;line-height:1.6}table{border-collapse:collapse;width:100%;margin-bottom:28px}th,td{border:1px solid #ccc;padding:8px;text-align:left}h1,h2{margin-top:24px}.warning{font-weight:bold}</style></head>
<body><h1>有猫腻个人数据摘要</h1><p class="warning">此归档为明文，包含敏感个人资料，请妥善保管。</p>
<h2>数据范围</h2><table><thead><tr><th>数据域</th><th>记录数</th><th>CSV 文件</th></tr></thead><tbody>""")
            domains.forEach {
                append("<tr><td>").append(escapeHtml(it.name)).append("</td><td>").append(it.records.toString())
                    .append("</td><td>").append(escapeHtml(it.csv)).append("</td></tr>\n")
            }
            append("</tbody></table><h2>猫咪档案</h2><table><thead><tr><th>昵称</th><th>状态</th><th>健康状态</th><th>地区</th></tr></thead><tbody>")
            if (data.cats.isEmpty()) append("<tr><td colspan=\"4\">暂无猫咪档案</td></tr>")
            data.cats.forEach {
                append("<tr><td>").append(escapeHtml(it.nickname)).append("</td><td>").append(escapeHtml(it.status))
                    .append("</td><td>").append(escapeHtml(it.healthStatus)).append("</td><td>")
                    .append(escapeHtml(it.cityName + it.districtName)).append("</td></tr>\n")
            }
            append("</tbody></table></body></html>")
            flush()
        }
    }

    private fun writeReadme(output: OutputStream, media: List<ExportedMedia>, serverDataIncluded: Boolean) {
        OutputStreamWriter(output, Charsets.UTF_8).apply {
            val serverScope = if (serverDataIncluded) {
                "已包含登录账号在 Gin 服务端保存的公开猫咪、社区互动、领养、消息、通知、媒体元数据和加密同步密文。"
            } else {
                "当前未登录，因此不包含服务端账号数据。"
            }
            append("""有猫腻个人数据可移植归档

本 ZIP 为明文文件，可能包含领养人联系方式、合同记录、AI 对话和照片，请勿上传到不可信位置。$serverScope

目录说明：
- manifest.json：格式版本、数据域数量和文件校验值。
- data/personal-data.json：完整结构化数据，UTF-8 编码。
- data/server-account-data.json：登录账号的服务端个人数据；未登录时不存在。
- tables/*.csv：每个数据域的独立表格，UTF-8 BOM 编码并已中和公式前缀。
- summary/index.html：浏览器可直接打开的摘要。
- media/：可读取的档案、合同和相册媒体，共 ${media.count(ExportedMedia::included)} 个。
- checksums.sha256：归档载荷 SHA-256；manifest.json 不参与自校验。

有 ${media.count { !it.included }} 个媒体引用在导出时不可读取，详情见 data/personal-data.json 的 mediaFiles。
API Key、密码、Token、同步密钥、恢复口令和内部同步队列不会导出。
""")
            flush()
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
