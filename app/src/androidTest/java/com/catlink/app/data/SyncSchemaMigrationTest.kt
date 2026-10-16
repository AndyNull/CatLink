package com.catlink.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.PublicDraftEntity
import com.catlink.app.data.db.SyncBlobEntity
import com.catlink.app.data.db.SyncConflictEntity
import com.catlink.app.data.db.SyncOutboxEntity
import com.catlink.app.data.db.SyncRevisionEntity
import com.catlink.app.data.db.VaultKeyEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSchemaMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase("migration-test.db")
        context.deleteDatabase("migration-v12-test.db")
        context.deleteDatabase("migration-v13-test.db")
        context.deleteDatabase("sync-dao-test.db")
    }

    private fun createV4Tables(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cats (" +
                "id TEXT NOT NULL PRIMARY KEY, nickname TEXT NOT NULL, alias TEXT NOT NULL DEFAULT '', " +
                "status TEXT NOT NULL, healthStatus TEXT NOT NULL, gender TEXT NOT NULL DEFAULT '', " +
                "ageEstimate TEXT NOT NULL DEFAULT '', coat TEXT NOT NULL DEFAULT '', weight TEXT NOT NULL DEFAULT '', " +
                "personality TEXT NOT NULL DEFAULT '', dietRestrictions TEXT NOT NULL DEFAULT '', " +
                "contactWithHumans TEXT NOT NULL DEFAULT '', contactWithCats TEXT NOT NULL DEFAULT '', " +
                "statusDateEpochDay INTEGER, statusReason TEXT NOT NULL DEFAULT '', story TEXT NOT NULL DEFAULT '', " +
                "rescuedDateEpochDay INTEGER NOT NULL, tags TEXT NOT NULL DEFAULT '', coverColorIndex INTEGER NOT NULL DEFAULT 0, " +
                "coverUri TEXT NOT NULL DEFAULT '', deletedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS media (" +
                "id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, uri TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS contracts (" +
                "id TEXT NOT NULL PRIMARY KEY, caseId TEXT NOT NULL, contractDateEpochDay INTEGER NOT NULL, " +
                "content TEXT NOT NULL DEFAULT '', signedAt INTEGER, signaturePhotoUri TEXT NOT NULL DEFAULT '', " +
                "handoverPhotoUri TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS medical_records (" +
                "id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, " +
                "category TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', " +
                "hospital TEXT NOT NULL DEFAULT '', cost TEXT NOT NULL DEFAULT '', " +
                "nextReminderEpochDay INTEGER, createdAt INTEGER NOT NULL)"
        )
    }

    private fun createV12PublicDrafts(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS public_drafts (" +
                "id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, publicNickname TEXT NOT NULL, " +
                "story TEXT NOT NULL, gender TEXT NOT NULL, ageBand TEXT NOT NULL, coat TEXT NOT NULL, " +
                "personalityTags TEXT NOT NULL, healthSummary TEXT NOT NULL, statusCode TEXT NOT NULL, " +
                "coverMediaId TEXT, publishState TEXT NOT NULL, serverCatId TEXT, updatedAt INTEGER NOT NULL, " +
                "cityCode TEXT, districtCode TEXT)"
        )
    }

    @Test
    fun v4_to_v7_migration_keeps_old_data_and_creates_sync_tables() {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    createV4Tables(db)
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = factory.create(config)
        val v4 = helper.writableDatabase
        createV4Tables(v4)
        v4.execSQL(
            "INSERT INTO cats (id, nickname, status, healthStatus, rescuedDateEpochDay, createdAt, updatedAt) " +
                "VALUES ('cat-old-1', '老猫', 'RESCUING', 'HEALTHY', 20000, 1000, 1000)"
        )
        v4.execSQL(
            "INSERT INTO media (id, catId, uri, createdAt) VALUES ('media-old-1', 'cat-old-1', 'file:///old.jpg', 1000)"
        )

        // 执行完整迁移链（Room 全链路由真实旧库升级覆盖）
        AppDatabase.MIGRATION_4_5.migrate(v4)
        AppDatabase.MIGRATION_5_6.migrate(v4)
        AppDatabase.MIGRATION_6_7.migrate(v4)
        AppDatabase.MIGRATION_7_8.migrate(v4)
        AppDatabase.MIGRATION_8_9.migrate(v4)

        // 旧数据保留
        var catCount = 0
        var catName = ""
        v4.query("SELECT id, nickname FROM cats").use { c ->
            while (c.moveToNext()) {
                catCount++
                catName = c.getString(1)
            }
        }
        assertEquals(1, catCount)
        assertEquals("老猫", catName)

        // 新表全部存在
        val tables = mutableSetOf<String>()
        v4.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        listOf(
            "sync_outbox", "sync_revisions", "sync_conflicts",
            "vault_keys", "sync_blobs", "public_drafts",
            "cat_versions", "cat_relations",
        ).forEach { assertTrue("missing $it", tables.contains(it)) }

        // cat_versions 列完整
        val versionCols = mutableSetOf<String>()
        v4.query("PRAGMA table_info(cat_versions)").use { c ->
            while (c.moveToNext()) versionCols.add(c.getString(1))
        }
        listOf("id", "catId", "versionNo", "snapshotJson", "changeSummary", "reason", "createdAt")
            .forEach { assertTrue("missing col $it", versionCols.contains(it)) }

        // cat_relations 列完整
        val relationCols = mutableSetOf<String>()
        v4.query("PRAGMA table_info(cat_relations)").use { c ->
            while (c.moveToNext()) relationCols.add(c.getString(1))
        }
        listOf("id", "catId", "relatedCatId", "relationType", "note", "createdAt")
            .forEach { assertTrue("missing col $it", relationCols.contains(it)) }

        // medical_records 新列
        val medicalCols = mutableSetOf<String>()
        v4.query("PRAGMA table_info(medical_records)").use { c ->
            while (c.moveToNext()) medicalCols.add(c.getString(1))
        }
        listOf("weight", "behaviorNote", "result")
            .forEach { assertTrue("missing col $it", medicalCols.contains(it)) }

        // vault_keys 恢复码包装列存在
        val vaultCols = mutableSetOf<String>()
        v4.query("PRAGMA table_info(vault_keys)").use { c ->
            while (c.moveToNext()) vaultCols.add(c.getString(1))
        }
        assertTrue("missing wrappedDekRecovery", vaultCols.contains("wrappedDekRecovery"))

        // 幂等：4→5 的 CREATE TABLE 可重复执行；5→6 的 ALTER 由 Room 版本管理保证只执行一次
        AppDatabase.MIGRATION_4_5.migrate(v4)
        v4.close()
        helper.close()
    }

    @Test
    fun v5_tables_match_room_expected_schema() {
        // Room 期望 schema（in-memory 自动建表）
        val expectedDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val expected = expectedDb.openHelper.writableDatabase
        val tables = listOf(
            "sync_outbox", "sync_revisions", "sync_conflicts",
            "vault_keys", "sync_blobs", "public_drafts",
        )
        val expectedInfos = tables.associateWith { androidx.room.util.TableInfo.read(expected, it) }
        expectedDb.close()

        // 迁移 SQL 建出的 schema
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    createV4Tables(db)
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = factory.create(config)
        val migrated = helper.writableDatabase
        AppDatabase.MIGRATION_4_5.migrate(migrated)
        AppDatabase.MIGRATION_5_6.migrate(migrated)
        AppDatabase.MIGRATION_6_7.migrate(migrated)
        AppDatabase.MIGRATION_7_8.migrate(migrated)
        AppDatabase.MIGRATION_8_9.migrate(migrated)
        AppDatabase.MIGRATION_9_10.migrate(migrated)
        AppDatabase.MIGRATION_10_11.migrate(migrated)
        AppDatabase.MIGRATION_11_12.migrate(migrated)
        AppDatabase.MIGRATION_12_13.migrate(migrated)
        AppDatabase.MIGRATION_13_14.migrate(migrated)
        AppDatabase.MIGRATION_14_15.migrate(migrated)
        AppDatabase.MIGRATION_15_16.migrate(migrated)
        AppDatabase.MIGRATION_16_17.migrate(migrated)

        tables.forEach { table ->
            val actual = androidx.room.util.TableInfo.read(migrated, table)
            val expect = expectedInfos.getValue(table)
            assertEquals("schema mismatch for $table", expect, actual)
        }
        migrated.close()
        helper.close()
    }

    @Test
    fun v11_to_v12_creates_sync_fingerprint_schema() {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-v12-test.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_11_12.migrate(db)
        val info = androidx.room.util.TableInfo.read(db, "sync_fingerprints")
        assertTrue((info.columns["entityType"]?.primaryKeyPosition ?: 0) > 0)
        assertTrue((info.columns["entityId"]?.primaryKeyPosition ?: 0) > 0)
        assertTrue(info.columns.containsKey("payloadHash"))
        assertTrue(info.columns.containsKey("revision"))
        db.close()
        helper.close()
    }

    @Test
    fun v12_to_v13_adds_public_cat_profile_fields() {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-v13-test.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV12PublicDrafts(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        createV12PublicDrafts(db)
        db.execSQL(
            "INSERT INTO public_drafts VALUES (" +
                "'draft-1', 'cat-1', '旧草稿', '故事', '妹妹', '1岁', '橘白', '亲人', '健康', " +
                "'adoptable', NULL, 'draft', NULL, 1234, '成都市', '武侯区')"
        )
        AppDatabase.MIGRATION_12_13.migrate(db)
        val columns = androidx.room.util.TableInfo.read(db, "public_drafts").columns
        listOf(
            "alias", "weight", "dietRestrictions", "contactWithHumans", "contactWithCats",
            "catStatus", "statusReason", "rescuedDateEpochDay", "statusDateEpochDay",
        ).forEach { assertTrue("missing $it", columns.containsKey(it)) }
        db.query("SELECT publicNickname, alias, rescuedDateEpochDay FROM public_drafts WHERE id='draft-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧草稿", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(0L, cursor.getLong(2))
        }
        db.close()
        helper.close()
    }

    @Test
    fun sync_dao_conflict_lifecycle() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val sync = db.syncDao()
            sync.insertConflict(
                SyncConflictEntity(
                    id = "c1", entityType = "todo", entityId = "t1",
                    localPayloadJson = "l1", remotePayloadJson = "r1", localHash = "lh", remoteHash = "rh",
                    status = "pending",
                )
            )
            sync.insertConflict(
                SyncConflictEntity(
                    id = "c2", entityType = "todo", entityId = "t2",
                    localPayloadJson = "l2", remotePayloadJson = "r2", localHash = "lh", remoteHash = "rh",
                    status = "pending",
                )
            )
            assertEquals(2, sync.pendingConflictCount())
            sync.resolveConflict("c1", "resolved_local", 2000)
            assertEquals(1, sync.pendingConflictCount())
            sync.deleteConflict("c2")
            // c1 已解决但记录保留在表中
            assertEquals(1, sync.conflictCount())
            assertEquals(0, sync.pendingConflictCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun outbox_dedupes_by_entity_via_replace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val sync = db.syncDao()
            // 同一实体的新变更覆盖旧变更（REPLACE 按主键 id，但这里验证 deleteByEntity 清理）
            sync.insertOutbox(
                SyncOutboxEntity(
                    id = "a", entityType = "cat", entityId = "c1", op = "upsert",
                    payloadJson = "v1", payloadHash = "h1", revision = 1,
                )
            )
            sync.insertOutbox(
                SyncOutboxEntity(
                    id = "b", entityType = "cat", entityId = "c1", op = "upsert",
                    payloadJson = "v2", payloadHash = "h2", revision = 2,
                )
            )
            assertEquals(2, sync.outboxCount())
            sync.deleteOutboxByEntity("cat", "c1")
            assertEquals(0, sync.outboxCount())
        } finally {
            db.close()
        }
    }
}
