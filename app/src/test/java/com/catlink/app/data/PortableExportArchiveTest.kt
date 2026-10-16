package com.catlink.app.data

import com.catlink.app.data.db.AdopterEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.MediaEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class PortableExportArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `完整归档包含全部结构并通过校验`() {
        val mediaBytes = "猫咪照片内容".toByteArray()
        val output = ByteArrayOutputStream()
        val result = PortableExportArchive.write(
            output,
            request(
                data = sampleData(),
                mediaSources = listOf(
                    ArchiveMediaSource("/private/猫咪.jpg", "media/abc_猫咪.jpg") { ByteArrayInputStream(mediaBytes) },
                    ArchiveMediaSource("https://example.com/missing.jpg", "media/missing.jpg") { error("gone") },
                ),
            ),
        )
        val entries = unzip(output.toByteArray())
        val required = setOf(
            "manifest.json",
            "README.txt",
            "checksums.sha256",
            "data/personal-data.json",
            "data/server-account-data.json",
            "summary/index.html",
            "media/abc_猫咪.jpg",
            "tables/account.csv",
            "tables/cats.csv",
            "tables/medical-records.csv",
            "tables/cat-versions.csv",
            "tables/cat-relations.csv",
            "tables/adopters.csv",
            "tables/adoption-cases.csv",
            "tables/contracts.csv",
            "tables/follow-ups.csv",
            "tables/timeline-events.csv",
            "tables/todos.csv",
            "tables/case-notes.csv",
            "tables/media-records.csv",
            "tables/public-drafts.csv",
            "tables/ai-sessions.csv",
            "tables/ai-messages.csv",
            "tables/preferences.csv",
            "tables/media-files.csv",
        )
        assertTrue(entries.keys.containsAll(required))
        assertFalse("media/missing.jpg" in entries)
        assertEquals(1, result.mediaCount)
        assertEquals(1, result.missingMediaCount)
        assertEquals(mediaBytes.size.toLong(), result.mediaBytes)

        val dataJson = entries.getValue("data/personal-data.json").toString(Charsets.UTF_8)
        assertTrue("猫咪一号" in dataJson)
        assertTrue("13800000000" in dataJson)
        assertTrue("rescuer@example.com" in dataJson)
        assertTrue("我的 AI 问题" in dataJson)
        assertTrue("\"included\": false" in dataJson)
        assertFalse("apiKey" in dataJson)
        assertFalse("refreshToken" in dataJson)
        assertTrue("服务端帖子" in entries.getValue("data/server-account-data.json").toString(Charsets.UTF_8))

        val html = entries.getValue("summary/index.html").toString(Charsets.UTF_8)
        assertTrue("&lt;猫&amp;咪&gt;" in html)
        assertFalse("<猫&咪>" in html)
        val adopterCsv = entries.getValue("tables/adopters.csv").toString(Charsets.UTF_8)
        assertTrue("'=2+2" in adopterCsv)
        assertTrue(adopterCsv.startsWith("\uFEFF"))

        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
        assertEquals("youmaoni-personal-data", manifest.getValue("format").jsonPrimitive.content)
        assertEquals(1, manifest.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals(18, manifest.getValue("domains").jsonArray.size)
        assertEquals("data/server-account-data.json", manifest.getValue("serverDataFile").jsonPrimitive.content)
        manifest.getValue("files").jsonArray.forEach { item ->
            val record = item.jsonObject
            val path = record.getValue("path").jsonPrimitive.content
            assertEquals(record.getValue("sizeBytes").jsonPrimitive.content.toLong(), entries.getValue(path).size.toLong())
            assertEquals(record.getValue("sha256").jsonPrimitive.content, sha256(entries.getValue(path)))
        }
        val checksums = entries.getValue("checksums.sha256").toString(Charsets.UTF_8)
        assertTrue("${sha256(mediaBytes)}  media/abc_猫咪.jpg" in checksums)
        assertFalse("manifest.json" in checksums)
    }

    @Test
    fun `空数据归档仍有完整表头`() {
        val output = ByteArrayOutputStream()
        val result = PortableExportArchive.write(output, request(data = emptyData(), serverAccountData = null))
        val entries = unzip(output.toByteArray())
        assertEquals(0, result.mediaCount)
        assertEquals(0, result.missingMediaCount)
        assertEquals(18, entries.keys.count { it.startsWith("tables/") })
        assertFalse("data/server-account-data.json" in entries)
        assertEquals(1, entries.getValue("tables/cats.csv").toString(Charsets.UTF_8).lineSequence().count(String::isNotBlank))
        assertTrue("暂无猫咪档案" in entries.getValue("summary/index.html").toString(Charsets.UTF_8))
        val data = Json.parseToJsonElement(entries.getValue("data/personal-data.json").toString(Charsets.UTF_8)).jsonObject
            .getValue("data").jsonObject
        assertTrue(data.getValue("cats").jsonArray.isEmpty())
        assertTrue(data.getValue("mediaFiles").jsonArray.isEmpty())
    }

    @Test
    fun `大媒体使用有界缓冲流式写入`() {
        val size = 12 * 1024 * 1024
        val source = GeneratingInputStream(size)
        val archive = temporaryFolder.newFile("large-export.zip")
        FileOutputStream(archive).use { output ->
            val result = PortableExportArchive.write(
                output,
                request(
                    data = emptyData(),
                    mediaSources = listOf(ArchiveMediaSource("large.bin", "media/large.bin") { source }),
                ),
            )
            assertEquals(size.toLong(), result.mediaBytes)
        }
        var extracted = 0L
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "media/large.bin") {
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        extracted += count
                    }
                }
            }
        }
        assertEquals(size.toLong(), extracted)
        assertTrue(source.maxRequestedBytes <= 64 * 1024)
    }

    @Test
    fun `大规模结构化数据逐行写入有效归档`() {
        val count = 20_000
        val cats = List(count) { index ->
            CatEntity(
                id = "cat-$index",
                nickname = "猫咪-$index",
                alias = "记录-${"温柔".repeat(64)}-$index",
                status = "INTAKE",
                healthStatus = "HEALTHY",
                rescuedDateEpochDay = 20_000,
            )
        }
        val archive = temporaryFolder.newFile("large-structured-export.zip")
        FileOutputStream(archive).use { output ->
            PortableExportArchive.write(output, request(data = emptyData().copy(cats = cats)))
        }

        var rows = 0
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "tables/cats.csv") {
                    rows = zip.bufferedReader().lineSequence().count() - 1
                    break
                }
            }
        }
        assertEquals(count, rows)
    }

    @Test
    fun `拒绝越界媒体路径`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableExportArchive.write(
                ByteArrayOutputStream(),
                request(
                    data = emptyData(),
                    mediaSources = listOf(ArchiveMediaSource("x", "../outside.bin") { ByteArrayInputStream(byteArrayOf(1)) }),
                ),
            )
        }
    }

    @Test
    fun `AI 接口导出移除嵌入凭据`() {
        assertEquals(
            "https://api.example.com:8443",
            ExportManager.endpointOrigin("https://user:password@api.example.com:8443/v1?api_key=secret#token"),
        )
    }

    private fun sampleData() = PortablePersonalData(
        account = PortableExportAccount("rescuer@example.com", true),
        cats = listOf(
            CatEntity(
                id = "cat-1",
                nickname = "<猫&咪>",
                alias = "猫咪一号",
                status = "INTAKE",
                healthStatus = "HEALTHY",
                rescuedDateEpochDay = 20_000,
                cityName = "成都市",
                districtName = "武侯区",
            ),
        ),
        adopters = listOf(AdopterEntity(id = "adopter-1", name = "=2+2", phone = "13800000000")),
        mediaRecords = listOf(MediaEntity("media-1", "cat-1", "/private/猫咪.jpg")),
        publicDrafts = listOf(
            PortablePublicDraft(
                id = "cat-1",
                catId = "cat-1",
                publicNickname = "猫咪一号",
                alias = "",
                story = "公开故事",
                gender = "母",
                ageBand = "成年",
                coat = "橘色",
                personalityTags = "亲人",
                healthSummary = "健康",
                weight = "3.2kg",
                dietRestrictions = "",
                contactWithHumans = "亲人",
                contactWithCats = "友好",
                catStatus = "救助中",
                statusReason = "",
                archiveReason = "",
                rescuedDateEpochDay = 20_000,
                statusDateEpochDay = null,
                statusCode = "rescue",
                coverMediaId = null,
                photoUrls = "",
                mediaUploadState = null,
                cityCode = "成都市",
                districtCode = "武侯区",
                publishState = "draft",
                serverCatId = null,
                updatedAt = 1_700_000_000_000,
            ),
        ),
        aiSessions = listOf(
            PortableAiSession(
                id = "ai-1",
                title = "养护咨询",
                messages = listOf(PortableAiMessage("user", "我的 AI 问题")),
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_001,
                summary = "",
                summarizedCount = 0,
            ),
        ),
        preferences = preferences(),
    )

    private fun emptyData() = PortablePersonalData(preferences = preferences())

    private fun preferences() = PortableExportPreferences(
        theme = "WHITE",
        localNickname = "救助人",
        aiEndpoint = "https://api.example.com",
        aiModel = "model",
        dailyEncouragementDate = "2026-08-22",
        dailyEncouragementText = "今天也要温柔",
        dailyEncouragementSource = "local",
    )

    private fun request(
        data: PortablePersonalData,
        mediaSources: List<ArchiveMediaSource> = emptyList(),
        serverAccountData: kotlinx.serialization.json.JsonElement? = buildJsonObject { put("post", "服务端帖子") },
    ) = PortableExportRequest(
        appVersion = "1.0.0",
        exportedAtMillis = 1_700_000_000_000,
        data = data,
        mediaSources = mediaSources,
        serverAccountData = serverAccountData,
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class GeneratingInputStream(size: Int) : InputStream() {
        private var remaining = size
        var maxRequestedBytes = 0
            private set

        override fun read(): Int = if (remaining-- > 0) 0x5a else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) return -1
            maxRequestedBytes = maxOf(maxRequestedBytes, length)
            val count = minOf(remaining, length)
            java.util.Arrays.fill(buffer, offset, offset + count, 0x5a.toByte())
            remaining -= count
            return count
        }
    }
}
