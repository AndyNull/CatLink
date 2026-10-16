package com.catlink.app.ui.screens.theme

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.theme.ThemeStyle
import kotlinx.coroutines.launch

/** 主题设置页：白色 / 奶油粉 / 夜间护眼（低频入口，独立页面） */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as CatLinkApplication).container.settings
    val scope = rememberCoroutineScope()
    val current by settings.themeStyle.collectAsState(initial = ThemeStyle.WHITE)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        ThemeStyle.entries.forEach { style ->
            item {
                ThemeOptionCard(
                    style = style,
                    selected = current == style,
                    onClick = { scope.launch { settings.setThemeStyle(style) } },
                )
            }
        }

        item {
            Text(
                text = "主题选择会立即生效并保存在本机。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(style: ThemeStyle, selected: Boolean, onClick: () -> Unit) {
    val (bg, card, primary) = previewColors(style)
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 预览缩略
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(1.dp, Color(0x22000000), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(card)
                        .border(1.dp, primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = styleDesc(style),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
        }
    }
}

@Composable
private fun previewColors(style: ThemeStyle): Triple<Color, Color, Color> = when (style) {
    ThemeStyle.WHITE -> Triple(Color(0xFFFCF9F2), Color(0xFFFFFFFF), Color(0xFF864E5A))
    ThemeStyle.PINK -> Triple(Color(0xFFFFF7F9), Color(0xFFFFFFFF), Color(0xFFB02B5C))
    ThemeStyle.DARK -> Triple(Color(0xFF171214), Color(0xFF2E272A), Color(0xFFFFB1C8))
}

private fun styleDesc(style: ThemeStyle): String = when (style) {
    ThemeStyle.WHITE -> "暖白纸感 · 莓果粉主色"
    ThemeStyle.PINK -> "奶油粉底 · 莓果粉主色"
    ThemeStyle.DARK -> "暖深灰 · 浅粉 · 护眼"
}
