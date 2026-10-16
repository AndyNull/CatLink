package com.catlink.app.ui.screens.casedetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.CaseStage
import com.catlink.app.data.ContractStatus
import com.catlink.app.data.FollowUpStatus
import com.catlink.app.data.db.CaseEntity
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.ContractEntity
import com.catlink.app.data.db.FollowUpEntity
import com.catlink.app.ui.components.CatAvatar
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatDateField
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SectionHeader
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.components.StatusChip
import com.catlink.app.ui.theme.CatLinkThemeColors
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("M月d日")

/** 领养个案详情：阶段状态机 + 合同 + 回访（产品文档 7.2 / 7.3 / 6.7） */
@Composable
fun CaseDetailScreen(caseId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as CatLinkApplication).container.repository
    val scope = rememberCoroutineScope()

    var case by remember { mutableStateOf<CaseEntity?>(null) }
    var cat by remember { mutableStateOf<CatEntity?>(null) }
    var contracts by remember { mutableStateOf<List<ContractEntity>>(emptyList()) }
    var followUps by remember { mutableStateOf<List<FollowUpEntity>>(emptyList()) }
    var caseNotes by remember { mutableStateOf<List<com.catlink.app.data.db.CaseNoteEntity>>(emptyList()) }
    var showContractDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var completingFollowUp by remember { mutableStateOf<FollowUpEntity?>(null) }
    var signingContract by remember { mutableStateOf<ContractEntity?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var voidingContract by remember { mutableStateOf<ContractEntity?>(null) }
    var showDeleteCaseDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 交接现场照片（TakePicture）
    val handoverPhotoUri = remember { mutableStateOf<Uri?>(null) }
    val takeHandoverPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val uri = handoverPhotoUri.value
            if (uri != null && caseId.isNotBlank()) {
                scope.launch { repo.updateCaseHandoverPhoto(caseId, uri.toString()) }
            }
        }
    }

    LaunchedEffect(caseId) {
        val dao = (context.applicationContext as CatLinkApplication).container.dao
        launch { dao.observeCaseById(caseId).collect { case = it } }
        launch { repo.observeContracts(caseId).collect { contracts = it } }
        launch { repo.observeFollowUps(caseId).collect { followUps = it } }
        launch { repo.observeCaseNotes(caseId).collect { caseNotes = it } }
    }
    LaunchedEffect(case?.catId) {
        case?.catId?.let { cid ->
            launch { repo.observeCat(cid).collect { cat = it } }
        }
    }

    if (case == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val c = case!!
    val stage = CaseStage.fromName(c.stage)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(6.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "领养个案",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = cat?.nickname ?: "关联猫咪",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusChip(
                    text = stage.label,
                    container = if (stage == CaseStage.FOLLOW_UP || stage == CaseStage.CLOSED) CatLinkThemeColors.extras.mint
                    else MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除个案",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showDeleteCaseDialog = true }
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
        }

        // 流程操作区（推进/拒绝/撤回）
        item {
            CaseFlowActions(
                stage = stage,
                hasAdopter = c.adopterId.isNotBlank(),
                hasPreparedContract = contracts.any { it.status == ContractStatus.PREPARED.name },
                onAdvance = {
                    if (stage == CaseStage.APPLIED && c.adopterId.isBlank()) {
                        toast("请先关联领养人（点顶部领养人卡片选择）")
                        return@CaseFlowActions
                    }
                    if (stage == CaseStage.CONTRACT && !contracts.any { it.status == ContractStatus.PREPARED.name }) {
                        toast("请先添加并准备好合同（待签署状态）")
                        return@CaseFlowActions
                    }
                    scope.launch { repo.advanceCase(caseId) }
                },
                onReject = { showRejectDialog = true },
                onWithdraw = { showWithdrawDialog = true },
            )
        }

        // 沟通与评估记录
        item {
            SectionHeader(title = "沟通记录", actionText = "添加", onAction = { showNoteDialog = true })
        }
        if (caseNotes.isEmpty()) {
            item {
                Text(
                    text = "记录与领养人的沟通、家访情况。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(caseNotes.size) { index ->
                CaseNoteRow(note = caseNotes[index])
            }
        }

        // 交接现场照片
        item {
            HandoverPhotoCard(
                photoUri = c.handoverPhotoUri.ifBlank { null },
                onTakePhoto = {
                    val context2 = context
                    val dir = File(context2.filesDir, "catlink_media").apply { mkdirs() }
                    val file = File(dir, "handover_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context2, "${context2.packageName}.fileprovider", file)
                    handoverPhotoUri.value = uri
                    takeHandoverPhoto.launch(uri)
                },
            )
        }

        // 合同
        item {
            SectionHeader(title = "合同", actionText = "添加合同", onAction = { showContractDialog = true })
        }
        if (contracts.isEmpty()) {
            item {
                Text(
                    text = "暂无合同记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(contracts.size) { index ->
                ContractRow(
                    contract = contracts[index],
                    onSign = { signingContract = contracts[index] },
                    onVoid = { voidingContract = contracts[index] },
                )
            }
        }

        // 回访
        item {
            SectionHeader(title = "回访计划", actionText = "添加", onAction = { showFollowUpDialog = true })
        }
        if (followUps.isEmpty()) {
            item {
                Text(
                    text = "暂无回访记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(followUps.size) { index ->
                FollowUpRow(
                    followUp = followUps[index],
                    onComplete = {
                        if (followUps[index].status == FollowUpStatus.PENDING.name) {
                            completingFollowUp = followUps[index]
                        }
                    },
                )
            }
        }
    }

    if (showContractDialog) {
        ContractDialog(
            onDismiss = { showContractDialog = false },
            onConfirm = { notes ->
                scope.launch {
                    repo.prepareContract(caseId, notes)
                    showContractDialog = false
                }
            },
        )
    }
    if (showFollowUpDialog) {
        FollowUpDialog(
            onDismiss = { showFollowUpDialog = false },
            onConfirm = { date, method ->
                scope.launch {
                    repo.addFollowUp(caseId, date, method)
                    showFollowUpDialog = false
                }
            },
        )
    }
    completingFollowUp?.let { f ->
        CompleteFollowUpDialog(
            onDismiss = { completingFollowUp = null },
            onConfirm = { method, note, risk, statusAtVisit, doneDate ->
                scope.launch {
                    repo.completeFollowUp(f, method, note, risk, statusAtVisit, doneDate)
                    completingFollowUp = null
                }
            },
        )
    }
    signingContract?.let { contract ->
        SignContractDialog(
            onDismiss = { signingContract = null },
            onConfirm = { hash, signatureUri ->
                scope.launch {
                    repo.signContract(contract.id, hash, signatureUri)
                    signingContract = null
                }
            },
        )
    }
    if (showNoteDialog) {
        CaseNoteDialog(
            onDismiss = { showNoteDialog = false },
            onConfirm = { type, content ->
                scope.launch {
                    repo.addCaseNote(caseId, type, content)
                    showNoteDialog = false
                }
            },
        )
    }
    voidingContract?.let { contract ->
        ReasonDialog(
            title = "作废合同",
            hint = "作废后原合同不可再签署；可准备补充协议或新版本。",
            confirmText = "确认作废",
            onDismiss = { voidingContract = null },
            onConfirm = { reason ->
                scope.launch {
                    repo.voidContract(contract.id, reason)
                    voidingContract = null
                }
            },
        )
    }
    if (showDeleteCaseDialog) {
        CatDialog(
            onDismissRequest = { showDeleteCaseDialog = false },
            title = { Text("删除这个个案？") },
            text = { Text("将删除该个案的合同、回访与沟通记录，猫咪档案不受影响。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteCase(caseId)
                        showDeleteCaseDialog = false
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCaseDialog = false }) { Text("取消") }
            },
        )
    }
    if (showRejectDialog) {
        ReasonDialog(
            title = "拒绝这个领养申请？",
            hint = "个案将进入「已拒绝」，猫咪回到待领养状态。",
            confirmText = "拒绝",
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason ->
                scope.launch {
                    repo.rejectCase(caseId, reason.ifBlank { "审核未通过" })
                    showRejectDialog = false
                    toast("已拒绝该申请")
                }
            },
        )
    }
    if (showWithdrawDialog) {
        ReasonDialog(
            title = "撤回这个领养申请？",
            hint = "个案将进入「已撤回」，猫咪回到待领养状态。",
            confirmText = "撤回",
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { reason ->
                scope.launch {
                    repo.withdrawCase(caseId, reason.ifBlank { "候选人撤回" })
                    showWithdrawDialog = false
                    toast("已撤回该申请")
                }
            },
        )
    }
}

/** 流程操作区：推进阶段 + 拒绝/撤回 */
@Composable
private fun CaseFlowActions(
    stage: CaseStage,
    hasAdopter: Boolean,
    hasPreparedContract: Boolean,
    onAdvance: () -> Unit,
    onReject: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val active = stage == CaseStage.APPLIED || stage == CaseStage.SCREENING ||
        stage == CaseStage.CONTRACT || stage == CaseStage.HANDOVER || stage == CaseStage.FOLLOW_UP
    if (!active) return
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val nextLabel = when (stage) {
                CaseStage.APPLIED -> "进入筛选（需先关联领养人）"
                CaseStage.SCREENING -> "进入签约"
                CaseStage.CONTRACT -> "进入交接（需待签署合同）"
                CaseStage.HANDOVER -> "完成交接，开始回访"
                CaseStage.FOLLOW_UP -> "关闭个案"
                else -> ""
            }
            val nextHint = when (stage) {
                CaseStage.APPLIED -> if (!hasAdopter) "⚠ 尚未关联领养人，需先在顶部选择" else "将进入「筛选」阶段"
                CaseStage.CONTRACT -> if (!hasPreparedContract) "⚠ 尚未准备合同，需先添加待签署合同" else "将进入「交接」阶段"
                else -> "将自动更新猫咪状态与待办"
            }
            Text(
                text = "个案进度",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$nextLabel · $nextHint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAdvance,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("推进", style = MaterialTheme.typography.labelMedium)
                }
                if (stage != CaseStage.FOLLOW_UP) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("拒绝", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = onWithdraw,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("撤回", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 沟通记录行 */
@Composable
private fun CaseNoteRow(note: com.catlink.app.data.db.CaseNoteEntity) {
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StatusChip(
                text = note.type,
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = LocalDate.ofEpochDay(note.noteDateEpochDay).format(dateFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 添加沟通记录 */
@Composable
private fun CaseNoteDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var type by remember { mutableStateOf("咨询") }
    var content by remember { mutableStateOf("") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加沟通记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("咨询", "家访", "其他").forEach { t ->
                        StatusChip(
                            text = t,
                            container = if (type == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (type == t) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { type = t },
                        )
                    }
                }
                CatTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "内容",
                    placeholder = "如：电话沟通 15 分钟，对方养猫经验丰富",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = { onConfirm(type, content.trim()) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 交接日期设置 */
/** 通用原因输入对话框（拒绝/撤回/作废） */
@Composable
private fun ReasonDialog(
    title: String,
    hint: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CatTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "原因（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 交接现场照片卡 */
@Composable
private fun HandoverPhotoCard(photoUri: String?, onTakePhoto: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "现场照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (photoUri != null) "现场照片" else "拍摄现场照片",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (photoUri != null) "已记录（仅本机保存）" else "记录猫咪送养/交接的现场情况",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTakePhoto) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (photoUri != null) "重拍" else "拍摄", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 合同签署：输入原件哈希（可选），拍摄签署照片 */
@Composable
private fun SignContractDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val context = LocalContext.current
    var hash by remember { mutableStateOf("") }
    var signatureUri by remember { mutableStateOf("") }
    val signatureUriState = remember { mutableStateOf<Uri?>(null) }
    val takeSignature = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) signatureUriState.value?.let { signatureUri = it.toString() }
    }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("签署合同") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "签署后合同快照不可直接编辑，修订需新建补充协议或新版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CatTextField(
                    value = hash,
                    onValueChange = { hash = it },
                    label = "原件哈希（可选）",
                    placeholder = "如已扫描合同文件，可记录其哈希",
                    modifier = Modifier.fillMaxWidth(),
                )
                // 签名照
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (signatureUri.isNotBlank()) {
                            AsyncImage(
                                model = signatureUri,
                                contentDescription = "签署照片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = {
                        val dir = File(context.filesDir, "catlink_media").apply { mkdirs() }
                        val file = File(dir, "signature_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        signatureUriState.value = uri
                        takeSignature.launch(uri)
                    }) {
                        Text(if (signatureUri.isNotBlank()) "重拍签署照片" else "拍摄签署照片", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hash, signatureUri) }) { Text("确认签署") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun OutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(42.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ContractRow(contract: ContractEntity, onSign: () -> Unit, onVoid: () -> Unit = {}) {
    val status = ContractStatus.fromName(contract.status)
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "领养合同 ${if (contract.notes.isNotBlank()) "· ${contract.notes}" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (status) {
                        ContractStatus.SIGNED -> "已签署 · 原件哈希已记录"
                        ContractStatus.PREPARED -> "待签署"
                        else -> status.label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status == ContractStatus.PREPARED) {
                TextButton(onClick = onSign) {
                    Text("签署", style = MaterialTheme.typography.labelMedium)
                }
            } else if (status == ContractStatus.SIGNED) {
                StatusChip(
                    text = "已签署",
                    container = CatLinkThemeColors.extras.mint,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = onVoid) {
                    Text("作废", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            } else {
                StatusChip(
                    text = status.label,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FollowUpRow(followUp: FollowUpEntity, onComplete: () -> Unit) {
    val status = FollowUpStatus.fromName(followUp.status)
    val plan = LocalDate.ofEpochDay(followUp.planDateEpochDay)
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (status == FollowUpStatus.DONE) CatLinkThemeColors.extras.mint else MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventNote,
                    contentDescription = null,
                    tint = if (status == FollowUpStatus.DONE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${plan.format(dateFmt)} · ${followUp.method}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (followUp.note.isNotBlank()) {
                    Text(
                        text = followUp.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status == FollowUpStatus.PENDING) {
                TextButton(onClick = onComplete) {
                    Text("完成", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                StatusChip(
                    text = "已完成",
                    container = CatLinkThemeColors.extras.mint,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ContractDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加合同") },
        text = {
            Column {
                Text(
                    text = "将生成一份合同填写快照并绑定到本个案。签署后正文不可直接编辑，修改需创建补充协议或新版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CatTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "合同备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notes) }) { Text("生成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FollowUpDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, String) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var method by remember { mutableStateOf("电话") }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加回访计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatDateField(
                    value = date,
                    onDateChange = { date = it },
                    label = "计划日期 *",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = method,
                    onValueChange = { method = it },
                    label = "回访方式",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date, method) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CompleteFollowUpDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String, LocalDate) -> Unit,
) {
    var method by remember { mutableStateOf("电话") }
    var note by remember { mutableStateOf("") }
    var risk by remember { mutableStateOf(0) }
    var statusAtVisit by remember { mutableStateOf("健康") }
    var doneDate by remember { mutableStateOf(LocalDate.now()) }
    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("完成回访") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatDateField(
                    value = doneDate,
                    onDateChange = { doneDate = it },
                    label = "回访日期",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatTextField(
                    value = method,
                    onValueChange = { method = it },
                    label = "实际方式",
                    modifier = Modifier.fillMaxWidth(),
                )
                // 记录猫咪当前状态
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("猫咪状态：", style = MaterialTheme.typography.labelMedium)
                    listOf("健康", "好转", "需注意", "异常").forEach { s ->
                        StatusChip(
                            text = s,
                            container = if (statusAtVisit == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (statusAtVisit == s) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { statusAtVisit = s },
                        )
                    }
                }
                CatTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "观察内容",
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("风险：", style = MaterialTheme.typography.labelMedium)
                    listOf(0 to "低", 1 to "中", 2 to "高").forEach { (v, label) ->
                        StatusChip(
                            text = label,
                            container = if (risk == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (risk == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { risk = v },
                        )
                    }
                }
                if (risk >= 2) {
                    Text(
                        text = "高风险回访将自动提升首页待办优先级。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(method, note, risk, statusAtVisit, doneDate) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
