package com.catlink.app.ui.screens.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class AiConnectionState { REQUESTING, READY, OFFLINE, UNCONFIGURED }

internal data class AiHeaderState(
    val connection: AiConnectionState,
    val connectionLabel: String,
    val modelLabel: String,
    val sessionLabel: String,
)

internal fun aiHeaderState(
    model: String,
    configured: Boolean,
    networkAvailable: Boolean,
    loading: Boolean,
    sessionTitle: String?,
    messageCount: Int,
): AiHeaderState {
    val connection = when {
        loading -> AiConnectionState.REQUESTING
        !configured -> AiConnectionState.UNCONFIGURED
        !networkAvailable -> AiConnectionState.OFFLINE
        else -> AiConnectionState.READY
    }
    return AiHeaderState(
        connection = connection,
        connectionLabel = when (connection) {
            AiConnectionState.REQUESTING -> "请求中"
            AiConnectionState.READY -> "联网可用"
            AiConnectionState.OFFLINE -> "当前离线"
            AiConnectionState.UNCONFIGURED -> "待配置"
        },
        modelLabel = model.trim().ifBlank { "未选择模型" },
        sessionLabel = sessionTitle?.trim()?.takeIf(String::isNotEmpty)
            ?: if (messageCount == 0) "新会话" else "$messageCount 条消息",
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AiChatHeader(
    state: AiHeaderState,
    onOpenHistory: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AI 助手",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            HeaderAction(Icons.Outlined.History, "历史会话", onOpenHistory)
            HeaderAction(Icons.Outlined.AddComment, "新开会话", onNewSession)
            HeaderAction(Icons.Outlined.Settings, "AI 设置", onOpenSettings)
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HeaderStatus(state.connectionLabel, connectionColor(state.connection))
            HeaderStatus("模型 · ${state.modelLabel}", MaterialTheme.colorScheme.onSurfaceVariant, 190.dp)
            HeaderStatus("会话 · ${state.sessionLabel}", MaterialTheme.colorScheme.onSurfaceVariant, 150.dp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeaderStatus(label: String, color: Color, maxWidth: androidx.compose.ui.unit.Dp = 120.dp) {
    Row(
        modifier = Modifier.widthIn(max = maxWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun connectionColor(state: AiConnectionState): Color = when (state) {
    AiConnectionState.REQUESTING -> MaterialTheme.colorScheme.tertiary
    AiConnectionState.READY -> MaterialTheme.colorScheme.primary
    AiConnectionState.OFFLINE -> MaterialTheme.colorScheme.error
    AiConnectionState.UNCONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun rememberAiNetworkAvailable(): State<Boolean> {
    val context = LocalContext.current
    val available = remember(context) { mutableStateOf(context.hasValidatedNetwork()) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.value = context.hasValidatedNetwork()
            }

            override fun onLost(network: Network) {
                available.value = context.hasValidatedNetwork()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                available.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return available
}

private fun Context.hasValidatedNetwork(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
