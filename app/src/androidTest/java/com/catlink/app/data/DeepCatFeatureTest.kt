package com.catlink.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.CatEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 猫咪档案深度完善：自动版本快照、亲属关系双向、医疗称重同步与消费汇总。 */
class DeepCatFeatureTest {
    private val databaseName = "deep-cat-feature-test.db"
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: VaultRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        db = AppDatabase.build(context, databaseName)
        repo = VaultRepository(db.vaultDao(), context)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun cat(id: String, weight: String = "3.0kg") = CatEntity(
        id = id, nickname = "测试猫$id", status = CatStatus.RESCUE.code, healthStatus = "OK",
        rescuedDateEpochDay = 20000, weight = weight,
    )

    @Test fun catStatus_enforcesArchiveReasonAndDeceasedTerminal() = runBlocking {
        val dao = db.vaultDao()
        dao.upsertCat(cat("status"))

        assertTrue(repo.changeCatStatus("status", CatStatus.ADOPTED).isFailure)
        assertTrue(repo.changeCatStatus("status", CatStatus.ARCHIVED).isFailure)
        assertTrue(repo.changeCatStatus("status", CatStatus.ARCHIVED, archiveReason = ArchiveReason.LOST).isSuccess)
        assertEquals(ArchiveReason.LOST.code, repo.catById("status")!!.archiveReason)
        assertTrue(repo.changeCatStatus("status", CatStatus.ADOPTABLE).isSuccess)
        assertTrue(repo.changeCatStatus("status", CatStatus.ARCHIVED, archiveReason = ArchiveReason.DECEASED).isSuccess)
        assertTrue(repo.changeCatStatus("status", CatStatus.ADOPTABLE).isFailure)

        val final = repo.catById("status")!!
        assertEquals(CatStatus.ARCHIVED.code, final.status)
        assertEquals(ArchiveReason.DECEASED.code, final.archiveReason)
    }

    @Test fun updateCat_autoSnapshotsPreviousVersion() = runBlocking {
        val dao = db.vaultDao()
        dao.upsertCat(cat("v1"))
        repo.catById("v1")!!

        // 编辑档案：体重 + 状态变化 → 自动生成版本 1
        repo.updateCat(
            id = "v1", nickname = "测试猫v1", alias = "", gender = "", ageEstimate = "",
            coat = "", weight = "3.5kg", personality = "粘人", story = "",
            rescuedDate = LocalDate.ofEpochDay(20000), healthStatus = HealthStatus.OK, tags = "已绝育",
        )
        val versions = repo.catVersions("v1")
        assertEquals(1, versions.size)
        assertEquals(1, versions[0].versionNo)
        assertTrue(versions[0].changeSummary.contains("称重"))
        // 快照内容为变更前档案
        val snapshot = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(com.catlink.app.data.db.CatEntity.serializer(), versions[0].snapshotJson)
        assertEquals("3.0kg", snapshot.weight)

        // 再次编辑（无变化）→ 不产生新版本
        repo.updateCat(
            id = "v1", nickname = "测试猫v1", alias = "", gender = "", ageEstimate = "",
            coat = "", weight = "3.5kg", personality = "粘人", story = "",
            rescuedDate = LocalDate.ofEpochDay(20000), healthStatus = HealthStatus.OK, tags = "已绝育",
        )
        assertEquals(1, repo.catVersions("v1").size)
    }

    @Test fun addMedical_weightAndCostSyncToCatAndSummary() = runBlocking {
        val dao = db.vaultDao()
        dao.upsertCat(cat("m1"))
        repo.addMedical(
            catId = "m1", date = LocalDate.now(), category = MedicalCategory.SPAY,
            title = "绝育手术", detail = "住院一天", hospital = "爱心宠物医院",
            cost = "350", nextReminder = null, weight = "3.2kg", behaviorNote = "绝育后变粘人", result = "恢复良好",
        )
        // 体重/性格同步到档案
        val updated = repo.catById("m1")!!
        assertEquals("3.2kg", updated.weight)
        assertEquals("绝育后变粘人", updated.personality)
        // 消费汇总
        val cost = repo.totalMedicalCost("m1")
        android.util.Log.d("DEEP_TEST", "totalCost=$cost medicals=${db.vaultDao().medicalsSnapshot().size}")
        assertEquals(350.0, cost, 0.01)
        // 标签联动：绝育 → 已绝育
        assertTrue(updated.tags.contains("已绝育"))
        // 医疗记录新字段
        val medicals = db.vaultDao().medicalsSnapshot().filter { it.catId == "m1" }
        assertEquals(1, medicals.size)
        assertEquals("3.2kg", medicals[0].weight)
        assertEquals("恢复良好", medicals[0].result)
    }

    @Test fun relations_areBidirectionalAndGraphResolves() = runBlocking {
        val dao = db.vaultDao()
        dao.upsertCat(cat("mom"))
        dao.upsertCat(cat("kid"))
        repo.addCatRelation("mom", "kid", CatRelationType.OFFSPRING, "同窝")

        // 双方可见
        assertEquals(1, repo.catRelations("mom").size)
        assertEquals(1, repo.catRelations("kid").size)

        // 关系图解析对方猫
        val graph = repo.relationGraph("mom")
        assertEquals(1, graph.size)
        val (relation, other) = graph[0]
        assertEquals("kid", other?.id)
        assertEquals(CatRelationType.OFFSPRING.name, relation.relationType)

        // 重复添加被忽略
        repo.addCatRelation("mom", "kid", CatRelationType.OFFSPRING)
        assertEquals(1, repo.catRelations("mom").size)

        // 自我关联被忽略
        repo.addCatRelation("mom", "mom", CatRelationType.OTHER)
        assertEquals(1, repo.catRelations("mom").size)

        // 删除关系后双方不可见
        val relationId = repo.catRelations("mom")[0].id
        repo.removeCatRelation(relationId)
        assertEquals(0, repo.catRelations("mom").size)
        assertEquals(0, repo.catRelations("kid").size)

        // 反向交叉去重：kid 已删除关系后，mom→kid 可重新添加；反向重复被拦截
        repo.addCatRelation("mom", "kid", CatRelationType.OFFSPRING)
        repo.addCatRelation("kid", "mom", CatRelationType.MOTHER)
        assertEquals(1, repo.catRelations("mom").size)
        assertEquals(1, repo.catRelations("kid").size)
    }
}
