package com.catlink.app.data

import java.time.LocalDate

/** 猫咪主状态。持久化与 API 统一使用 [code]，中文只用于展示。 */
enum class CatStatus(val code: String, val label: String) {
    RESCUE("rescue", "救助中"),
    TREATING("treating", "治疗中"),
    ADOPTABLE("adoptable", "待领养"),
    ADOPTED("adopted", "已领养"),
    ARCHIVED("archived", "已归档");

    companion object {
        fun fromName(value: String): CatStatus = fromNameOrNull(value) ?: RESCUE

        fun fromNameOrNull(value: String): CatStatus? = when (value.trim().uppercase()) {
            "RESCUE", "RESCUING", "INTAKE", "救助中" -> RESCUE
            "TREATING", "RECOVERING", "恢复中", "治疗中" -> TREATING
            "ADOPTABLE", "READY_FOR_ADOPTION", "MATCHING", "RETURNED", "待领养", "匹配中", "被退回" -> ADOPTABLE
            "ADOPTED", "FOLLOW_UP", "已领养", "回访中" -> ADOPTED
            "ARCHIVED", "DECEASED", "LOST", "LOST_CONTACT", "已归档", "已离世", "离世", "失踪", "失联" -> ARCHIVED
            else -> null
        }

        fun canTransition(from: CatStatus, to: CatStatus, currentArchiveReason: ArchiveReason? = null): Boolean =
            to in from.nextStates(currentArchiveReason)
    }
}

enum class ArchiveReason(val code: String, val label: String) {
    LOST("lost", "失踪"),
    DECEASED("deceased", "离世"),
    OTHER("other", "其他");

    companion object {
        fun fromNameOrNull(value: String): ArchiveReason? = when (value.trim().uppercase()) {
            "LOST", "LOST_CONTACT", "失踪", "失联" -> LOST
            "DECEASED", "离世", "已离世" -> DECEASED
            "OTHER", "其他" -> OTHER
            else -> null
        }

        fun fromLegacyStatus(value: String): ArchiveReason? = when (value.trim().uppercase()) {
            "LOST", "LOST_CONTACT", "失踪", "失联" -> LOST
            "DECEASED", "离世", "已离世" -> DECEASED
            else -> null
        }
    }
}

/** 离世归档不可恢复；失踪与其他归档允许重新进入业务流程。 */
fun CatStatus.nextStates(currentArchiveReason: ArchiveReason? = null): List<CatStatus> = when (this) {
    CatStatus.RESCUE -> listOf(CatStatus.TREATING, CatStatus.ADOPTABLE, CatStatus.ARCHIVED)
    CatStatus.TREATING -> listOf(CatStatus.RESCUE, CatStatus.ADOPTABLE, CatStatus.ARCHIVED)
    CatStatus.ADOPTABLE -> listOf(CatStatus.RESCUE, CatStatus.TREATING, CatStatus.ADOPTED, CatStatus.ARCHIVED)
    CatStatus.ADOPTED -> listOf(CatStatus.ADOPTABLE, CatStatus.ARCHIVED)
    CatStatus.ARCHIVED -> if (currentArchiveReason == ArchiveReason.DECEASED) emptyList() else
        listOf(CatStatus.RESCUE, CatStatus.TREATING, CatStatus.ADOPTABLE, CatStatus.ADOPTED)
}

/** 健康状态 */
enum class HealthStatus(val label: String) {
    OK("健康"),
    TREATING("治疗中"),
    UNKNOWN("待检查");

    companion object {
        fun fromName(name: String): HealthStatus =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** 医疗记录类别（对应产品文档 6.3） */
enum class MedicalCategory(val label: String) {
    RESCUE("救助"),
    TREATMENT("治疗"),
    CHECKUP("体况"),
    DIAGNOSIS("诊断"),
    EXAM("检查"),
    VACCINE("疫苗"),
    DEWORM("驱虫"),
    SPAY("绝育"),
    MEDICATION("用药"),
    REVIEW("复诊");

    companion object {
        fun fromName(name: String): MedicalCategory =
            entries.firstOrNull { it.name == name } ?: CHECKUP
    }
}

/** 领养个案阶段（对应产品文档 7.2 状态机） */
/** 个案阶段（产品文档 7.2 简化为 6 步主流程） */
enum class CaseStage(val label: String) {
    APPLIED("已申请"),
    SCREENING("沟通中"),
    CONTRACT("已签约"),
    HANDOVER("已交接"),
    FOLLOW_UP("回访中"),
    CLOSED("已结束"),
    RETURNED("已退养"),
    REJECTED("未通过"),
    WITHDRAWN("已撤回");

    companion object {
        /** 兼容 v4 之前的旧阶段名 */
        fun fromName(name: String): CaseStage = when (name) {
            "DRAFT" -> APPLIED
            "APPROVED" -> SCREENING
            "CONTRACT_PENDING" -> CONTRACT
            "HANDOVER_SCHEDULED" -> HANDOVER
            "COMPLETED", "FOLLOW_UP_OPEN" -> FOLLOW_UP
            else -> entries.firstOrNull { it.name == name } ?: APPLIED
        }
    }
}

/** 合同状态（对应产品文档 7.3） */
enum class ContractStatus(val label: String) {
    PREPARED("待签署"),
    SIGNED("已签署"),
    SUPERSEDED("已替代"),
    VOIDED("已作废");

    companion object {
        fun fromName(name: String): ContractStatus =
            entries.firstOrNull { it.name == name } ?: PREPARED
    }
}

/** 回访状态 */
enum class FollowUpStatus(val label: String) {
    PENDING("待回访"),
    DONE("已完成"),
    OVERDUE("已逾期");

    companion object {
        fun fromName(name: String): FollowUpStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

/** 待办优先级 */
enum class Priority(val label: String) {
    HIGH("紧急"),
    NORMAL("普通"),
    LOW("低");

    companion object {
        fun fromName(name: String): Priority =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** 时间线事件类型 */
enum class EventKind(val label: String) {
    RESCUE("救助"),
    HEALTH("健康"),
    ADOPTION("领养"),
    FOLLOW_UP("回访"),
    STATUS("状态");

    companion object {
        fun fromName(name: String): EventKind =
            entries.firstOrNull { it.name == name } ?: STATUS
    }
}

/** AI 对话消息（data 层，供会话持久化使用） */
data class ChatMessage(val role: String, val content: String)

/** 待办事项（UI 模型） */
data class TodoItem(    val id: String,
    val title: String,
    val subtitle: String = "",
    val dueText: String = "",
    val priority: Priority = Priority.NORMAL,
    val done: Boolean = false,
)

/** 时间线事件（UI 模型） */
data class TimelineEvent(
    val date: LocalDate,
    val title: String,
    val detail: String = "",
    val kind: EventKind,
)

/** 统计概览（UI 模型） */
data class HomeStats(
    val totalCats: Int = 0,
    val rescued: Int = 0,
    val adopted: Int = 0,
    val readyForAdoption: Int = 0,
    val pendingFollowUp: Int = 0,
    val treating: Int = 0,
    val followUpDone: Int = 0,
    val followUpTotal: Int = 0,
    val followUpOnTime: Int = 0,
    val vaccinated: Int = 0,
    val dewormed: Int = 0,
    val spayed: Int = 0,
    val streakDays: Int = 0,
    val avgWaitDays: Int? = null,       // 平均待领养时长（天）
    val waitSampleCount: Int = 0,       // 样本数
    val lastModifiedAt: Long = 0,       // 本机最后修改时间
) {
    /** 领养转化率 = 完成领养 / 进入待领养（分母为 0 返回 null，不显示百分比） */
    val adoptionRate: Float? get() =
        if (readyForAdoption + adopted == 0) null else adopted.toFloat() / (readyForAdoption + adopted)

    /** 回访完成率 */
    val followUpRate: Float? get() =
        if (followUpTotal == 0) null else followUpDone.toFloat() / followUpTotal

    /** 回访及时率 = 按时完成 / 完成数 */
    val followUpOnTimeRate: Float? get() =
        if (followUpDone == 0) null else followUpOnTime.toFloat() / followUpDone
}

/** 猫咪亲属关系类型 */
enum class CatRelationType(val label: String) {
    MOTHER("母亲"),
    FATHER("父亲"),
    OFFSPRING("子女"),
    SIBLING("同胞"),
    OTHER("其他");

    companion object {
        fun fromName(name: String): CatRelationType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
