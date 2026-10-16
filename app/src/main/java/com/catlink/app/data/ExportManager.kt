package com.catlink.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.PublicDraftEntity
import com.catlink.app.data.db.VaultSnapshot
import com.catlink.app.network.AuthSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/** 明文可移植归档：仅写入用户通过 SAF 明确选择的位置。 */
object ExportManager {
    data class ExportResult(val ok: Boolean, val message: String, val fileName: String = "")

    suspend fun exportPortable(
        context: Context,
        database: AppDatabase,
        settings: SettingsStore,
        authSession: AuthSessionManager,
        publicApi: PublicApi,
        uri: Uri,
        appVersion: String,
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val serverData = if (authSession.isSignedIn()) {
                publicApi.exportAccountData().getOrElse {
                    error("服务端个人数据获取失败，请检查网络后重试")
                }
            } else {
                null
            }
            val (snapshot, drafts) = database.withTransaction {
                database.vaultDao().snapshot() to database.syncDao().allDrafts().first()
            }
            val data = portableData(snapshot, drafts, settings, authSession)
            val output = context.contentResolver.openOutputStream(uri, "w") ?: error("无法写入目标文件")
            output.use {
                PortableExportArchive.write(
                    output = it,
                    request = PortableExportRequest(
                        appVersion = appVersion,
                        exportedAtMillis = System.currentTimeMillis(),
                        data = data,
                        mediaSources = mediaSources(context, snapshot),
                        serverAccountData = serverData,
                    ),
                )
            }
        }.fold(
            onSuccess = { result ->
                val missing = if (result.missingMediaCount == 0) "" else "，${result.missingMediaCount} 个媒体不可读取"
                val server = if (result.serverDataIncluded) "，已包含服务端账号数据" else ""
                ExportResult(true, "已导出 ${result.mediaCount} 个媒体$missing$server")
            },
            onFailure = { ExportResult(false, "导出失败：${it.message ?: "无法写入文件"}") },
        )
    }

    internal fun csvCell(value: String): String = PortableExportArchive.csvCell(value)

    private suspend fun portableData(
        snapshot: VaultSnapshot,
        drafts: List<PublicDraftEntity>,
        settings: SettingsStore,
        authSession: AuthSessionManager,
    ): PortablePersonalData {
        val ai = settings.aiConfig.first()
        val encouragement = settings.dailyEncouragement.first()
        return PortablePersonalData(
            account = PortableExportAccount(
                email = authSession.currentEmail().orEmpty(),
                emailVerified = authSession.currentEmailVerified(),
            ),
            cats = snapshot.cats,
            medicalRecords = snapshot.medicals,
            catVersions = snapshot.versions,
            catRelations = snapshot.relations,
            adopters = snapshot.adopters,
            adoptionCases = snapshot.cases,
            contracts = snapshot.contracts,
            followUps = snapshot.followUps,
            timelineEvents = snapshot.timeline,
            todos = snapshot.todos,
            caseNotes = snapshot.caseNotes,
            mediaRecords = snapshot.media,
            publicDrafts = drafts.map { it.portable() },
            aiSessions = settings.aiSessions.first().map { it.portable() },
            preferences = PortableExportPreferences(
                theme = settings.themeStyle.first().name,
                localNickname = settings.nickname.first(),
                aiEndpoint = endpointOrigin(ai.endpoint),
                aiModel = ai.model,
                dailyEncouragementDate = encouragement.attemptedDate,
                dailyEncouragementText = encouragement.text,
                dailyEncouragementSource = encouragement.source,
            ),
        )
    }

    private fun SettingsStore.AiSession.portable() = PortableAiSession(
        id = id,
        title = title,
        messages = messages.map { PortableAiMessage(it.role, it.content) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        summary = summary,
        summarizedCount = summarizedCount,
    )

    internal fun endpointOrigin(raw: String): String = runCatching {
        val uri = URI(raw)
        URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString()
    }.getOrDefault("")

    private fun mediaSources(context: Context, snapshot: VaultSnapshot): List<ArchiveMediaSource> {
        val references = linkedSetOf<String>()
        snapshot.cats.mapTo(references) { it.coverUri }
        snapshot.media.flatMapTo(references) { listOf(it.uri, it.localUri) }
        snapshot.cases.mapTo(references) { it.handoverPhotoUri }
        snapshot.contracts.forEach { contract ->
            references += contract.signaturePhotoUri
            references += contract.handoverPhotoUri
            references += contract.photoUris.split(',').map(String::trim)
        }
        MediaFiles.listFiles(context).mapTo(references, File::getAbsolutePath)
        return references.filter(String::isNotBlank).map { reference ->
            ArchiveMediaSource(
                sourceReference = reference,
                archivePath = archivePath(reference),
                open = { openMedia(context, reference) },
            )
        }
    }

    private fun archivePath(reference: String): String {
        val rawName = Uri.parse(reference).lastPathSegment.orEmpty().substringBefore('?')
        val safeName = rawName.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('.').take(80).ifBlank { "media.bin" }
        val id = MessageDigest.getInstance("SHA-256")
            .digest(reference.toByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }
        return "media/${id}_$safeName"
    }

    private fun openMedia(context: Context, reference: String): InputStream? {
        val uri = Uri.parse(reference)
        return when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "https" -> openRemote(reference)
            "http" -> if (uri.host in setOf("127.0.0.1", "localhost", "::1")) openRemote(reference) else null
            "file", null -> openPrivateFile(context, uri.path ?: reference)
            else -> null
        }
    }

    private fun openPrivateFile(context: Context, path: String): InputStream? {
        val root = context.filesDir.canonicalFile.toPath()
        val file = File(path).canonicalFile
        return if (file.isFile && file.toPath().startsWith(root)) file.inputStream() else null
    }

    private fun openRemote(reference: String): InputStream? {
        val connection = URL(reference).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299 || connection.contentLengthLong > 100L * 1024 * 1024) {
            connection.disconnect()
            return null
        }
        return object : FilterInputStream(connection.inputStream) {
            override fun close() {
                super.close()
                connection.disconnect()
            }
        }
    }

    private fun PublicDraftEntity.portable() = PortablePublicDraft(
        id = id,
        catId = catId,
        publicNickname = publicNickname,
        alias = alias,
        story = story,
        gender = gender,
        ageBand = ageBand,
        coat = coat,
        personalityTags = personalityTags,
        healthSummary = healthSummary,
        weight = weight,
        dietRestrictions = dietRestrictions,
        contactWithHumans = contactWithHumans,
        contactWithCats = contactWithCats,
        catStatus = catStatus,
        statusReason = statusReason,
        archiveReason = archiveReason,
        rescuedDateEpochDay = rescuedDateEpochDay,
        statusDateEpochDay = statusDateEpochDay,
        statusCode = statusCode,
        coverMediaId = coverMediaId,
        photoUrls = photoUrls,
        mediaUploadState = mediaUploadState,
        cityCode = cityCode,
        districtCode = districtCode,
        publishState = publishState,
        serverCatId = serverCatId,
        updatedAt = updatedAt,
    )
}
