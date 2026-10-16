package com.catlink.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CatEntity::class,
        MedicalEntity::class,
        AdopterEntity::class,
        CaseEntity::class,
        ContractEntity::class,
        FollowUpEntity::class,
        TimelineEntity::class,
        TodoEntity::class,
        CaseNoteEntity::class,
        MediaEntity::class,
        SyncOutboxEntity::class,
        SyncFingerprintEntity::class,
        SyncRevisionEntity::class,
        SyncConflictEntity::class,
        VaultKeyEntity::class,
        SyncBlobEntity::class,
        PublicDraftEntity::class,
        CatVersionEntity::class,
        CatRelationEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun syncDao(): SyncDao

    companion object {
        /** v1 → v2：领养人扩展联系方式字段、合同/交接现场照片、猫咪封面 URI */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cats ADD COLUMN coverUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN wechat TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN qq TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN idCard TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adoption_cases ADD COLUMN handoverPhotoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contracts ADD COLUMN signaturePhotoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contracts ADD COLUMN handoverPhotoUri TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3：猫咪状态日期/原因与相处字段、领养人评估字段、个案拒绝/撤回原因、沟通记录表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cats ADD COLUMN dietRestrictions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cats ADD COLUMN contactWithHumans TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cats ADD COLUMN contactWithCats TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cats ADD COLUMN statusDateEpochDay INTEGER")
                db.execSQL("ALTER TABLE cats ADD COLUMN statusReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN backupContact TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN preferredContact TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN residence TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN petExperience TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN familyMembers TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adopters ADD COLUMN contactAuthorized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE adopters ADD COLUMN authorizedAt INTEGER")
                db.execSQL("ALTER TABLE adoption_cases ADD COLUMN rejectReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE adoption_cases ADD COLUMN withdrawReason TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS case_notes (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "caseId TEXT NOT NULL, " +
                        "noteDateEpochDay INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "content TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS media (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "catId TEXT NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        /** v3 → v4：回访记录猫咪状态 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN statusAtVisit TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 → v5：E2EE 同步元数据表与公开草稿（纯新增，非破坏；DEFAULT 由 Kotlin 实体层提供） */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_outbox (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "entityType TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, " +
                        "op TEXT NOT NULL, " +
                        "payloadJson TEXT NOT NULL, " +
                        "payloadHash TEXT NOT NULL, " +
                        "revision INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "attemptCount INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_revisions (" +
                        "entityId TEXT NOT NULL PRIMARY KEY, " +
                        "lastLocalRevision INTEGER NOT NULL, " +
                        "lastSyncedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_conflicts (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "entityType TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, " +
                        "localPayloadJson TEXT NOT NULL, " +
                        "remotePayloadJson TEXT NOT NULL, " +
                        "localHash TEXT NOT NULL, " +
                        "remoteHash TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "resolvedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS vault_keys (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "kekVersion INTEGER NOT NULL, " +
                        "wrappedDek TEXT NOT NULL, " +
                        "dekSalt TEXT NOT NULL, " +
                        "kekIterations INTEGER NOT NULL, " +
                        "recoveryCodeHash TEXT NOT NULL, " +
                        "recoveryCodeSalt TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_blobs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "logicalPath TEXT NOT NULL, " +
                        "sizeBytes INTEGER NOT NULL, " +
                        "sha256 TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS public_drafts (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "catId TEXT NOT NULL, " +
                        "publicNickname TEXT NOT NULL, " +
                        "story TEXT NOT NULL, " +
                        "gender TEXT NOT NULL, " +
                        "ageBand TEXT NOT NULL, " +
                        "coat TEXT NOT NULL, " +
                        "personalityTags TEXT NOT NULL, " +
                        "healthSummary TEXT NOT NULL, " +
                        "statusCode TEXT NOT NULL, " +
                        "coverMediaId TEXT, " +
                        "publishState TEXT NOT NULL, " +
                        "serverCatId TEXT, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        /** v5 → v6：vault_keys 增加恢复码二次包装 DEK 列 */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_keys ADD COLUMN wrappedDekRecovery TEXT")
            }
        }

        /** v6 → v7：public_drafts 增加城市/区县列（附近推荐用） */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE public_drafts ADD COLUMN cityCode TEXT")
                db.execSQL("ALTER TABLE public_drafts ADD COLUMN districtCode TEXT")
            }
        }

        /** v7 → v8：合同支持多张签署/交接照片（逗号分隔 URI） */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contracts ADD COLUMN photoUris TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v11 → v12：持久化实体同步 fingerprint，用于完整差异捕获与删除 tombstone。 */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_fingerprints (" +
                        "entityType TEXT NOT NULL, " +
                        "entityId TEXT NOT NULL, " +
                        "payloadHash TEXT NOT NULL, " +
                        "revision INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(entityType, entityId))"
                )
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE public_drafts_v13 (" +
                        "id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, publicNickname TEXT NOT NULL, " +
                        "alias TEXT NOT NULL, story TEXT NOT NULL, gender TEXT NOT NULL, ageBand TEXT NOT NULL, " +
                        "coat TEXT NOT NULL, personalityTags TEXT NOT NULL, healthSummary TEXT NOT NULL, " +
                        "weight TEXT NOT NULL, dietRestrictions TEXT NOT NULL, contactWithHumans TEXT NOT NULL, " +
                        "contactWithCats TEXT NOT NULL, catStatus TEXT NOT NULL, statusReason TEXT NOT NULL, " +
                        "rescuedDateEpochDay INTEGER NOT NULL, statusDateEpochDay INTEGER, statusCode TEXT NOT NULL, " +
                        "coverMediaId TEXT, cityCode TEXT, districtCode TEXT, publishState TEXT NOT NULL, " +
                        "serverCatId TEXT, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO public_drafts_v13 (" +
                        "id, catId, publicNickname, alias, story, gender, ageBand, coat, personalityTags, healthSummary, " +
                        "weight, dietRestrictions, contactWithHumans, contactWithCats, catStatus, statusReason, " +
                        "rescuedDateEpochDay, statusDateEpochDay, statusCode, coverMediaId, cityCode, districtCode, " +
                        "publishState, serverCatId, updatedAt) " +
                        "SELECT id, catId, publicNickname, '', story, gender, ageBand, coat, personalityTags, healthSummary, " +
                        "'', '', '', '', '', '', 0, NULL, statusCode, coverMediaId, cityCode, districtCode, " +
                        "publishState, serverCatId, updatedAt FROM public_drafts"
                )
                db.execSQL("DROP TABLE public_drafts")
                db.execSQL("ALTER TABLE public_drafts_v13 RENAME TO public_drafts")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE public_drafts_v14 (" +
                        "id TEXT NOT NULL PRIMARY KEY, catId TEXT NOT NULL, publicNickname TEXT NOT NULL, " +
                        "alias TEXT NOT NULL, story TEXT NOT NULL, gender TEXT NOT NULL, ageBand TEXT NOT NULL, " +
                        "coat TEXT NOT NULL, personalityTags TEXT NOT NULL, healthSummary TEXT NOT NULL, " +
                        "weight TEXT NOT NULL, dietRestrictions TEXT NOT NULL, contactWithHumans TEXT NOT NULL, " +
                        "contactWithCats TEXT NOT NULL, catStatus TEXT NOT NULL, statusReason TEXT NOT NULL, " +
                        "rescuedDateEpochDay INTEGER NOT NULL, statusDateEpochDay INTEGER, statusCode TEXT NOT NULL, " +
                        "coverMediaId TEXT, photoUrls TEXT NOT NULL, cityCode TEXT, districtCode TEXT, " +
                        "publishState TEXT NOT NULL, serverCatId TEXT, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO public_drafts_v14 (" +
                        "id, catId, publicNickname, alias, story, gender, ageBand, coat, personalityTags, " +
                        "healthSummary, weight, dietRestrictions, contactWithHumans, contactWithCats, catStatus, " +
                        "statusReason, rescuedDateEpochDay, statusDateEpochDay, statusCode, coverMediaId, photoUrls, " +
                        "cityCode, districtCode, publishState, serverCatId, updatedAt) " +
                        "SELECT id, catId, publicNickname, alias, story, gender, ageBand, coat, personalityTags, " +
                        "healthSummary, weight, dietRestrictions, contactWithHumans, contactWithCats, catStatus, " +
                        "statusReason, rescuedDateEpochDay, statusDateEpochDay, statusCode, coverMediaId, '', " +
                        "cityCode, districtCode, publishState, serverCatId, updatedAt FROM public_drafts"
                )
                db.execSQL("DROP TABLE public_drafts")
                db.execSQL("ALTER TABLE public_drafts_v14 RENAME TO public_drafts")
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE public_drafts ADD COLUMN mediaUploadState TEXT")
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN localUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN remoteUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN serverMediaId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media ADD COLUMN syncState TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE media ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE media SET sortOrder = createdAt")
                db.execSQL("UPDATE media SET remoteUrl = uri, syncState = 'uploaded' WHERE uri LIKE 'http://%' OR uri LIKE 'https://%'")
                db.execSQL("UPDATE media SET localUri = uri WHERE remoteUrl = ''")
            }
        }

        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cats ADD COLUMN archiveReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cats ADD COLUMN legacyStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE public_drafts ADD COLUMN archiveReason TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE cats SET legacyStatus = status WHERE UPPER(status) NOT IN (" +
                        "'RESCUE','RESCUING','INTAKE','TREATING','RECOVERING','ADOPTABLE','READY_FOR_ADOPTION'," +
                        "'MATCHING','RETURNED','ADOPTED','FOLLOW_UP','ARCHIVED','DECEASED','LOST','LOST_CONTACT')"
                )
                db.execSQL("UPDATE cats SET archiveReason = 'deceased' WHERE UPPER(status) = 'DECEASED'")
                db.execSQL("UPDATE cats SET archiveReason = 'lost' WHERE UPPER(status) IN ('LOST','LOST_CONTACT')")
                db.execSQL("UPDATE cats SET status = 'rescue' WHERE UPPER(status) IN ('RESCUE','RESCUING','INTAKE')")
                db.execSQL("UPDATE cats SET status = 'treating' WHERE UPPER(status) IN ('TREATING','RECOVERING')")
                db.execSQL("UPDATE cats SET status = 'adoptable' WHERE UPPER(status) IN ('ADOPTABLE','READY_FOR_ADOPTION','MATCHING','RETURNED')")
                db.execSQL("UPDATE cats SET status = 'adopted' WHERE UPPER(status) IN ('ADOPTED','FOLLOW_UP')")
                db.execSQL("UPDATE cats SET status = 'archived' WHERE UPPER(status) IN ('ARCHIVED','DECEASED','LOST','LOST_CONTACT')")
                db.execSQL("UPDATE cats SET status = 'rescue' WHERE status NOT IN ('rescue','treating','adoptable','adopted','archived')")
                db.execSQL("UPDATE public_drafts SET archiveReason = 'deceased' WHERE UPPER(statusCode) = 'DECEASED'")
                db.execSQL("UPDATE public_drafts SET archiveReason = 'lost' WHERE UPPER(statusCode) IN ('LOST','LOST_CONTACT')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'rescue', catStatus = '救助中' WHERE UPPER(statusCode) IN ('RESCUE','RESCUING','INTAKE')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'treating', catStatus = '治疗中' WHERE UPPER(statusCode) IN ('TREATING','RECOVERING')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'adoptable', catStatus = '待领养' WHERE UPPER(statusCode) IN ('ADOPTABLE','READY_FOR_ADOPTION','MATCHING','RETURNED')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'adopted', catStatus = '已领养' WHERE UPPER(statusCode) IN ('ADOPTED','FOLLOW_UP')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'archived', catStatus = '已归档' WHERE UPPER(statusCode) IN ('ARCHIVED','DECEASED','LOST','LOST_CONTACT')")
                db.execSQL("UPDATE public_drafts SET statusCode = 'rescue', catStatus = '救助中', archiveReason = '' WHERE statusCode NOT IN ('rescue','treating','adoptable','adopted','archived')")
            }
        }

        /** v10 → v11：猫咪档案地区（内置库选择） */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cats ADD COLUMN cityName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cats ADD COLUMN districtName TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v9 → v10：档案版本增加关联领养人摘要 */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cat_versions ADD COLUMN adopterSummary TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v8 → v9：猫咪档案深度完善——医疗记录称重/行为变化/结果、全量版本快照、亲属关系 */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medical_records ADD COLUMN weight TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medical_records ADD COLUMN behaviorNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medical_records ADD COLUMN result TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cat_versions (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "catId TEXT NOT NULL, " +
                        "versionNo INTEGER NOT NULL, " +
                        "snapshotJson TEXT NOT NULL, " +
                        "changeSummary TEXT NOT NULL, " +
                        "reason TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cat_relations (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "catId TEXT NOT NULL, " +
                        "relatedCatId TEXT NOT NULL, " +
                        "relationType TEXT NOT NULL, " +
                        "note TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context, name: String = "catlink-vault.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                )
                .build()
    }
}
