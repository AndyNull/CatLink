package com.catlink.app.debug

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.catlink.app.network.ApiEnvironment
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatButtonStyle
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object DeveloperMode {
    const val enabled: Boolean = true
}

@Composable
fun DeveloperServerScreen(
    context: Context,
    onBack: () -> Unit,
) {
    var endpoint by remember { mutableStateOf(ApiEnvironment.baseUrl(context)) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CatSpacing.pageHorizontal,
            end = CatSpacing.pageHorizontal,
            top = CatSpacing.pageTop,
            bottom = CatSpacing.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
    ) {
        item { SecondaryTopBar(title = "开发者服务", onBack = onBack) }
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CatSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CatSpacing.sm),
                ) {
                    Text(
                        "仅 Debug 构建",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "输入 Gin 反向代理域名或源站地址，不要填写 /api；App 会自动追加接口前缀。本机调试可将 8888 端口反向映射到模拟器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CatTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = "API 地址",
                        placeholder = ApiEnvironment.DEBUG_DEFAULT,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CatButton(
                        text = "保存并检查连接",
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val saved = ApiEnvironment.saveDebugBaseUrl(context, endpoint)
                            saved.onFailure { CatNoticeCenter.show(it.message ?: "API 地址无效", error = true) }
                            saved.onSuccess { base ->
                                checking = true
                                scope.launch {
                                    val result = checkHealth(base)
                                    checking = false
                                    CatNoticeCenter.show(
                                        result.fold(
                                            onSuccess = { "连接成功：$it" },
                                            onFailure = { it.message ?: "连接失败" },
                                        ),
                                        error = result.isFailure,
                                    )
                                }
                            }
                        },
                    )
                    CatButton(
                        text = "恢复本机默认地址",
                        style = CatButtonStyle.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ApiEnvironment.resetDebug(context)
                            endpoint = ApiEnvironment.DEBUG_DEFAULT
                            CatNoticeCenter.show("已恢复 ${ApiEnvironment.DEBUG_DEFAULT}")
                        },
                    )
                }
            }
        }
    }
}

private suspend fun checkHealth(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL("$baseUrl/api/v1/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4_000
            readTimeout = 4_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode == 200) { "服务返回 HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText().take(16 * 1024) }
            parseGinHealth(body)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseGinHealth(body: String): String {
    val root = Json.parseToJsonElement(body).jsonObject
    require(root["code"]?.jsonPrimitive?.contentOrNull == "0") { "健康检查响应无效" }
    require(root["data"]?.jsonPrimitive?.contentOrNull == "ok") { "健康检查响应无效" }
    return "Gin API"
}
