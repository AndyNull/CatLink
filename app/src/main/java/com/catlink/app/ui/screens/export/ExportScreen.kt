package com.catlink.app.ui.screens.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.BuildConfig
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.ExportManager
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as CatLinkApplication).container
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val stamp = remember { DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            working = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val exported = ExportManager.exportPortable(
                context = context,
                database = container.database,
                settings = container.settings,
                authSession = container.authSession,
                publicApi = container.publicApi,
                uri = uri,
                appVersion = BuildConfig.VERSION_NAME,
            )
            working = false
            if (exported.ok) {
                result = exported.message
                CatNoticeCenter.show("导出完成")
            } else {
                CatNoticeCenter.show(exported.message, error = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CatSpacing.pageHorizontal),
    ) {
        SecondaryTopBar(title = "导出个人数据", onBack = onBack)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "生成完整可移植 ZIP，并通过系统文件选择器保存。登录后会同时读取服务端账号数据；归档不加密，请妥善保管。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "个人数据可移植归档",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "包含本机 JSON、逐表 CSV、HTML 摘要、档案与合同媒体；登录后还包含服务端社区、消息、通知和领养记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "API Key、密码、Token、同步密钥和恢复口令不会写入归档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                CatButton(
                    text = if (working) "导出中…" else "选择位置并导出 ZIP",
                    onClick = {
                        if (!working) {
                            working = true
                            result = ""
                            launcher.launch("youmaoni_personal_data_$stamp.zip")
                        }
                    },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (result.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "✓ $result",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
