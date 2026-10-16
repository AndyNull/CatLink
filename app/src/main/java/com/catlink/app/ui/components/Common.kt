package com.catlink.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catlink.app.ui.theme.CatLinkThemeColors
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing

/**
 * 软卡片：白色表面 + 柔和暖色阴影（参考 DESIGN.md "Tinted Depth"）。
 * 参考实现为 0 4px 20px -4px 极淡光晕，故压低 elevation 并使用带 alpha 的暖色阴影。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(CatRadii.card),
    content: @Composable () -> Unit,
) {
    val shadowColor = CatLinkThemeColors.extras.warmShadow
    val scheme = MaterialTheme.colorScheme
    val borderColor = scheme.outlineVariant
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .shadow(
                elevation = if (pressed) 1.dp else 2.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            .clip(shape)
            .background(if (pressed) scheme.surfaceContainerLow else scheme.surfaceContainerLowest)
            .border(CatSpacing.hairline, borderColor.copy(alpha = 0.72f), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            ),
    ) {
        content()
    }
}

/** 区块标题：标题 + 右侧"查看全部" */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(CatRadii.compact))
                    .clickable(enabled = onAction != null, role = androidx.compose.ui.semantics.Role.Button) { onAction?.invoke() }
                    .padding(horizontal = CatSpacing.xs),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 状态胶囊标签：参考为 12px/600 + 半透明底色的小标签 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CatRadii.pill))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/**
 * 状态标签只使用三组语义色：处理中 / 正向推进 / 结束或异常。
 * 状态差异由文字承担，颜色只表达大类，避免十种色相破坏品牌统一性。
 */
@Composable
fun catStatusColors(status: com.catlink.app.data.CatStatus): Pair<Color, Color> {
    val extras = CatLinkThemeColors.extras
    return when (status) {
        com.catlink.app.data.CatStatus.RESCUE,
        com.catlink.app.data.CatStatus.TREATING,
        -> extras.warm to extras.onWarm

        com.catlink.app.data.CatStatus.ADOPTABLE,
        com.catlink.app.data.CatStatus.ADOPTED,
        -> extras.mint to extras.onMint

        com.catlink.app.data.CatStatus.ARCHIVED,
        -> extras.neutral to extras.onNeutral
    }
}

/**
 * 猫猫头像占位：柔和色块 + 手绘风猫脸剪影。
 * 后续接入真实照片（MediaAsset）后替换。
 */
@Composable
fun CatAvatar(
    index: Int,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    coverUri: String? = null,
) {
    if (!coverUri.isNullOrBlank()) {
        coil.compose.AsyncImage(
            model = coverUri,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
        return
    }
    val extras = CatLinkThemeColors.extras
    val coverPalette = listOf(extras.accentPink, extras.mint, extras.warm)
    val inkPalette = listOf(extras.onAccentPink, extras.onMint, extras.onWarm)
    val bg = coverPalette[index % coverPalette.size]
    val ink = inkPalette[index % inkPalette.size]
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.55f
            val headR = w * 0.32f
            val strokeW = w * 0.032f
            val ear = Path().apply {
                // 左耳
                moveTo(cx - headR * 0.85f, cy - headR * 0.5f)
                lineTo(cx - headR * 0.55f, cy - headR * 1.25f)
                lineTo(cx - headR * 0.15f, cy - headR * 0.95f)
                // 右耳
                moveTo(cx + headR * 0.85f, cy - headR * 0.5f)
                lineTo(cx + headR * 0.55f, cy - headR * 1.25f)
                lineTo(cx + headR * 0.15f, cy - headR * 0.95f)
            }
            drawPath(ear, color = ink, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            // 脸
            drawCircle(color = ink, radius = headR, center = Offset(cx, cy))
            // 耳朵内侧三角
            val inner = Path().apply {
                moveTo(cx - headR * 0.75f, cy - headR * 0.55f)
                lineTo(cx - headR * 0.58f, cy - headR * 1.1f)
                lineTo(cx - headR * 0.3f, cy - headR * 0.88f)
                moveTo(cx + headR * 0.75f, cy - headR * 0.55f)
                lineTo(cx + headR * 0.58f, cy - headR * 1.1f)
                lineTo(cx + headR * 0.3f, cy - headR * 0.88f)
            }
            drawPath(inner, color = bg, style = Stroke(width = strokeW * 0.7f, cap = StrokeCap.Round))
            // 额心留白和弯眼形成 CatLink 的统一品牌猫脸。
            val blaze = Path().apply {
                moveTo(cx, cy - headR * 0.82f)
                cubicTo(cx - headR * 0.22f, cy - headR * 0.55f, cx - headR * 0.18f, cy - headR * 0.32f, cx, cy - headR * 0.2f)
                cubicTo(cx + headR * 0.18f, cy - headR * 0.32f, cx + headR * 0.22f, cy - headR * 0.55f, cx, cy - headR * 0.82f)
                close()
            }
            drawPath(blaze, color = bg.copy(alpha = 0.9f))
            drawArc(bg, 12f, 156f, false, Offset(cx - headR * 0.58f, cy - headR * 0.2f), androidx.compose.ui.geometry.Size(headR * 0.42f, headR * 0.28f), style = Stroke(width = strokeW * 0.75f, cap = StrokeCap.Round))
            drawArc(bg, 12f, 156f, false, Offset(cx + headR * 0.16f, cy - headR * 0.2f), androidx.compose.ui.geometry.Size(headR * 0.42f, headR * 0.28f), style = Stroke(width = strokeW * 0.75f, cap = StrokeCap.Round))
            // 鼻子 + 嘴
            drawCircle(color = bg, radius = w * 0.026f, center = Offset(cx, cy + headR * 0.25f))
            val mouth = Path().apply {
                moveTo(cx - headR * 0.16f, cy + headR * 0.5f)
                cubicTo(cx - headR * 0.1f, cy + headR * 0.62f, cx + headR * 0.1f, cy + headR * 0.62f, cx + headR * 0.16f, cy + headR * 0.5f)
            }
            drawPath(mouth, color = bg, style = Stroke(width = strokeW * 0.7f, cap = StrokeCap.Round))
            // 胡须
            val whiskerColor = ink.copy(alpha = 0.55f)
            drawLine(whiskerColor, Offset(cx - headR * 0.45f, cy + headR * 0.05f), Offset(cx - headR * 1.1f, cy + headR * 0.1f), w * 0.014f, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx - headR * 0.45f, cy + headR * 0.3f), Offset(cx - headR * 1.05f, cy + headR * 0.42f), w * 0.014f, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx + headR * 0.45f, cy + headR * 0.05f), Offset(cx + headR * 1.1f, cy + headR * 0.1f), w * 0.014f, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx + headR * 0.45f, cy + headR * 0.3f), Offset(cx + headR * 1.05f, cy + headR * 0.42f), w * 0.014f, StrokeCap.Round)
        }
    }
}

/** CatLink 品牌猫心标记：用于首页温柔语句和公告发送方，不混用业务猫咪头像。 */
@Composable
fun BrandLogoAvatar(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
) {
    Image(
        painter = painterResource(com.catlink.app.R.drawable.catlink_logo_avatar),
        contentDescription = "有猫腻",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
    )
}

/** 空状态：柔和占位 */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 带图标的统计小块（统计页）：图标+数字并排在上、标签在下 */
@Composable
fun MiniStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    iconBg: Color = CatLinkThemeColors.extras.mint,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    SoftCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) { icon() }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 统一自绘对话框：不使用 Material AlertDialog 的默认间距、按钮和宽度。
 * 24dp 屏幕边距，最大宽度 360dp；内容超高时正文区域滚动，按钮始终固定在底部。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CatRadii.dialog),
) {
    val scheme = MaterialTheme.colorScheme
    val shadowColor = CatLinkThemeColors.extras.warmShadow
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = CatSpacing.xl, vertical = CatSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .shadow(14.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(shape)
                    .background(scheme.surfaceContainerLowest)
                    .border(CatSpacing.hairline, scheme.outlineVariant, shape)
                    .padding(horizontal = CatSpacing.lg, vertical = CatSpacing.lg),
            ) {
                if (title != null) {
                    Box(Modifier.fillMaxWidth()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides scheme.onSurface,
                            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge,
                        ) { title() }
                    }
                    Spacer(Modifier.height(CatSpacing.sm))
                }
                if (text != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides scheme.onSurfaceVariant,
                            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        ) { text() }
                    }
                    Spacer(Modifier.height(CatSpacing.sm))
                }
                // 底部按钮：紧凑单行右对齐（不高占用）
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
