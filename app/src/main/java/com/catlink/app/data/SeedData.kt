package com.catlink.app.data

import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.VaultDao
import java.time.LocalDate

/** 首次启动种子数据：6 只演示猫咪 + 医疗 + 时间线 + 待办 */
object SeedData {

    suspend fun seed(repository: VaultRepository, dao: VaultDao) {
        val d = LocalDate.now()
        seedCat(
            dao, repository,
            nickname = "咪咪",
            alias = "三花小咪",
            status = CatStatus.ADOPTABLE,
            health = HealthStatus.OK,
            gender = "妹妹",
            age = "约 8 个月",
            coat = "三花 · 短毛",
            weight = "2.9 kg",
            personality = "亲人，喜欢蹭手，怕大声音",
            story = "在小区车棚下发现，当时瘦弱怕人，喂了半个月后主动蹭手。已驱虫，疫苗完成。",
            rescued = d.minusDays(96),
            tags = "亲人,已疫苗,已驱虫",
            cover = 0,
        )
        seedCat(
            dao, repository,
            nickname = "月亮",
            alias = "Moon",
            status = CatStatus.ADOPTED,
            health = HealthStatus.OK,
            gender = "妹妹",
            age = "约 1 岁",
            coat = "灰白 · 短毛",
            weight = "3.6 kg",
            personality = "文静，爱看窗外，会自己玩球",
            story = "雨夜里被救，身上有旧伤，恢复后非常安静乖巧。已绝育，疫苗完成。",
            rescued = d.minusDays(170),
            tags = "已绝育,已疫苗",
            cover = 1,
        )
        seedCat(
            dao, repository,
            nickname = "小虎",
            alias = "",
            status = CatStatus.TREATING,
            health = HealthStatus.TREATING,
            gender = "弟弟",
            age = "约 5 个月",
            coat = "橘白 · 短毛",
            weight = "2.1 kg",
            personality = "活泼好动，干饭积极",
            story = "后巷纸箱里发现，有轻微猫藓，正在治疗中。胃口很好，恢复顺利。",
            rescued = d.minusDays(26),
            tags = "治疗中,橘猫",
            cover = 2,
        )
        seedCat(
            dao, repository,
            nickname = "雪球",
            alias = "白白",
            status = CatStatus.ADOPTED,
            health = HealthStatus.OK,
            gender = "弟弟",
            age = "约 2 岁",
            coat = "纯白 · 长毛",
            weight = "4.1 kg",
            personality = "黏人，爱咕噜，怕陌生人",
            story = "去年冬天从桥洞救出，已送养，回访中一切正常。",
            rescued = d.minusDays(240),
            tags = "已领养,长毛",
            cover = 3,
        )
        seedCat(
            dao, repository,
            nickname = "豆包",
            alias = "",
            status = CatStatus.ADOPTABLE,
            health = HealthStatus.OK,
            gender = "弟弟",
            age = "约 10 个月",
            coat = "狸花 · 短毛",
            weight = "3.2 kg",
            personality = "大胆，会主动找人玩",
            story = "救助后一直在室内养着，性格稳定，正在匹配领养人。",
            rescued = d.minusDays(137),
            tags = "亲人,已绝育",
            cover = 4,
        )
        seedCat(
            dao, repository,
            nickname = "奶糖",
            alias = "",
            status = CatStatus.RESCUE,
            health = HealthStatus.UNKNOWN,
            gender = "妹妹",
            age = "约 3 个月",
            coat = "乳白 · 短毛",
            weight = "0.9 kg",
            personality = "奶声奶气，睡觉很香",
            story = "今天刚在公园长椅下捡到，待体检。",
            rescued = d.minusDays(1),
            tags = "新救助,待体检",
            cover = 5,
        )

        // 医疗记录 + 待办
        repository.addMedical("seed-咪咪", d.minusDays(91), MedicalCategory.DEWORM, "完成驱虫", "外驱 + 内驱", "", "", null)
        repository.addMedical("seed-咪咪", d.minusDays(84), MedicalCategory.VACCINE, "猫三联第一针", "基础疫苗", "", "", null)
        repository.addMedical("seed-咪咪", d.minusDays(56), MedicalCategory.VACCINE, "猫三联第二针", "加强疫苗", "", "", null)
        repository.addMedical("seed-月亮", d.minusDays(160), MedicalCategory.SPAY, "完成绝育", "术后恢复良好", "", "", null)
        repository.addMedical("seed-小虎", d.minusDays(25), MedicalCategory.DIAGNOSIS, "确诊猫藓", "开始用药治疗", "", "", null)
        repository.addMedical("seed-小虎", d.minusDays(12), MedicalCategory.REVIEW, "复诊", "恢复良好，继续用药一周", "", "", d.plusDays(2))
        repository.addMedical("seed-豆包", d.minusDays(120), MedicalCategory.SPAY, "完成绝育", "恢复顺利", "", "", null)
        repository.addMedical("seed-雪球", d.minusDays(230), MedicalCategory.VACCINE, "猫三联完成", "免疫完成", "", "", null)

        repository.addTodo("跟进咪咪的领养人", "本周六下午家访", d.plusDays(2), Priority.HIGH)
        repository.addTodo("带小虎去复诊", "复查猫藓恢复情况", d.plusDays(1), Priority.NORMAL)
        repository.addTodo("雪球回访 · 电话", "领养后第 30 天回访", d.plusDays(6), Priority.NORMAL)
        repository.addTodo("豆包绝育预约", "联系合作医院", null, Priority.LOW)
    }

    private suspend fun seedCat(
        dao: VaultDao,
        repository: VaultRepository,
        nickname: String,
        alias: String,
        status: CatStatus,
        health: HealthStatus,
        gender: String,
        age: String,
        coat: String,
        weight: String,
        personality: String,
        story: String,
        rescued: LocalDate,
        tags: String,
        cover: Int,
    ) {
        val id = "seed-$nickname"
        dao.upsertCat(
            CatEntity(
                id = id,
                nickname = nickname,
                alias = alias,
                status = status.code,
                healthStatus = health.name,
                gender = gender,
                ageEstimate = age,
                coat = coat,
                weight = weight,
                personality = personality,
                story = story,
                rescuedDateEpochDay = rescued.toEpochDay(),
                tags = tags,
                coverColorIndex = cover,
            )
        )
        repository.addTimeline(id, rescued, "救助建档", "记录了一位新朋友", EventKind.RESCUE)
        repository.addTimeline(id, rescued.plusDays(3), "完成体检", "基础体检正常", EventKind.HEALTH)
    }
}
