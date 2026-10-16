package com.catlink.app.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.catlink.app.data.Priority

/** 猫咪档案（对应产品文档 6.1） */
@kotlinx.serialization.Serializable
@Entity(tableName = "cats")
data class CatEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val alias: String = "",
    val status: String,            // CatStatus.code
    val healthStatus: String,      // HealthStatus.name
    val gender: String = "",
    val ageEstimate: String = "",
    val coat: String = "",
    val weight: String = "",
    val personality: String = "",
    val dietRestrictions: String = "",     // 饮食禁忌
    val contactWithHumans: String = "",    // 与人相处
    val contactWithCats: String = "",      // 与猫相处
    val statusDateEpochDay: Long? = null,  // 当前状态发生日期
    val statusReason: String = "",         // 当前状态原因
    @ColumnInfo(defaultValue = "''") val archiveReason: String = "", // ArchiveReason.code，仅已归档有效
    @ColumnInfo(defaultValue = "''") val legacyStatus: String = "",  // 无法识别的升级前状态，供审计与回滚
    val story: String = "",
    val rescuedDateEpochDay: Long, // LocalDate.toEpochDay
    val tags: String = "",         // 逗号分隔
    val coverColorIndex: Int = 0,
    val coverUri: String = "",      // 封面照片（拍照/相册选择）
    val cityName: String = "",      // 地区（内置库选择）
    val districtName: String = "",  // 区县（内置库选择）
    val deletedAt: Long? = null,   // 回收站 tombstone（产品文档 7.4）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 健康/医疗记录（产品文档 6.3） */
@kotlinx.serialization.Serializable
@Entity(tableName = "medical_records")
data class MedicalEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val dateEpochDay: Long,
    val category: String,          // MedicalCategory.name
    val title: String,
    val detail: String = "",
    val hospital: String = "",
    val cost: String = "",         // 消费金额（元）
    val weight: String = "",       // 称重记录（如 3.2kg）
    val behaviorNote: String = "", // 性格/行为变化
    val result: String = "",       // 治疗结果/恢复情况
    val nextReminderEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 猫咪档案全量版本快照（每次内容变更自动备份旧版本，便于回溯） */
@kotlinx.serialization.Serializable
@Entity(tableName = "cat_versions")
data class CatVersionEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val versionNo: Int,            // 同一猫咪内递增
    val snapshotJson: String,      // CatEntity 全量 JSON
    val changeSummary: String,     // 变更摘要（如：绝育+称重 3.2kg）
    val reason: String = "",       // 更新原因
    val adopterSummary: String = "", // 变更时关联领养人摘要（姓名 · 电话 · 状态）
    val createdAt: Long = System.currentTimeMillis(),
)

/** 猫咪亲属关系（母子/父子/同胞等，双向关联） */
@kotlinx.serialization.Serializable
@Entity(tableName = "cat_relations")
data class CatRelationEntity(
    @PrimaryKey val id: String,
    val catId: String,             // 主猫
    val relatedCatId: String,      // 关联猫
    val relationType: String,      // CatRelationType.name
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 领养人档案（产品文档 6.4：联系方式字段最小化但完整） */
@kotlinx.serialization.Serializable
@Entity(tableName = "adopters")
data class AdopterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val wechat: String = "",
    val qq: String = "",
    val email: String = "",
    val idCard: String = "",        // 身份证号（私密，仅本机）
    val address: String = "",
    val backupContact: String = "",      // 备用联系人
    val preferredContact: String = "",   // 沟通偏好
    val residence: String = "",          // 居住情况
    val petExperience: String = "",      // 养宠经验
    val familyMembers: String = "",      // 家庭成员
    val contactAuthorized: Boolean = false, // 联系与回访授权
    val authorizedAt: Long? = null,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 领养个案（产品文档 6.5 / 7.2 状态机） */
@kotlinx.serialization.Serializable
@Entity(tableName = "adoption_cases")
data class CaseEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val adopterId: String = "",
    val stage: String,             // CaseStage.name
    val source: String = "",
    val notes: String = "",
    val handoverDateEpochDay: Long? = null,
    val handoverPhotoUri: String = "",   // 交接现场照片
    val rejectReason: String = "",       // 拒绝原因（敏感，不进社区）
    val withdrawReason: String = "",     // 撤回原因
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 合同（产品文档 6.6 / 7.3） */
@kotlinx.serialization.Serializable
@Entity(tableName = "contracts")
data class ContractEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val status: String,            // ContractStatus.name
    val signedDateEpochDay: Long? = null,
    val fileHash: String = "",     // 已签原件哈希（初版记录文本摘要）
    val signaturePhotoUri: String = "",  // 签署照片
    val handoverPhotoUri: String = "",   // 交接现场照片
    val notes: String = "",
    val photoUris: String = "",        // 签署/交接照片（多张，逗号分隔）
    val createdAt: Long = System.currentTimeMillis(),
)

/** 回访记录（产品文档 6.7） */
@kotlinx.serialization.Serializable
@Entity(tableName = "follow_ups")
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val planDateEpochDay: Long,
    val status: String,            // FollowUpStatus.name
    val doneDateEpochDay: Long? = null,
    val method: String = "",
    val note: String = "",
    val riskLevel: Int = 0,        // 0 低 1 中 2 高
    val statusAtVisit: String = "", // 回访时猫咪状态：健康/好转/需注意/异常
    val createdAt: Long = System.currentTimeMillis(),
)

/** 时间线事件（状态变更不可删除，只追加） */
@kotlinx.serialization.Serializable
@Entity(tableName = "timeline_events")
data class TimelineEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val dateEpochDay: Long,
    val title: String,
    val detail: String = "",
    val kind: String,              // EventKind.name
    val createdAt: Long = System.currentTimeMillis(),
)

/** 待办（健康提醒、回访计划生成） */
@kotlinx.serialization.Serializable
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String = "",
    val dueEpochDay: Long? = null,
    val priority: String = Priority.NORMAL.name,
    val done: Boolean = false,
    val catId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 个案沟通/评估记录（咨询、家访、审核事件；产品文档 6.5） */
@kotlinx.serialization.Serializable
@Entity(tableName = "case_notes")
data class CaseNoteEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val noteDateEpochDay: Long,
    val type: String,   // 咨询/家访/审核/交接/其他
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 猫咪相册媒体（产品文档 6.2 媒体库；URI 指向本机私密文件） */
@kotlinx.serialization.Serializable
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val catId: String,
    val uri: String,
    val localUri: String = "",
    val remoteUrl: String = "",
    val serverMediaId: String = "",
    val syncState: String = "local",
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** E2EE 云同步：待上传变更队列（v5+） */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val id: String,           // UUID
    val entityType: String,               // "cat"/"adopter"/"case"/"todo"/"media"/...
    val entityId: String,                 // 业务实体 id
    val op: String,                       // "upsert"/"delete"
    val payloadJson: String,              // E2EE 加密后的完整密文（delete 时可为空）
    val payloadHash: String,              // 明文 SHA-256 摘要（用于去重与冲突检测）
    val revision: Long,                   // 本地单调 revision
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
)

@Entity(tableName = "sync_fingerprints", primaryKeys = ["entityType", "entityId"])
data class SyncFingerprintEntity(
    val entityType: String,
    val entityId: String,
    val payloadHash: String,
    val revision: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** E2EE 云同步：实体级同步游标（每实体最后成功同步的 revision） */
@Entity(tableName = "sync_revisions")
data class SyncRevisionEntity(
    @PrimaryKey val entityId: String,     // "cat:<id>" 形式，与 outbox.entityType/entityId 对应
    val lastLocalRevision: Long,
    val lastSyncedAt: Long = System.currentTimeMillis(),
)

/** E2EE 云同步：冲突记录（本地/远端密文快照，供冲突中心人工或规则解决） */
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val localHash: String,
    val remoteHash: String,
    val status: String,                   // "pending"/"resolved_local"/"resolved_remote"/"discarded"
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
)

/** E2EE 云同步：vault 密钥（单行；DEK 由 Keystore 派生的 KEK 包装） */
@Entity(tableName = "vault_keys")
data class VaultKeyEntity(
    @PrimaryKey val id: String = "vault",
    val kekVersion: Int,                  // KEK 派生版本（升级 Keystore 算法时递增）
    val wrappedDek: String,               // KEK 包装的 DEK（Base64）
    val dekSalt: String,                  // KEK 派生盐（Base64）
    val kekIterations: Int,               // PBKDF2 迭代数
    val recoveryCodeHash: String = "",    // 恢复码 HMAC（用于校验恢复码输入，可空=未设置）
    val recoveryCodeSalt: String = "",
    val wrappedDekRecovery: String? = null, // 恢复码 KEK 二次包装的 DEK（v6+；可空=未设置）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** E2EE 云同步：媒体 blob 元数据（密文文件与云端对象一一对应） */
@Entity(tableName = "sync_blobs")
data class SyncBlobEntity(
    @PrimaryKey val id: String,           // 逻辑路径 "media/<catId>/<fileId>.<ext>"
    val logicalPath: String,
    val sizeBytes: Long,
    val sha256: String,                   // 密文 SHA-256（完整性校验）
    val status: String,                   // "pending_upload"/"uploaded"/"pending_download"/"downloaded"
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 公开档案草稿（脱敏白名单副本，仅存允许公开的字段；v5+） */
@Entity(tableName = "public_drafts")
data class PublicDraftEntity(
    @PrimaryKey val id: String,           // = catId，一只猫一个草稿
    val catId: String,
    val publicNickname: String = "",
    val alias: String = "",
    val story: String = "",
    val gender: String = "",
    val ageBand: String = "",
    val coat: String = "",
    val personalityTags: String = "",
    val healthSummary: String = "",
    val weight: String = "",
    val dietRestrictions: String = "",
    val contactWithHumans: String = "",
    val contactWithCats: String = "",
    val catStatus: String = "",
    val statusReason: String = "",
    @ColumnInfo(defaultValue = "''") val archiveReason: String = "",
    val rescuedDateEpochDay: Long = 0,
    val statusDateEpochDay: Long? = null,
    val statusCode: String = "",          // 公开状态 code（如 adoptable/adopted/pending）
    val coverMediaId: String? = null,     // 脱敏封面（非原始 URI）
    val photoUrls: String = "",          // 明确选择公开的相册 URL（逗号分隔）
    val mediaUploadState: String? = null, // 本地媒体 ID -> 服务端媒体 ID/URL，用于弱网续传
    val cityCode: String? = null,         // v7+：城市 code（附近推荐用）
    val districtCode: String? = null,     // v7+：区县 code
    val publishState: String = "draft",   // draft/pending_review/published/suspended/archived
    val serverCatId: String? = null,      // 发布后服务端公开档案 id
    val updatedAt: Long = System.currentTimeMillis(),
)
