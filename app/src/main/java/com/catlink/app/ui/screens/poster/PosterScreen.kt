package com.catlink.app.ui.screens.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catlink.app.data.HomeStats
import com.catlink.app.ui.MainViewModel
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.SecondaryTopBar
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** 海报版式与颜色独立选择，所有组合都必须保持预览和导出一致。 */
enum class PosterLayout(val label: String) {
    POETIC("诗意"), FRAMED("相框"), TENDER("柔光"),
    EDITORIAL("杂志"), POSTCARD("明信片"), MOONLIGHT("月光"),
    BOTANICAL("花笺"), JOURNAL("手账"), MINIMAL("留白"), SUNRISE("晨光"),
    LETTER("信笺"), EMBRACE("拥抱"), ARCH("拱窗"), BOOKMARK("书页"),
    BREEZE("微风"), RIPPLE("涟漪"),
}

private data class PosterStyle(
    val name: String,
    val top: Color,
    val bottom: Color,
    val ink: Color,
    val accent: Color,
    val muted: Color,
    val paper: Color,
)

private data class PosterContent(
    val quote: String,
    val metric: String,
    val metricLabel: String,
)

private const val PosterWidth = 1080f
private const val PosterHeight = 1920f
private const val PosterPreviewWidth = 540
internal const val PosterSafeInset = 72f

private data class PosterLayoutSpec(
    val quoteBounds: RectF,
    val quoteSize: Float,
    val quoteAlignment: Layout.Alignment,
    val catBounds: RectF,
    val metricX: Float,
    val metricBaseline: Float,
    val metricSize: Float,
    val metricAlign: Paint.Align = Paint.Align.LEFT,
    val labelX: Float,
    val labelBaseline: Float,
    val labelAlign: Paint.Align = Paint.Align.LEFT,
    val footer: String? = null,
    val footerX: Float = 150f,
    val footerBaseline: Float = 1740f,
    val footerAlign: Paint.Align = Paint.Align.LEFT,
)

internal data class PosterElementBounds(
    val name: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PosterRenderAudit(
    val style: String,
    val layout: String,
    val quoteFits: Boolean,
    val quoteLineCount: Int,
    val elements: List<PosterElementBounds>,
)

private class PosterAuditBuilder(private val style: PosterStyle, private val layout: PosterLayout) {
    private val elements = mutableListOf<PosterElementBounds>()
    var quoteFits = false
    var quoteLineCount = 0

    fun record(name: String, left: Float, top: Float, right: Float, bottom: Float) {
        elements += PosterElementBounds(name, left, top, right, bottom)
    }

    fun build() = PosterRenderAudit(style.name, layout.label, quoteFits, quoteLineCount, elements.toList())
}

private fun posterLayoutSpec(layout: PosterLayout): PosterLayoutSpec = when (layout) {
    PosterLayout.POETIC -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 500f, 930f, 1010f), quoteSize = 86f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(690f, 1050f, 930f, 1290f), metricX = 150f, metricBaseline = 1450f, metricSize = 132f,
        labelX = 150f, labelBaseline = 1520f, footer = "用心记录每一次救助", footerBaseline = 1735f,
    )
    PosterLayout.FRAMED -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 720f, 930f, 1160f), quoteSize = 86f, quoteAlignment = Layout.Alignment.ALIGN_CENTER,
        catBounds = RectF(390f, 300f, 690f, 600f), metricX = 540f, metricBaseline = 1350f, metricSize = 120f,
        metricAlign = Paint.Align.CENTER, labelX = 540f, labelBaseline = 1420f, labelAlign = Paint.Align.CENTER,
        footer = "用心记录每一次救助", footerX = 540f, footerBaseline = 1660f, footerAlign = Paint.Align.CENTER,
    )
    PosterLayout.TENDER -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 930f, 930f, 1350f), quoteSize = 78f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(380f, 350f, 700f, 670f), metricX = 150f, metricBaseline = 1515f, metricSize = 132f,
        labelX = 150f, labelBaseline = 1590f, footer = "把被看见的生命，交给时间慢慢发光", footerBaseline = 1740f,
    )
    PosterLayout.EDITORIAL -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 980f, 930f, 1390f), quoteSize = 84f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(720f, 1450f, 940f, 1670f), metricX = 150f, metricBaseline = 520f, metricSize = 210f,
        labelX = 150f, labelBaseline = 600f,
    )
    PosterLayout.POSTCARD -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 940f, 930f, 1370f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(380f, 340f, 700f, 660f), metricX = 150f, metricBaseline = 1570f, metricSize = 120f,
        labelX = 150f, labelBaseline = 1645f,
    )
    PosterLayout.MOONLIGHT -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 1030f, 930f, 1400f), quoteSize = 80f, quoteAlignment = Layout.Alignment.ALIGN_CENTER,
        catBounds = RectF(370f, 450f, 710f, 790f), metricX = 540f, metricBaseline = 1570f, metricSize = 126f,
        metricAlign = Paint.Align.CENTER, labelX = 540f, labelBaseline = 1645f, labelAlign = Paint.Align.CENTER,
    )
    PosterLayout.BOTANICAL -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 930f, 930f, 1370f), quoteSize = 84f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(610f, 355f, 910f, 655f), metricX = 150f, metricBaseline = 1540f, metricSize = 126f,
        labelX = 150f, labelBaseline = 1615f,
    )
    PosterLayout.JOURNAL -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 500f, 930f, 1060f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(700f, 1260f, 930f, 1490f), metricX = 150f, metricBaseline = 1450f, metricSize = 132f,
        labelX = 150f, labelBaseline = 1525f, footer = LocalDate.now().toString(), footerBaseline = 1740f,
    )
    PosterLayout.MINIMAL -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 650f, 930f, 1110f), quoteSize = 88f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(730f, 1150f, 920f, 1340f), metricX = 150f, metricBaseline = 1580f, metricSize = 118f,
        labelX = 150f, labelBaseline = 1655f,
    )
    PosterLayout.SUNRISE -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 1020f, 930f, 1400f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_CENTER,
        catBounds = RectF(375f, 410f, 705f, 740f), metricX = 540f, metricBaseline = 1560f, metricSize = 132f,
        metricAlign = Paint.Align.CENTER, labelX = 540f, labelBaseline = 1635f, labelAlign = Paint.Align.CENTER,
        footer = "今天也从温柔开始", footerX = 540f, footerBaseline = 1740f, footerAlign = Paint.Align.CENTER,
    )
    PosterLayout.LETTER -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 500f, 930f, 1040f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(690f, 1080f, 930f, 1320f), metricX = 150f, metricBaseline = 1480f, metricSize = 124f,
        labelX = 150f, labelBaseline = 1555f, footer = "写给仍在认真守护的你", footerBaseline = 1740f,
    )
    PosterLayout.EMBRACE -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 790f, 930f, 1210f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_CENTER,
        catBounds = RectF(390f, 330f, 690f, 630f), metricX = 540f, metricBaseline = 1425f, metricSize = 126f,
        metricAlign = Paint.Align.CENTER, labelX = 540f, labelBaseline = 1500f, labelAlign = Paint.Align.CENTER,
        footer = "每一次接住，都是一次拥抱", footerX = 540f, footerBaseline = 1735f, footerAlign = Paint.Align.CENTER,
    )
    PosterLayout.ARCH -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 930f, 930f, 1350f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(390f, 350f, 690f, 650f), metricX = 150f, metricBaseline = 1530f, metricSize = 124f,
        labelX = 150f, labelBaseline = 1605f,
    )
    PosterLayout.BOOKMARK -> PosterLayoutSpec(
        quoteBounds = RectF(210f, 700f, 900f, 1230f), quoteSize = 80f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(700f, 1300f, 920f, 1520f), metricX = 150f, metricBaseline = 460f, metricSize = 174f,
        labelX = 150f, labelBaseline = 540f, footer = LocalDate.now().toString(), footerBaseline = 1735f,
    )
    PosterLayout.BREEZE -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 510f, 930f, 1040f), quoteSize = 84f, quoteAlignment = Layout.Alignment.ALIGN_NORMAL,
        catBounds = RectF(660f, 1090f, 920f, 1350f), metricX = 150f, metricBaseline = 1480f, metricSize = 126f,
        labelX = 150f, labelBaseline = 1555f, footer = "愿温柔有迹可循", footerBaseline = 1740f,
    )
    PosterLayout.RIPPLE -> PosterLayoutSpec(
        quoteBounds = RectF(150f, 830f, 930f, 1260f), quoteSize = 82f, quoteAlignment = Layout.Alignment.ALIGN_CENTER,
        catBounds = RectF(390f, 350f, 690f, 650f), metricX = 540f, metricBaseline = 1460f, metricSize = 126f,
        metricAlign = Paint.Align.CENTER, labelX = 540f, labelBaseline = 1535f, labelAlign = Paint.Align.CENTER,
        footer = "一圈一圈，把善意传远", footerX = 540f, footerBaseline = 1740f, footerAlign = Paint.Align.CENTER,
    )
}

private val PosterStyles = listOf(
    PosterStyle("奶油白", Color(0xFFFCF6EE), Color(0xFFF8E8E7), Color(0xFF44393C), Color(0xFFA75368), Color(0xFF8B7479), Color(0xFFFFFCF8)),
    PosterStyle("雾青绿", Color(0xFFF3F7F4), Color(0xFFE3EEEA), Color(0xFF30413E), Color(0xFF557C75), Color(0xFF70837F), Color(0xFFFAFCFA)),
    PosterStyle("暮莓紫", Color(0xFF211B1E), Color(0xFF33282C), Color(0xFFF5E8EB), Color(0xFFEAA0B5), Color(0xFFC7AFB6), Color(0xFF3A3034)),
    PosterStyle("落日橙", Color(0xFFFFF0E6), Color(0xFFFFD9C9), Color(0xFF5A3E32), Color(0xFFE07850), Color(0xFF9A6B58), Color(0xFFFFF6F0)),
    PosterStyle("湖水青", Color(0xFFEAF4F2), Color(0xFFD3E8E4), Color(0xFF1F3B38), Color(0xFF2F8478), Color(0xFF5F7E79), Color(0xFFF2FAF8)),
    PosterStyle("麦芽金", Color(0xFFFFFAF0), Color(0xFFF6EBD9), Color(0xFF4E4234), Color(0xFFC99A3C), Color(0xFF8C7A5E), Color(0xFFFFFDF6)),
    PosterStyle("纸框棕", Color(0xFFF9F6F0), Color(0xFFEFE6D8), Color(0xFF3E342A), Color(0xFF9C6B3F), Color(0xFF7E6C55), Color(0xFFFFFBF4)),
    PosterStyle("柔光杏", Color(0xFFFFF6F0), Color(0xFFF6DFD8), Color(0xFF513B3A), Color(0xFFD27D70), Color(0xFF9A706C), Color(0xFFFFFBF8)),
)

private val PosterQuotes = listOf(
    "你留下的一小步，\n是它重新相信世界的一大步。",
    "被认真记录的生命，\n终会被温柔地看见。",
    "救助不是一个人的英雄故事，\n是许多次没有放弃。",
    "今天做过的每一件小事，\n都在替它靠近一个家。",
    "愿每一次相遇都有回声，\n每一份守护都有归处。",
)

@Composable
fun PosterScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val defaultQuote = remember { LocalDate.now().dayOfYear % PosterQuotes.size }
    var styleIndex by remember { mutableIntStateOf(0) }
    var layoutIndex by remember { mutableIntStateOf(0) }
    var quoteIndex by remember { mutableIntStateOf(defaultQuote) }
    var generating by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var quoteDraft by remember { mutableStateOf("") }
    var customQuote by remember { mutableStateOf<String?>(null) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var savedShareUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val style = PosterStyles[styleIndex]
    val layout = PosterLayout.entries[layoutIndex]
    val activeQuote = customQuote ?: PosterQuotes[quoteIndex]
    val content = posterContent(stats, activeQuote)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CatSpacing.pageHorizontal),
    ) {
        SecondaryTopBar(title = "鼓励海报", onBack = onBack)
        Spacer(Modifier.height(CatSpacing.sm))

        PosterPreview(style, layout, content)

        Spacer(Modifier.height(CatSpacing.md))
        Text("海报颜色", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(CatSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            PosterStyles.forEachIndexed { index, option ->
                PosterChoice(
                    text = option.name,
                    selected = styleIndex == index,
                    accent = option.accent,
                    onClick = { styleIndex = index; savedName = null },
                )
            }
        }

        Spacer(Modifier.height(CatSpacing.md))
        Text("海报风格", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(CatSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            PosterLayout.entries.forEachIndexed { index, option ->
                PosterChoice(
                    text = option.label,
                    selected = layoutIndex == index,
                    accent = style.accent,
                    onClick = { layoutIndex = index; savedName = null },
                )
            }
        }

        Spacer(Modifier.height(CatSpacing.md))
        Text("这一刻想说", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(CatSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
        ) {
            PosterQuotes.forEachIndexed { index, quote ->
                PosterChoice(
                    text = quote.substringBefore('，').substringBefore('\n').take(11),
                    selected = customQuote == null && quoteIndex == index,
                    accent = style.accent,
                    onClick = { customQuote = null; quoteIndex = index; savedName = null },
                )
            }
        }
        Spacer(Modifier.height(CatSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = quoteDraft,
                onValueChange = { quoteDraft = it.take(120) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.weight(1f).height(44.dp).padding(vertical = 4.dp),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(CatRadii.field))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CatRadii.field))
                            .padding(horizontal = CatSpacing.sm),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (quoteDraft.isEmpty()) Text("输入这一刻想说的话", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
            )
            PosterCompactButton(
                text = "确认",
                onClick = {
                    val text = quoteDraft.trim()
                    if (text.length !in 4..120) CatNoticeCenter.show("请输入 4-120 个字", error = true)
                    else { customQuote = text; savedName = null }
                },
            )
            PosterCompactButton(
                text = "随机",
                onClick = {
                    scope.launch {
                        val api = (context.applicationContext as com.catlink.app.CatLinkApplication).container.publicApi
                        api.encouragement().onSuccess { quote -> customQuote = quote; quoteDraft = quote; savedName = null }
                            .onFailure { customQuote = null; quoteIndex = (0 until PosterQuotes.size).random() }
                    }
                },
            )
        }

        Spacer(Modifier.height(CatSpacing.md))
        Text(
            "只展示聚合里程碑，不包含领养人、合同、地址、医疗金额与猫咪私密资料。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(CatSpacing.sm))
        CatButton(
            text = if (generating) "正在生成海报…" else "保存并分享",
            enabled = !generating,
            onClick = {
                generating = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { renderAndSavePoster(context, style, layout, content) }
                    generating = false
                    result.onSuccess { saved ->
                        savedName = saved.name
                        savedShareUri = saved.uri
                        showSavedDialog = true
                    }.onFailure { error ->
                        CatNoticeCenter.show(error.message ?: "海报生成失败", error = true)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            leading = {
                if (generating) CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        savedName?.let {
            Spacer(Modifier.height(CatSpacing.xs))
            Text("已保存：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(CatSpacing.xl))
    }

    if (showSavedDialog) {
        com.catlink.app.ui.components.CatDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text("海报已保存 🎉") },
            text = { Text("已保存到系统相册。是否立即分享？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    savedShareUri?.let { sharePoster(context, it) }
                    showSavedDialog = false
                }) { Text("分享") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSavedDialog = false }) { Text("完成") }
            },
        )
    }
}

@Composable
private fun PosterChoice(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CatRadii.pill)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = CatSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.size(CatSpacing.xs))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PosterCompactButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(CatRadii.control))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CatRadii.control)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PosterPreview(style: PosterStyle, layout: PosterLayout, content: PosterContent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rendered = remember(style, layout, content) { renderPoster(context, style, layout, content, bitmapWidth = PosterPreviewWidth) }
    DisposableEffect(rendered.bitmap) {
        onDispose { rendered.bitmap.recycle() }
    }
    Image(
        bitmap = rendered.bitmap.asImageBitmap(),
        contentDescription = "${layout.label}海报预览",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(26.dp))
            .background(style.top),
    )
}

private data class SavedPoster(val uri: Uri, val name: String)

private fun posterContent(stats: HomeStats, quote: String): PosterContent = when {
    stats.adopted > 0 -> PosterContent(quote, stats.adopted.toString(), "个生命已经抵达新的家")
    stats.rescued > 0 -> PosterContent(quote, stats.rescued.toString(), "个生命被认真接住")
    stats.streakDays > 0 -> PosterContent(quote, stats.streakDays.toString(), "天持续记录与守护")
    else -> PosterContent(quote, "今天", "也值得为这份温柔留下记录")
}

private data class RenderedPoster(val bitmap: Bitmap, val audit: PosterRenderAudit)

private fun renderAndSavePoster(
    context: Context,
    style: PosterStyle,
    layout: PosterLayout,
    content: PosterContent,
): Result<SavedPoster> = runCatching {
    val rendered = renderPoster(context, style, layout, content)
    try {
        saveBitmap(context, rendered.bitmap, "youmaoni_poster_${System.currentTimeMillis()}.png")
    } finally {
        rendered.bitmap.recycle()
    }
}

private fun renderPoster(
    context: Context,
    style: PosterStyle,
    layout: PosterLayout,
    content: PosterContent,
    bitmapWidth: Int = PosterWidth.toInt(),
): RenderedPoster {
    val bitmapHeight = (bitmapWidth * PosterHeight / PosterWidth).toInt()
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(bitmapWidth / PosterWidth, bitmapWidth / PosterWidth)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val audit = PosterAuditBuilder(style, layout)
    val spec = posterLayoutSpec(layout)

    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        PosterHeight,
        style.top.toArgbCompat(),
        style.bottom.toArgbCompat(),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, PosterWidth, PosterHeight, paint)
    paint.shader = null
    drawPosterDecorations(canvas, paint, style, layout, audit)
    drawPosterBrandLockup(context, canvas, paint, style, layoutSuffix(layout), audit)

    drawFittedText(
        canvas = canvas,
        text = content.quote,
        bounds = spec.quoteBounds,
        maxTextSize = spec.quoteSize,
        color = style.ink.toArgbCompat(),
        alignment = spec.quoteAlignment,
        audit = audit,
    )
    drawCatLineArt(
        canvas,
        style.accent.toArgbCompat(),
        spec.catBounds.left,
        spec.catBounds.top,
        spec.catBounds.width(),
    )
    audit.record("cat", spec.catBounds.left, spec.catBounds.top, spec.catBounds.right, spec.catBounds.bottom)

    drawSingleLine(
        canvas = canvas,
        paint = paint,
        text = content.metric,
        x = spec.metricX,
        baseline = spec.metricBaseline,
        requestedSize = spec.metricSize,
        color = if (layout == PosterLayout.FRAMED) style.accent.toArgbCompat() else style.ink.toArgbCompat(),
        typeface = Typeface.create(Typeface.SANS_SERIF, if (layout == PosterLayout.MINIMAL) Typeface.NORMAL else Typeface.BOLD),
        align = spec.metricAlign,
        maxWidth = 780f,
        name = "metric",
        audit = audit,
    )
    drawSingleLine(
        canvas = canvas,
        paint = paint,
        text = content.metricLabel,
        x = spec.labelX,
        baseline = spec.labelBaseline,
        requestedSize = 34f,
        color = style.muted.toArgbCompat(),
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
        align = spec.labelAlign,
        maxWidth = 780f,
        name = "metricLabel",
        audit = audit,
    )
    spec.footer?.let {
        drawSingleLine(
            canvas = canvas,
            paint = paint,
            text = it,
            x = spec.footerX,
            baseline = spec.footerBaseline,
            requestedSize = 28f,
            color = style.muted.toArgbCompat(),
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            align = spec.footerAlign,
            maxWidth = 780f,
            name = "footer",
            audit = audit,
        )
    }
    return RenderedPoster(bitmap, audit.build())
}

private fun drawPosterDecorations(
    canvas: Canvas,
    paint: Paint,
    style: PosterStyle,
    layout: PosterLayout,
    audit: PosterAuditBuilder,
) {
    paint.style = Paint.Style.FILL
    paint.color = style.accent.copy(alpha = .09f).toArgbCompat()
    canvas.drawCircle(PosterWidth * .86f, PosterHeight * .10f, 190f, paint)

    when (layout) {
        PosterLayout.FRAMED -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.color = style.accent.toArgbCompat()
            canvas.drawRect(48f, 48f, PosterWidth - 48f, PosterHeight - 48f, paint)
            paint.strokeWidth = 2f
            paint.color = style.accent.copy(alpha = .5f).toArgbCompat()
            canvas.drawRect(72f, 72f, PosterWidth - 72f, PosterHeight - 72f, paint)
            paint.style = Paint.Style.FILL
        }
        PosterLayout.TENDER -> {
            paint.color = style.accent.copy(alpha = .13f).toArgbCompat()
            canvas.drawCircle(PosterWidth * .5f, 510f, 250f, paint)
            drawSingleLine(
                canvas, paint, "这一刻想说", 150f, 890f, 28f, style.muted.toArgbCompat(),
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), Paint.Align.LEFT, 780f, "eyebrow", audit,
            )
        }
        PosterLayout.EDITORIAL -> drawSingleLine(
            canvas, paint, "TODAY'S NOTE", 150f, 930f, 25f, style.accent.toArgbCompat(),
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), Paint.Align.LEFT, 780f, "eyebrow", audit,
        )
        PosterLayout.POSTCARD -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = style.ink.copy(alpha = .18f).toArgbCompat()
            canvas.drawRoundRect(60f, 60f, PosterWidth - 60f, PosterHeight - 60f, 30f, 30f, paint)
            paint.style = Paint.Style.FILL
        }
        PosterLayout.MOONLIGHT -> {
            paint.color = style.paper.copy(alpha = .72f).toArgbCompat()
            canvas.drawCircle(PosterWidth * .5f, 620f, 300f, paint)
        }
        PosterLayout.BOTANICAL -> {
            paint.color = style.paper.copy(alpha = .76f).toArgbCompat()
            canvas.drawCircle(760f, 500f, 230f, paint)
            paint.color = style.accent.copy(alpha = .55f).toArgbCompat()
            canvas.drawCircle(150f, 1740f, 7f, paint)
            canvas.drawCircle(184f, 1740f, 11f, paint)
            canvas.drawCircle(224f, 1740f, 7f, paint)
        }
        PosterLayout.JOURNAL -> {
            drawSingleLine(
                canvas, paint, "TODAY", 150f, 410f, 25f, style.accent.toArgbCompat(),
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), Paint.Align.LEFT, 780f, "eyebrow", audit,
            )
            paint.color = style.ink.copy(alpha = .14f).toArgbCompat()
            canvas.drawRect(150f, 1190f, 930f, 1193f, paint)
        }
        PosterLayout.POETIC -> {
            paint.color = style.ink.copy(alpha = .10f).toArgbCompat()
            canvas.drawRect(150f, 1650f, 930f, 1653f, paint)
        }
        PosterLayout.SUNRISE -> {
            paint.color = style.accent.copy(alpha = .12f).toArgbCompat()
            canvas.drawCircle(PosterWidth * .5f, 590f, 290f, paint)
        }
        PosterLayout.LETTER -> {
            paint.color = style.paper.copy(alpha = .72f).toArgbCompat()
            canvas.drawRoundRect(110f, 390f, 970f, 1640f, 34f, 34f, paint)
            paint.color = style.accent.copy(alpha = .28f).toArgbCompat()
            canvas.drawCircle(895f, 1570f, 24f, paint)
        }
        PosterLayout.EMBRACE -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = style.accent.copy(alpha = .18f).toArgbCompat()
            canvas.drawCircle(540f, 490f, 250f, paint)
            canvas.drawCircle(540f, 490f, 285f, paint)
            paint.style = Paint.Style.FILL
        }
        PosterLayout.ARCH -> {
            paint.color = style.paper.copy(alpha = .72f).toArgbCompat()
            canvas.drawRoundRect(250f, 260f, 830f, 790f, 290f, 290f, paint)
            paint.color = style.accent.copy(alpha = .16f).toArgbCompat()
            canvas.drawRoundRect(286f, 296f, 794f, 754f, 254f, 254f, paint)
        }
        PosterLayout.BOOKMARK -> {
            paint.color = style.ink.copy(alpha = .12f).toArgbCompat()
            canvas.drawRect(178f, 650f, 182f, 1260f, paint)
            drawSingleLine(
                canvas, paint, "救助记录 · ${LocalDate.now().year}", 210f, 1320f, 25f, style.muted.toArgbCompat(),
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), Paint.Align.LEFT, 600f, "eyebrow", audit,
            )
        }
        PosterLayout.BREEZE -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = style.accent.copy(alpha = .22f).toArgbCompat()
            repeat(3) { index ->
                val path = Path().apply {
                    moveTo(150f, 1180f + index * 52f)
                    cubicTo(340f, 1100f + index * 52f, 520f, 1260f + index * 52f, 700f, 1180f + index * 52f)
                }
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        }
        PosterLayout.RIPPLE -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = style.accent.copy(alpha = .16f).toArgbCompat()
            canvas.drawCircle(540f, 500f, 240f, paint)
            canvas.drawCircle(540f, 500f, 285f, paint)
            canvas.drawCircle(540f, 500f, 330f, paint)
            paint.style = Paint.Style.FILL
        }
        PosterLayout.MINIMAL -> Unit
    }
}

private fun drawFittedText(
    canvas: Canvas,
    text: String,
    bounds: RectF,
    maxTextSize: Float,
    color: Int,
    alignment: Layout.Alignment,
    audit: PosterAuditBuilder,
) {
    val normalized = text.trim()
    var textSize = maxTextSize
    var layout: StaticLayout
    var fits: Boolean
    do {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            this.textSize = textSize
        }
        layout = StaticLayout.Builder.obtain(normalized, 0, normalized.length, textPaint, bounds.width().toInt())
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(10f, 1f)
            .setMaxLines(10)
            .build()
        fits = layout.height <= bounds.height() && layout.lineCount > 0 &&
            layout.getLineEnd(layout.lineCount - 1) >= normalized.length
        if (!fits) textSize -= 2f
    } while (!fits && textSize >= 30f)

    val top = bounds.top + ((bounds.height() - layout.height) / 2f).coerceAtLeast(0f)
    canvas.save()
    canvas.translate(bounds.left, top)
    layout.draw(canvas)
    canvas.restore()
    audit.quoteFits = fits
    audit.quoteLineCount = layout.lineCount
    audit.record("quote", bounds.left, top, bounds.right, top + layout.height)
}

private fun drawSingleLine(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    requestedSize: Float,
    color: Int,
    typeface: Typeface,
    align: Paint.Align,
    maxWidth: Float,
    name: String,
    audit: PosterAuditBuilder,
) {
    paint.style = Paint.Style.FILL
    paint.shader = null
    paint.typeface = typeface
    paint.textSize = requestedSize
    paint.color = color
    paint.textAlign = align
    paint.letterSpacing = 0f
    while (paint.measureText(text) > maxWidth && paint.textSize > 20f) {
        paint.textSize -= 2f
    }
    val width = paint.measureText(text)
    val left = when (align) {
        Paint.Align.LEFT -> x
        Paint.Align.CENTER -> x - width / 2f
        Paint.Align.RIGHT -> x - width
    }
    val metrics = paint.fontMetrics
    audit.record(name, left, baseline + metrics.ascent, left + width, baseline + metrics.descent)
    canvas.drawText(text, x, baseline, paint)
}

private fun layoutSuffix(layout: PosterLayout): String = when (layout) {
    PosterLayout.POETIC -> "今日守护"
    PosterLayout.FRAMED -> "纪念"
    PosterLayout.TENDER -> "一点温柔"
    PosterLayout.EDITORIAL -> "救助手记"
    PosterLayout.POSTCARD -> "寄给今天"
    PosterLayout.MOONLIGHT -> "今夜也温柔"
    PosterLayout.BOTANICAL -> "花笺"
    PosterLayout.JOURNAL -> "救助手账"
    PosterLayout.MINIMAL -> "留白"
    PosterLayout.SUNRISE -> "晨光"
    PosterLayout.LETTER -> "一封信"
    PosterLayout.EMBRACE -> "拥抱"
    PosterLayout.ARCH -> "窗边"
    PosterLayout.BOOKMARK -> "书页"
    PosterLayout.BREEZE -> "微风"
    PosterLayout.RIPPLE -> "涟漪"
}

internal fun auditPosterLayoutSamples(context: Context, quote: String): List<PosterRenderAudit> {
    val content = PosterContent(quote, "今天", "也值得为这份温柔留下记录")
    return PosterStyles.flatMap { style ->
        PosterLayout.entries.map { layout ->
            val rendered = renderPoster(context, style, layout, content, bitmapWidth = 270)
            rendered.bitmap.recycle()
            rendered.audit
        }
    }
}

internal fun renderPosterSample(context: Context, layoutIndex: Int, quote: String, bitmapWidth: Int = PosterWidth.toInt()): Bitmap {
    val content = PosterContent(quote, "今天", "也值得为这份温柔留下记录")
    return renderPoster(context, PosterStyles.first(), PosterLayout.entries[layoutIndex], content, bitmapWidth).bitmap
}

private fun saveBitmap(context: Context, bitmap: Bitmap, name: String): SavedPoster {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/有猫腻")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "无法创建系统相册文件" }
        try {
            context.contentResolver.openOutputStream(uri)?.use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                ?: error("无法写入系统相册")
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return SavedPoster(uri, "Pictures/有猫腻/$name")
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "有猫腻").apply { mkdirs() }
    val file = File(dir, name)
    FileOutputStream(file).use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    return SavedPoster(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), file.name)
}

private fun sharePoster(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享这份守护"))
}

private fun drawPosterBrandLockup(
    context: Context,
    canvas: Canvas,
    paint: Paint,
    style: PosterStyle,
    suffix: String,
    audit: PosterAuditBuilder,
) {
    val logo = decodePosterLogo(context)
    val logoBounds = RectF(150f, 96f, 234f, 180f)
    paint.alpha = 255
    paint.shader = null
    paint.style = Paint.Style.FILL
    canvas.drawBitmap(logo, null, logoBounds, paint)
    logo.recycle()
    audit.record("logo", logoBounds.left, logoBounds.top, logoBounds.right, logoBounds.bottom)
    drawSingleLine(
        canvas, paint, "有猫腻 · $suffix", 254f, 153f, 30f, style.accent.toArgbCompat(),
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), Paint.Align.LEFT, 674f, "brand", audit,
    )
}

private fun decodePosterLogo(context: Context): Bitmap {
    val resource = com.catlink.app.R.drawable.catlink_logo_avatar
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(context.resources, resource, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 168) sample *= 2
    return requireNotNull(BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inSampleSize = sample }))
}

private fun drawCatLineArt(canvas: Canvas, color: Int, left: Float, top: Float, size: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(size * .06f)
    }
    val path = Path().apply {
        moveTo(left + size * .18f, top + size * .57f)
        cubicTo(left + size * .18f, top + size * .43f, left + size * .24f, top + size * .29f, left + size * .31f, top + size * .31f)
        cubicTo(left + size * .35f, top + size * .31f, left + size * .38f, top + size * .35f, left + size * .41f, top + size * .40f)
        cubicTo(left + size * .42f, top + size * .42f, left + size * .44f, top + size * .42f, left + size * .45f, top + size * .41f)
        cubicTo(left + size * .48f, top + size * .40f, left + size * .52f, top + size * .40f, left + size * .55f, top + size * .41f)
        cubicTo(left + size * .56f, top + size * .42f, left + size * .58f, top + size * .42f, left + size * .59f, top + size * .40f)
        cubicTo(left + size * .62f, top + size * .35f, left + size * .65f, top + size * .31f, left + size * .69f, top + size * .31f)
        cubicTo(left + size * .76f, top + size * .29f, left + size * .82f, top + size * .43f, left + size * .82f, top + size * .57f)
        cubicTo(left + size * .82f, top + size * .78f, left + size * .68f, top + size * .89f, left + size * .50f, top + size * .90f)
        cubicTo(left + size * .32f, top + size * .89f, left + size * .18f, top + size * .78f, left + size * .18f, top + size * .57f)
    }
    canvas.drawPath(path, paint)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(left + size * .39f, top + size * .57f, 7f, paint)
    canvas.drawCircle(left + size * .61f, top + size * .57f, 7f, paint)
    paint.style = Paint.Style.STROKE
    val smile = Path().apply {
        moveTo(left + size * .42f, top + size * .68f)
        cubicTo(left + size * .45f, top + size * .73f, left + size * .48f, top + size * .73f, left + size * .50f, top + size * .69f)
        cubicTo(left + size * .52f, top + size * .73f, left + size * .55f, top + size * .73f, left + size * .58f, top + size * .68f)
    }
    canvas.drawPath(smile, paint)
}

private fun Color.toArgbCompat(): Int = AColor.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
