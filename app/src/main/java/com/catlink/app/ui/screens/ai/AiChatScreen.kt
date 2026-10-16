package com.catlink.app.ui.screens.ai

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.ChatMessage
import com.catlink.app.data.SettingsStore
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.catFieldColors
import com.catlink.app.ui.theme.CatLinkThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 系统人设：救助助理 */
private const val SYSTEM_PROMPT =
    "你是 CatLink 救助站助手，正在帮助一位流浪动物救助人。你可以：1) 给出猫咪养护、\n" +
    "疾病观察与就医建议；2) 帮忙撰写领养信息、救助故事文案；3) 梳理救助流程与回访提醒。\n" +
    "回答简洁、温暖、务实，多用短句，必要时分点列出。"

/**
 * AI 对话：支持新开会话 / 历史会话记录 / 快捷键全选，
 * 调用用户配置的 DeepSeek（或兼容 OpenAI 协议）接口。
 */
@Composable
fun AiChatScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as CatLinkApplication).container.settings
    val scope = rememberCoroutineScope()
    val sessions by settings.aiSessions.collectAsState(initial = emptyList())
    val aiConfig by settings.aiConfig.collectAsState(initial = SettingsStore.AiConfig())
    val networkAvailable by rememberAiNetworkAvailable()

    // 当前会话：id 为 null 表示尚未持久化的新会话
    var sessionId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun toast(msg: String) {
        // callAi 的 onError 回调可能在 IO 线程，切回主线程再弹 Toast
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 保存当前会话到历史（有新消息才存）
    fun saveCurrent() {
        if (messages.isEmpty()) return
        val now = System.currentTimeMillis()
        val title = messages.firstOrNull { it.role == "user" }?.content?.take(12) ?: "新会话"
        val s = SettingsStore.AiSession(
            id = sessionId ?: UUID.randomUUID().toString(),
            title = title,
            messages = messages,
            createdAt = sessionId?.let { id -> sessions.find { it.id == id }?.createdAt } ?: now,
            updatedAt = now,
        )
        sessionId = s.id
        val rest = sessions.filter { it.id != s.id }
        scope.launch { settings.saveAiSessions(listOf(s) + rest) }
    }

    // 新开会话：保存当前，清空
    fun newSession() {
        saveCurrent()
        sessionId = null
        messages = emptyList()
        inputValue = TextFieldValue("")
    }

    // 载入历史会话
    fun loadSession(s: SettingsStore.AiSession) {
        sessionId = s.id
        messages = s.messages
        showHistory = false
        scope.launch { listState.scrollToItem(0) }
    }

    // 发送一条消息（输入框与快捷提问共用）
    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || loading) return
        inputValue = TextFieldValue("")
        messages = messages + ChatMessage("user", text)
        loading = true
        scope.launch {
            val reply = callAi(settings, messages) { err -> toast(err) }
            loading = false
            if (reply != null) {
                messages = messages + ChatMessage("assistant", reply)
            } else {
                toast("没有收到回复，请检查 AI 设置")
            }
            saveCurrent()
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size + 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        AiChatHeader(
            state = aiHeaderState(
                model = aiConfig.model,
                configured = aiConfig.apiKey.isNotBlank(),
                networkAvailable = networkAvailable,
                loading = loading,
                sessionTitle = sessionId?.let { id -> sessions.firstOrNull { it.id == id }?.title },
                messageCount = messages.size,
            ),
            onOpenHistory = { showHistory = true },
            onNewSession = { newSession() },
            onOpenSettings = onOpenSettings,
        )

        // 消息列表
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🐾", fontSize = 30.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "我是救助小助手",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "可以问我猫咪养护、领养文案、救助流程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                    Text(
                        text = "也可以聊聊今天遇到的毛孩子",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    listOf(
                        "✍️ 写一段领养文案",
                        "🍼 幼猫喂养要注意什么？",
                        "🏥 怎么判断猫是否生病？",
                    ).forEach { q ->
                        Text(
                            text = q,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .clickable { send(q) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
                if (loading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "思考中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 输入栏（紧凑胶囊，支持 Ctrl+A 全选）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                placeholder = {
                    Text(
                        "问问救助小助手…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .onPreviewKeyEvent { event ->
                        // Ctrl+A 全选
                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.A) {
                            val tv = inputValue
                            inputValue = tv.copy(selection = TextRange(0, tv.text.length))
                            true
                        } else {
                            false
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                colors = catFieldColors(),
            )
            Spacer(Modifier.size(8.dp))
            IconButton(
                onClick = { send(inputValue.text) },
                enabled = inputValue.text.isNotBlank() && !loading,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputValue.text.isNotBlank() && !loading) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
            ) {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = "发送",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    // 历史会话弹窗
    if (showHistory) {
        CatDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("历史会话") },
            text = {
                if (sessions.isEmpty()) {
                    Text(
                        "还没有历史会话，和 AI 聊几句就有了。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(sessions) { s ->
                            val time = remember(s.updatedAt) {
                                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(s.updatedAt))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { loadSession(s) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${s.messages.size} 条消息 · $time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { settings.saveAiSessions(sessions.filter { it.id != s.id }) }
                                    },
                                ) {
                                    Text(
                                        "删除",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("关闭") }
            },
        )
    }
}

/** 对话气泡：AI 白色带边框 + 🐾 头像；用户粉色渐变 */
@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(CatLinkThemeColors.extras.accentPink.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🐾", fontSize = 14.sp)
            }
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .then(
                    if (isUser) {
                        Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFFFFB9C9), Color(0xFFFFD1DB)))
                        )
                    } else {
                        Modifier.background(Color(0xFFFFFFFF))
                    }
                )
                .border(
                    width = if (isUser) 0.dp else 1.dp,
                    color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = if (isUser) Color(0xFF4A2A33) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 调用兼容 OpenAI 协议的聊天接口（默认 DeepSeek）。
 * 返回回复文本；失败返回 null 并通过 error 回调给出原因。
 */
@SuppressLint("BlockingMethodInNonBlockingContext")
private suspend fun callAi(
    settings: SettingsStore,
    history: List<ChatMessage>,
    onError: (String) -> Unit = {},
): String? = withContext(Dispatchers.IO) {
    val cfg = settings.aiConfig.first()
    if (cfg.apiKey.isBlank()) {
        onError("请先在「我的 → AI 设置」填写 API Key")
        return@withContext null
    }
    try {
        val url = URL(cfg.endpoint.trimEnd('/') + "/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.doOutput = true

        val body = JSONObject().apply {
            put("model", cfg.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
                history.forEach { put(JSONObject().apply { put("role", it.role); put("content", it.content) }) }
            })
            put("stream", false)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()

        if (code in 200..299) {
            val json = JSONObject(resp)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } else {
            val msg = try {
                JSONObject(resp).getJSONObject("error").getString("message")
            } catch (_: Exception) {
                "HTTP $code"
            }
            onError(msg)
            null
        }
    } catch (e: Exception) {
        onError(e.message ?: "网络请求失败")
        null
    }
}

/** 拉取厂商模型列表（GET {endpoint}/models，OpenAI 兼容） */
@SuppressLint("BlockingMethodInNonBlockingContext")
suspend fun fetchModelList(
    endpoint: String,
    apiKey: String,
): Result<List<String>> = withContext(Dispatchers.IO) {
    try {
        val url = URL(endpoint.trimEnd('/') + "/models")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code in 200..299) {
            val json = JSONObject(resp)
            val data = json.getJSONArray("data")
            val models = (0 until data.length()).map { data.getJSONObject(it).getString("id") }
            if (models.isEmpty()) Result.failure(Exception("接口没有返回模型")) else Result.success(models)
        } else {
            val msg = try {
                JSONObject(resp).getJSONObject("error").getString("message")
            } catch (_: Exception) {
                "HTTP $code"
            }
            Result.failure(Exception(msg))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
