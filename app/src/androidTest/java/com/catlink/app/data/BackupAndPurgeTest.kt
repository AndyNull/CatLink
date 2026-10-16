package com.catlink.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catlink.app.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class BackupAndPurgeTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before fun setup() {
        root = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "backup_test_${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        }
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun cleanup() { source.close(); target.close(); root.deleteRecursively() }

    @Test fun version2BackupRoundTripsEveryEntityField() = runBlocking {
        val dao = source.vaultDao()
        val cat = CatEntity(
            id="cat-1", nickname="完整字段猫", alias="alias", status=CatStatus.ADOPTED.code, healthStatus="TREATING",
            gender="F", ageEstimate="2y", coat="calico", weight="3.8", personality="calm",
            dietRestrictions="fish", contactWithHumans="friendly", contactWithCats="slow", statusDateEpochDay=100,
            statusReason="reason", story="story", rescuedDateEpochDay=88, tags="a,b", coverColorIndex=5,
            coverUri="/data/user/0/test/files/photos/cover.jpg", deletedAt=999, createdAt=11, updatedAt=12,
        )
        val medical = MedicalEntity(id = "med-1", catId = cat.id, dateEpochDay = 90, category = "VACCINE", title = "title", detail = "detail", hospital = "hospital", cost = "88.5", nextReminderEpochDay = 120, createdAt = 13)
        val adopter = AdopterEntity(
            id="adopter-1", name="name", phone="1", wechat="wx", qq="qq", email="e", idCard="id", address="addr",
            backupContact="backup", preferredContact="wechat", residence="rent", petExperience="yes", familyMembers="2",
            contactAuthorized=true, authorizedAt=77, notes="notes", deletedAt=888, createdAt=14,
        )
        val adoption = CaseEntity("case-1", cat.id, adopter.id, "COMPLETED", "source", "notes", 101, "/data/handover.jpg", "reject", "withdraw", 15, 16)
        val contract = ContractEntity("contract-1", adoption.id, "SIGNED", 102, "hash", "/data/sign.jpg", "/data/hand.jpg", "contract-note", "", 17)
        val follow = FollowUpEntity("follow-1", adoption.id, 103, "DONE", 104, "video", "follow-note", 2, "需注意", 18)
        val timeline = TimelineEntity("time-1", cat.id, 88, "event", "detail", "STATUS", 19)
        val todo = TodoEntity("todo-1", "todo", "followup:follow-1|case:case-1|x", 105, "HIGH", true, cat.id, 20)
        val note = CaseNoteEntity("note-1", adoption.id, 106, "电话", "content", 21)
        val media = MediaEntity("media-1", cat.id, "/data/media.jpg", sortOrder = 22, createdAt = 22)
        dao.upsertCat(cat); dao.upsertMedical(medical); dao.upsertAdopter(adopter); dao.upsertCase(adoption)
        dao.upsertContract(contract); dao.upsertFollowUp(follow); dao.upsertTimeline(timeline); dao.upsertTodo(todo)
        dao.upsertCaseNote(note); dao.upsertMedia(media)

        val backup = File(context.cacheDir, "vault.clb")
        assertTrue(BackupManager.exportToFile(dao, backup, "roundtrip-password").ok)
        val imported = BackupManager.importFromFile(target, backup, "roundtrip-password")
        assertTrue(imported.message, imported.ok)
        assertEquals(dao.snapshot(), target.vaultDao().snapshot())

        val sentinel = CatEntity("sentinel", "keep", "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=1)
        target.vaultDao().upsertCat(sentinel)
        val wrong = BackupManager.importFromFile(target, backup, "wrong-password")
        assertFalse(wrong.ok)
        assertEquals(sentinel, target.vaultDao().catById("sentinel"))
        assertFalse(BackupManager.exportToFile(dao, File(context.cacheDir, "weak.clb"), "123456").ok)

        val legacyPlain = "{\"app\":\"CatLink\",\"version\":1,\"cats\":[]}".toByteArray()
        val legacyFile = File(context.cacheDir, "legacy.clb")
        legacyFile.writeBytes(legacyEncrypt(legacyPlain, "123456"))
        val legacy = BackupManager.importFromFile(target, legacyFile, "123456")
        assertTrue(legacy.message, legacy.ok)
    }

    private fun legacyEncrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return salt + cipher.iv + cipher.doFinal(data)
    }

    @Test fun interruptedRestoreJournalRecoversOrFinalizesDeterministically() = runBlocking {
        val dao = source.vaultDao()
        val settings = SettingsStore(context)
        // 记录共享状态基线（acceptedSync 只增不减，回滚不得改变它）
        settings.clearRestoreJournal()
        val acceptedBefore = settings.acceptedSync()
        val recovery = File(context.noBackupFilesDir, "sync_recovery")
        val oldCat = CatEntity("old", "old", "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=1)
        val newCat = CatEntity("new", "new", "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=2)
        dao.upsertCat(oldCat)
        recovery.mkdirs()
        val password = "journal-recovery-password"
        assertTrue(BackupManager.exportToFile(dao, File(recovery, "old.clb"), password).ok)
        settings.beginRestore("snapshot-prepared", 3, password)
        dao.clearAll(); dao.upsertCat(newCat)
        val currentPhotos = File(context.filesDir, "photos").apply { mkdirs(); File(this, "new.jpg").writeText("new") }
        val mediaBackup = File(recovery, "media_work/original/photos").apply { mkdirs(); File(this, "old.jpg").writeText("old") }
        SyncRestoreCoordinator.recoverInterrupted(context, source, settings)
        assertEquals(oldCat, dao.catById(oldCat.id))
        assertEquals(null, dao.catById(newCat.id))
        assertTrue(File(context.filesDir, "photos/old.jpg").isFile)
        assertFalse(currentPhotos.resolve("new.jpg").exists())
        assertEquals(null, settings.restoreJournal())
        assertEquals(acceptedBefore, settings.acceptedSync())
        assertFalse(recovery.exists())

        dao.clearAll(); dao.upsertCat(newCat)
        File(context.filesDir, "photos").apply { mkdirs(); File(this, "committed.jpg").writeText("committed") }
        recovery.mkdirs()
        settings.beginRestore("snapshot-committed", 4, password)
        settings.markRestoreDatabaseCommitted()
        SyncRestoreCoordinator.recoverInterrupted(context, source, settings)
        assertEquals(newCat, dao.catById(newCat.id))
        assertTrue(File(context.filesDir, "photos/committed.jpg").isFile)
        assertEquals(null, settings.restoreJournal())
        assertEquals(4L, settings.acceptedSync().generation)
        assertEquals("snapshot-committed", settings.acceptedSync().snapshotId)

        settings.beginRestore("snapshot-missing", 5, password)
        val failed = SyncRestoreCoordinator.recoverInterrupted(context, source, settings)
        assertFalse(failed.first)
        assertTrue(settings.restoreJournal() != null)
        settings.clearRestoreJournal()

        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        kotlinx.coroutines.coroutineScope {
            val first = async(Dispatchers.Default) {
                SyncOperationGate.run { entered.complete(Unit); release.await() }
            }
            entered.await()
            val second = async(Dispatchers.Default) {
                SyncOperationGate.runIfNoRestore(settings, onPending = { "blocked" }) { "ran" }
            }
            settings.beginRestore("snapshot-queued", 6, password)
            release.complete(Unit)
            first.await()
            assertEquals("blocked", second.await())
        }
        settings.clearRestoreJournal()
    }

    @Test fun restoreAndExpiryPurgeNeverCreatePartialCat() = runBlocking {
        val dao = source.vaultDao()
        repeat(40) { index ->
            val id = "race-$index"
            dao.upsertCat(CatEntity(id, id, "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=1, deletedAt=1L))
            dao.upsertMedical(MedicalEntity("med-$index", id, 1, "OTHER", "x"))
            val repository = VaultRepository(dao, context)
            kotlinx.coroutines.coroutineScope {
                val restore = async(Dispatchers.Default) { repository.restoreCat(id) }
                val purge = async(Dispatchers.Default) { repository.purgeExpiredTrash(2L) }
                restore.await(); purge.await()
            }
            val cat = dao.catById(id)
            val medicalExists = dao.snapshot().medicals.any { it.catId == id }
            if (cat == null) assertFalse(medicalExists)
            else {
                assertEquals(null, cat.deletedAt)
                assertTrue(medicalExists)
            }
            if (cat != null) repository.purgeCat(id)
        }
    }

    @Test fun purgeCascadeDeletesOnlyTargetAndPrivateMedia() = runBlocking {
        val dao = source.vaultDao()
        val targetCat = CatEntity("purge", "purge", "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=1)
        val keepCat = CatEntity("keep", "keep", "", CatStatus.RESCUE.code, "UNKNOWN", rescuedDateEpochDay=1)
        val photo = File(MediaFiles.photosDir(context), "target.jpg").apply { writeText("photo") }
        val albumLocal = File(MediaFiles.photosDir(context), "album-local.jpg").apply { writeText("album") }
        val captured = File(context.filesDir, "catlink_media/contract.jpg").apply { parentFile?.mkdirs(); writeText("contract") }
        dao.upsertCat(targetCat.copy(coverUri=photo.absolutePath)); dao.upsertCat(keepCat)
        dao.upsertMedical(MedicalEntity("med", targetCat.id, 1, "OTHER", "x"))
        dao.upsertTimeline(TimelineEntity("timeline", targetCat.id, 1, "x", "", "OTHER"))
        dao.upsertTodo(TodoEntity("todo", "x", catId=targetCat.id))
        dao.upsertMedia(
            MediaEntity(
                "media",
                targetCat.id,
                "https://api.example/uploads/cat/remote.jpg",
                localUri = albumLocal.absolutePath,
                remoteUrl = "https://api.example/uploads/cat/remote.jpg",
                serverMediaId = "1",
                syncState = AlbumSyncState.UPLOADED.value,
            )
        )
        dao.upsertCase(CaseEntity("case", targetCat.id, "a", "COMPLETED"))
        dao.upsertContract(ContractEntity("contract", "case", "SIGNED", signaturePhotoUri=captured.absolutePath))
        dao.upsertFollowUp(FollowUpEntity("follow", "case", 2, "PLANNED"))
        dao.upsertCaseNote(CaseNoteEntity("note", "case", 2, "电话", "x"))
        dao.upsertTimeline(TimelineEntity("keep-time", keepCat.id, 1, "keep", "", "OTHER"))

        VaultRepository(dao, context).purgeCat(targetCat.id)
        val snap = dao.snapshot()
        assertTrue(snap.cats.none { it.id == targetCat.id })
        assertTrue(snap.medicals.none { it.catId == targetCat.id })
        assertTrue(snap.timeline.none { it.catId == targetCat.id })
        assertTrue(snap.todos.none { it.catId == targetCat.id })
        assertTrue(snap.media.none { it.catId == targetCat.id })
        assertTrue(snap.cases.none { it.catId == targetCat.id })
        assertTrue(snap.contracts.none { it.caseId == "case" })
        assertTrue(snap.followUps.none { it.caseId == "case" })
        assertTrue(snap.caseNotes.none { it.caseId == "case" })
        assertEquals(keepCat, dao.catById(keepCat.id))
        assertTrue(snap.timeline.any { it.id == "keep-time" })
        assertFalse(photo.exists())
        assertFalse(albumLocal.exists())
        assertFalse(captured.exists())
    }
}
