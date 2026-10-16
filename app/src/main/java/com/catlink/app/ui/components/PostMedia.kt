package com.catlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing

fun parsePostMedia(value: String): List<String> =
    value.split('|', ',').map(String::trim).filter(String::isNotBlank).distinct().take(4)

/** 社区列表多图预览：1 张宽图，2–4 张等宽缩略图，不把多张 URL 当成一个地址。 */
@Composable
fun PostMediaPreview(
    mediaUrls: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val urls = remember(mediaUrls) { parsePostMedia(mediaUrls) }
    if (urls.isEmpty()) return
    if (urls.size == 1) {
        PostMediaImage(
            url = urls.first(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            targetPixels = 1080,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 10f),
        )
        return
    }
    val rows = urls.chunked(2)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CatSpacing.xs),
    ) {
        rows.forEachIndexed { rowIndex, rowUrls ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CatSpacing.xs),
            ) {
                rowUrls.forEachIndexed { index, url ->
                    val absoluteIndex = rowIndex * 2 + index
                    PostMediaImage(
                        url = url,
                        contentDescription = "$contentDescription ${absoluteIndex + 1}",
                        contentScale = ContentScale.Crop,
                        targetPixels = 540,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    )
                }
                if (rowUrls.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

fun postDetailMediaHeight(widthDp: Float, aspectRatio: Float): Float =
    (widthDp / aspectRatio.coerceIn(0.25f, 4f)).coerceIn(180f, 520f)

/** 详情媒体按原比例完整显示，避免 Crop 截掉照片主体。 */
@Composable
fun PostDetailMedia(
    url: String,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableFloatStateOf(16f / 10f) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val targetHeight = postDetailMediaHeight(maxWidth.value, aspectRatio.floatValue).dp
        PostMediaImage(
            url = url,
            contentDescription = "帖子图片 ${index + 1}",
            contentScale = ContentScale.Fit,
            targetPixels = 1080,
            modifier = Modifier.fillMaxWidth().height(targetHeight),
            onSuccess = { width, height ->
                if (width > 0 && height > 0) aspectRatio.floatValue = width.toFloat() / height.toFloat()
            },
        )
    }
}

@Composable
private fun PostMediaImage(
    url: String,
    contentDescription: String,
    contentScale: ContentScale,
    targetPixels: Int,
    modifier: Modifier,
    onSuccess: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val request = remember(context, url, targetPixels) {
        ImageRequest.Builder(context)
            .data(url)
            .size(targetPixels)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    val failed = androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CatRadii.card))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
            onState = { state ->
                failed.value = state is AsyncImagePainter.State.Error
                if (state is AsyncImagePainter.State.Success) {
                    val size = state.painter.intrinsicSize
                    onSuccess(size.width.toInt(), size.height.toInt())
                }
            },
        )
        if (failed.value) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "图片加载失败",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
