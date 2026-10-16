package com.catlink.app.ui.screens.lock

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlink.app.CatLinkApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 应用锁：PIN 验证（保护敏感档案） */
@Composable
fun LockScreen(onUnlock: () -> Unit, onWrongPin: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var failCount by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableStateOf(0L) }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (System.currentTimeMillis() < lockedUntil) {
                // 锁定期内：清空输入并提示
                pin = ""
                return@LaunchedEffect
            }
            val ok = app.container.settings.verifyPin(pin)
            if (ok) {
                onUnlock()
            } else {
                failCount++
                error = true
                pin = ""
                if (failCount >= 5) {
                    lockedUntil = System.currentTimeMillis() + 30_000
                    failCount = 0
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "CatLink",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "输入 PIN 解锁",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        // PIN 圆点
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        if (error) {
            Text(
                text = if (System.currentTimeMillis() < lockedUntil) "错误次数过多，请 30 秒后再试" else "PIN 不正确，请重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }

        // 数字键盘
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { d -> Key(d) { if (pin.length < 4) pin += d } }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(64.dp))
                Key("0") { if (pin.length < 4) pin += "0" }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { pin = pin.dropLast(1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Backspace,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
