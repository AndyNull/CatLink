package com.catlink.app.ui.screens.ai

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.SettingsStore
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.catFieldColors
import com.catlink.app.ui.theme.CatLinkThemeColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AI 接口设置：
 * - 接口厂商下拉（内置 DeepSeek/OpenAI/通义千问/Kimi/智谱/火山方舟/自定义）
 * - API Key 自主填写
 * - 模型：可手动填写，也可点「获取」用 Key 实时拉取厂商模型列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as CatLinkApplication).container.settings
    val scope = rememberCoroutineScope()
    val cfg by settings.aiConfig.collectAsState(initial = SettingsStore.AiConfig())
    var saved by remember { mutableStateOf(false) }

    var apiKey by remember { mutableStateOf(cfg.apiKey) }
    var model by remember { mutableStateOf(cfg.model) }
    var endpoint by remember { mutableStateOf(cfg.endpoint) }

    // 厂商下拉
    var showProviders by remember { mutableStateOf(false) }
    val providerName = SettingsStore.AI_PROVIDERS.firstOrNull { it.endpoint == endpoint.trim() }?.name ?: "自定义"

    // 模型获取
    var showModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.size(6.dp))
            Text(
                text = "AI 设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "选择厂商自动填充接口，填入 API Key 后可点「获取」拉取模型列表；也可以全部手动填写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))

        // 厂商下拉（M3 ExposedDropdownMenuBox，menuAnchor 正确处理字段点击）
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "接口厂商",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
            ExposedDropdownMenuBox(
                expanded = showProviders,
                onExpandedChange = { showProviders = it },
            ) {
                OutlinedTextField(
                    value = providerName,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviders) },
                    colors = catFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .menuAnchor(),
                )
                DropdownMenu(
                    expanded = showProviders,
                    onDismissRequest = { showProviders = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 10.dp,
                ) {
                    SettingsStore.AI_PROVIDERS.forEach { p ->
                        val selected = p.endpoint == endpoint.trim()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                endpoint = p.endpoint
                                showProviders = false
                                saved = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("自定义", style = MaterialTheme.typography.bodyMedium) },
                        onClick = { showProviders = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 接口地址
        CatTextField(
            value = endpoint,
            onValueChange = { endpoint = it; saved = false },
            label = "接口地址",
            placeholder = "https://api.deepseek.com",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // API Key
        CatTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            label = "API Key",
            placeholder = "sk-…",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // 模型 + 获取按钮（底部对齐 + 同高，确保文字同中线）
        Row(verticalAlignment = Alignment.Bottom) {
            Box(modifier = Modifier.weight(1f)) {
                CatTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = "模型",
                    placeholder = "deepseek-chat",
                    modifier = Modifier.fillMaxWidth(),
                )
                // 获取到的模型列表
                DropdownMenu(
                    expanded = showModels,
                    onDismissRequest = { showModels = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 10.dp,
                ) {
                    fetchedModels.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                model = m
                                showModels = false
                                saved = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = {
                    if (apiKey.isBlank()) {
                        toast("请先填写 API Key")
                        return@OutlinedButton
                    }
                    fetching = true
                    scope.launch {
                        val r = fetchModelList(endpoint, apiKey)
                        fetching = false
                        r.onSuccess { models ->
                            fetchedModels = models
                            toast("获取到 ${models.size} 个模型")
                            showModels = true
                        }.onFailure { e ->
                            toast("获取失败：${e.message}")
                        }
                    }
                },
                enabled = !fetching,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(54.dp),
            ) {
                if (fetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(4.dp))
                Text("获取", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "提示：API Key 仅保存在本机，不会上传。DeepSeek 需先在 platform.deepseek.com 申请。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    settings.resetAiConfig()
                    val c = settings.aiConfig.first()
                    apiKey = c.apiKey
                    model = c.model
                    endpoint = c.endpoint
                    saved = false
                    toast("已恢复 DeepSeek 默认")
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("恢复 DeepSeek 默认（自动拉取接口与模型）", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    settings.setAiConfig(endpoint, apiKey, model)
                    saved = true
                    toast("已保存 ✓")
                }
            },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("保存", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))
    }
}
