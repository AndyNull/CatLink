package com.catlink.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.MediaEntity
import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import com.catlink.app.network.AuthSessionManager
import com.catlink.app.network.SessionStorage
import com.catlink.app.runtime.AppRuntimeStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var engine: SyncEngine
    private lateinit var vault: VaultManager
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        vault = VaultManager(context, db.syncDao())
        val api = AuthApiClient(
            baseUrlProvider = { server.url("/").toString() },
            authSessionProvider = { AuthSessionSnapshot("test-token", 0L) },
            json = json,
        )
        val auth = AuthSessionManager(
            api = api,
            store = object : SessionStorage {
                private var refresh: String? = "rt"
                override fun saveSession(refreshToken: String, accountId: String, email: String, emailVerified: Boolean): Boolean {
                    refresh = refreshToken
                    return true
                }
                override fun refreshToken(): String? = refresh
                override fun accountId(): String? = "a1"
                override fun email(): String? = "e@x.com"
                override fun emailVerified(): Boolean = true
                override fun clear(): Boolean {
                    refresh = null
                    return true
                }
            },
            runtime = AppRuntimeStateStore(),
            json = json,
        )
        engine = SyncEngine(db.vaultDao(), db.syncDao(), vault, api, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun push_encrypts_and_uploads_snapshot() = runBlocking {
        // 服务端：enable + sync（返回空远端）
        server.enqueue(MockResponse().setBody("""{"accepted":true,"created":true}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"cursor":5,"records":[],"acks":[{"recordId":"cat-1","recordType":"cat","revision":1,"status":"accepted"}]}""",
            ),
        )
        assertTrue(engine.enableVault().isSuccess)

        // 本地一只猫
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-1", nickname = "咪咪", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        assertEquals(1, engine.enqueueInitialSnapshot().getOrThrow())

        val report = engine.syncOnce().getOrThrow()
        assertEquals(1, report.pushed)
        // outbox 已清空
        assertEquals(0, db.syncDao().outboxCount())
        // 服务端收到密文（非明文）；第 1 个请求是 enable，第 2 个是 sync
        server.takeRequest()
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("cat-1"))
        assertTrue(!body.contains("咪咪"))
    }

    @Test
    fun pull_applies_remote_cat() = runBlocking {
        // 先 enqueue 一只猫并 push
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-1", nickname = "本地猫", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        server.enqueue(MockResponse().setBody("""{"accepted":true,"created":true}"""))
        server.enqueue(MockResponse().setBody("""{"cursor":1,"records":[],"acks":[{"recordId":"cat-1","recordType":"cat","revision":1,"status":"accepted"}]}"""))
        assertTrue(engine.enableVault().isSuccess)
        engine.enqueueInitialSnapshot()
        engine.syncOnce()

        // 第二次 sync：服务端返回远端新猫（密文）
        val remote = CatEntity(
            id = "cat-2", nickname = "远端猫", status = CatStatus.ADOPTED.code,
            healthStatus = "HEALTHY", rescuedDateEpochDay = 30000,
        )
        val cipher = vault.encryptEntity("cat", "cat-2", json.encodeToString(remote).toByteArray(Charsets.UTF_8))
        val parts = cipher.split(':', limit = 2)
        val remoteRecord = """{"recordId":"cat-2","recordType":"cat","keyVersion":1,"revision":2,"serverRevision":2,"ciphertext":"$cipher","deleted":false}"""
        server.enqueue(MockResponse().setBody("""{"cursor":2,"records":[$remoteRecord]}"""))

        val report = engine.syncOnce().getOrThrow()
        assertEquals(1, report.pulled)
        val applied = db.vaultDao().catById("cat-2")
        assertEquals("远端猫", applied?.nickname)
    }

    @Test
    fun small_outbox_is_capped_at_eleven_for_worst_case_conflicts() = runBlocking {
        repeat(12) { index ->
            db.syncDao().insertOutbox(
                com.catlink.app.data.db.SyncOutboxEntity(
                    id = "small-$index", entityType = "cat", entityId = "small-$index", op = "upsert",
                    payloadJson = "x", payloadHash = "hash-$index", revision = index + 1L,
                )
            )
        }
        fun response(range: IntRange): String {
            val acks = range.joinToString(",") { index ->
                """{"recordId":"small-$index","recordType":"cat","revision":${index + 1},"status":"accepted"}"""
            }
            return """{"cursor":0,"records":[],"acks":[$acks]}"""
        }
        server.enqueue(MockResponse().setBody(response(0..10)))
        server.enqueue(MockResponse().setBody(response(11..11)))

        assertEquals(11, engine.syncOnce().getOrThrow().pushed)
        repeat(11) { index -> db.syncDao().deleteFingerprint("cat", "small-$index") }
        assertEquals(1, engine.syncOnce().getOrThrow().pushed)
        assertEquals(11, json.decodeFromString<VaultSyncRequest>(server.takeRequest().body.readUtf8()).records.size)
        assertEquals(1, json.decodeFromString<VaultSyncRequest>(server.takeRequest().body.readUtf8()).records.size)
    }

    @Test
    fun large_outbox_is_split_by_byte_budget_and_drains() = runBlocking {
        repeat(11) { index ->
            db.syncDao().insertOutbox(
                com.catlink.app.data.db.SyncOutboxEntity(
                    id = "large-$index",
                    entityType = "cat",
                    entityId = "large-$index",
                    op = "upsert",
                    payloadJson = "x".repeat(500 * 1024),
                    payloadHash = "hash-$index",
                    revision = index + 1L,
                )
            )
        }
        fun response(range: IntRange): String {
            val acks = range.joinToString(",") { index ->
                """{"recordId":"large-$index","recordType":"cat","revision":${index + 1},"status":"accepted"}"""
            }
            return """{"cursor":0,"records":[],"acks":[$acks]}"""
        }
        server.enqueue(MockResponse().setBody(response(0..9)))
        server.enqueue(MockResponse().setBody(response(10..10)))

        val first = engine.syncOnce().getOrThrow()
        assertEquals(10, first.pushed)
        assertEquals(1, db.syncDao().outboxCount())
        repeat(10) { index -> db.syncDao().deleteFingerprint("cat", "large-$index") }
        val second = engine.syncOnce().getOrThrow()
        assertEquals(1, second.pushed)
        assertEquals(0, db.syncDao().outboxCount())
        assertTrue(server.takeRequest().bodySize < 8L * 1024 * 1024)
        assertTrue(server.takeRequest().bodySize < 8L * 1024 * 1024)
    }

    @Test
    fun conflict_record_beyond_page_does_not_advance_cursor() = runBlocking {
        db.vaultDao().upsertCat(
            CatEntity(
                id = "local-conflict", nickname = "本地冲突", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        engine.captureLocalChanges().getOrThrow()
        val pending = db.syncDao().pendingOutbox(1).single()
        val conflictCipher = vault.encryptEntity(
            "cat", "local-conflict",
            json.encodeToString(db.vaultDao().catById("local-conflict")!!.copy(nickname = "远端版本")).toByteArray(Charsets.UTF_8),
        )
        val records = buildList {
            repeat(100) { index ->
                val remote = CatEntity(
                    id = "remote-$index", nickname = "远端$index", status = CatStatus.RESCUE.code,
                    healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
                )
                val cipher = vault.encryptEntity("cat", remote.id, json.encodeToString(remote).toByteArray(Charsets.UTF_8))
                add(VaultSyncRecord(remote.id, "cat", 1, index + 1L, index + 1L, cipher))
            }
            add(VaultSyncRecord("local-conflict", "cat", 1, pending.revision + 200, 201, conflictCipher))
        }
        server.enqueue(
            MockResponse().setBody(
                json.encodeToString(
                    VaultSyncResponse(
                        cursor = 100,
                        records = records,
                        acks = listOf(VaultSyncAck("local-conflict", "cat", pending.revision, "conflict")),
                    )
                )
            )
        )

        val report = engine.syncOnce().getOrThrow()
        assertEquals(100L, report.cursor)
        assertEquals(100L, db.syncDao().revision("global:cursor")?.lastLocalRevision)
        assertEquals(1, report.conflicts)
    }

    @Test
    fun failed_remote_apply_does_not_advance_cursor() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"cursor":9,"records":[{"recordId":"bad","recordType":"cat","keyVersion":1,"revision":9,"serverRevision":9,"ciphertext":"not-valid-ciphertext","deleted":false}],"acks":[]}"""
            )
        )
        assertTrue(engine.syncOnce().isFailure)
        assertEquals(null, db.syncDao().revision("global:cursor"))
    }

    @Test
    fun pulled_revision_advances_local_revision_counter() = runBlocking {
        val remote = CatEntity(
            id = "cat-high", nickname = "高版本", status = CatStatus.RESCUE.code,
            healthStatus = "HEALTHY", rescuedDateEpochDay = 30000,
        )
        val cipher = vault.encryptEntity("cat", "cat-high", json.encodeToString(remote).toByteArray(Charsets.UTF_8))
        server.enqueue(MockResponse().setBody("""{"cursor":50,"records":[{"recordId":"cat-high","recordType":"cat","keyVersion":1,"revision":50,"serverRevision":50,"ciphertext":"$cipher","deleted":false}],"acks":[]}"""))
        assertTrue(engine.syncOnce().isSuccess)

        db.vaultDao().upsertCat(remote.copy(nickname = "本地修改"))
        assertEquals(1, engine.captureLocalChanges().getOrThrow())
        assertEquals(51, db.syncDao().pendingOutbox(1).single().revision)
    }

    @Test
    fun local_changes_are_captured_once_and_deletes_become_tombstones() = runBlocking {
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-diff", nickname = "差异猫", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        assertEquals(1, engine.captureLocalChanges().getOrThrow())
        assertEquals(0, engine.captureLocalChanges().getOrThrow())
        assertEquals(1, db.syncDao().outboxCount())

        val pending = db.syncDao().pendingOutbox(1).single()
        db.syncDao().upsertFingerprint(
            com.catlink.app.data.db.SyncFingerprintEntity("cat", "cat-diff", pending.payloadHash, pending.revision)
        )
        db.syncDao().deleteOutbox(pending.id)
        db.vaultDao().deleteCat("cat-diff")

        assertEquals(1, engine.captureLocalChanges().getOrThrow())
        val tombstone = db.syncDao().pendingOutbox(1).single()
        assertEquals("delete", tombstone.op)
        assertEquals("cat-diff", tombstone.entityId)
    }

    @Test
    fun identical_same_revision_echo_is_acknowledged_without_conflict() = runBlocking {
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-echo", nickname = "回显猫", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        server.enqueue(MockResponse().setBody("""{"accepted":true,"created":true}"""))
        assertTrue(engine.enableVault().isSuccess)
        engine.enqueueInitialSnapshot()
        val pending = db.syncDao().pendingOutbox(1).single()
        server.enqueue(
            MockResponse().setBody(
                json.encodeToString(
                    VaultSyncResponse(
                        cursor = 1,
                        records = listOf(
                            VaultSyncRecord(
                                recordId = pending.entityId,
                                recordType = pending.entityType,
                                keyVersion = 1,
                                revision = pending.revision,
                                ciphertext = pending.payloadJson,
                                deleted = false,
                            )
                        ),
                    )
                )
            )
        )

        val report = engine.syncOnce().getOrThrow()
        assertEquals(0, report.conflicts)
        assertEquals(0, db.syncDao().outboxCount())
        assertEquals(0, db.syncDao().pendingConflictCount())
    }

    @Test
    fun missing_ack_does_not_drop_outbox() = runBlocking {
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-unacked", nickname = "待确认", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        server.enqueue(MockResponse().setBody("""{"accepted":true,"created":true}"""))
        assertTrue(engine.enableVault().isSuccess)
        engine.enqueueInitialSnapshot()
        server.enqueue(MockResponse().setBody("""{"cursor":1,"records":[],"acks":[]}"""))

        val report = engine.syncOnce().getOrThrow()
        assertEquals(0, report.pushed)
        assertEquals(1, db.syncDao().outboxCount())
    }

    @Test
    fun conflict_is_recorded_when_local_pending_is_newer() = runBlocking {
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-1", nickname = "本地新", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        server.enqueue(MockResponse().setBody("""{"accepted":true,"created":true}"""))
        server.enqueue(MockResponse().setBody("""{"cursor":1,"records":[],"acks":[{"recordId":"cat-1","recordType":"cat","revision":1,"status":"accepted"}]}"""))
        assertTrue(engine.enableVault().isSuccess)
        engine.enqueueInitialSnapshot() // r1 入 outbox
        engine.syncOnce()               // 推送 r1，outbox 清空

        // 本地再次修改（r2 pending）
        db.vaultDao().upsertCat(
            CatEntity(
                id = "cat-1", nickname = "本地新2", status = CatStatus.RESCUE.code,
                healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
            )
        )
        engine.enqueue("cat", "cat-1", "本地新2-明文")
        // 服务端返回旧的 r1 内容（本地 pending r2 更高 → 冲突，本地胜）
        val remoteOld = CatEntity(
            id = "cat-1", nickname = "远端旧", status = CatStatus.RESCUE.code,
            healthStatus = "HEALTHY", rescuedDateEpochDay = 20000,
        )
        val cipher = vault.encryptEntity("cat", "cat-1", json.encodeToString(remoteOld).toByteArray(Charsets.UTF_8))
        server.enqueue(MockResponse().setBody("""{"cursor":1,"records":[{"recordId":"cat-1","recordType":"cat","keyVersion":1,"revision":1,"serverRevision":1,"ciphertext":"$cipher","deleted":false}],"acks":[{"recordId":"cat-1","recordType":"cat","revision":2,"status":"conflict"}]}"""))
        val report = engine.syncOnce().getOrThrow()
        // 本地 pending revision(2) > 远端(1) → 记冲突，本地内容保留
        assertEquals(1, report.conflicts)
        assertEquals("本地新2", db.vaultDao().catById("cat-1")?.nickname)
        assertEquals(1, db.syncDao().pendingConflictCount())
        // outbox 保留（等待人工解决）
        assertEquals(1, db.syncDao().outboxCount())
    }

    @Test
    fun media_sync_excludes_local_only_paths_and_strips_device_uri() = runBlocking {
        db.vaultDao().upsertMedia(
            MediaEntity(
                id = "local-media",
                catId = "cat",
                uri = "/data/user/0/app/files/photos/local.jpg",
                localUri = "/data/user/0/app/files/photos/local.jpg",
                syncState = AlbumSyncState.LOCAL.value,
            )
        )
        db.vaultDao().upsertMedia(
            MediaEntity(
                id = "remote-media",
                catId = "cat",
                uri = "https://api.example/uploads/cat/remote.jpg",
                localUri = "/data/user/0/app/files/photos/private.jpg",
                remoteUrl = "https://api.example/uploads/cat/remote.jpg",
                serverMediaId = "88",
                syncState = AlbumSyncState.UPLOADED.value,
            )
        )
        db.syncDao().insertOutbox(
            com.catlink.app.data.db.SyncOutboxEntity(
                id = "leaked-local",
                entityType = "media",
                entityId = "local-media",
                op = "upsert",
                payloadJson = "old-device-path",
                payloadHash = "old",
                revision = 1,
            )
        )

        assertEquals(1, engine.captureLocalChanges().getOrThrow())
        val pending = db.syncDao().pendingOutbox(10).single()
        val plain = vault.decryptEntity("media", "remote-media", pending.payloadJson)
        val synced = json.decodeFromString(MediaEntity.serializer(), String(plain, Charsets.UTF_8))

        assertEquals("remote-media", pending.entityId)
        assertEquals("https://api.example/uploads/cat/remote.jpg", synced.uri)
        assertEquals("", synced.localUri)
        assertFalse(pending.payloadJson.contains("/data/user/0"))
    }
}
