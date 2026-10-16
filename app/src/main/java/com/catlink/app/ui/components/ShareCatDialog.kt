package com.catlink.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.ShareCatInput
import com.catlink.app.data.db.CatEntity
import com.catlink.app.ui.theme.CatRadii
import com.catlink.app.ui.theme.CatSpacing
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 快捷分享猫咪档案：多张照片 + 二维码 + 链接；可预览或继续发布到公开档案。 */
@Composable
fun ShareCatDialog(
    cat: CatEntity,
    onDismiss: () -> Unit,
    onPublish: () -> Unit,
    onPreview: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoThumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    /** 创建/重建分享链接与二维码（照片上传完成后调用，包含最新照片）。 */
    fun createShareLink(urls: List<String>) {
        scope.launch {
            busy = true
            app.container.shareApi.createShareLink(
                ShareCatInput(
                    catId = cat.id,
                    nickname = cat.nickname,
                    story = cat.story,
                    gender = cat.gender,
                    ageBand = cat.ageEstimate,
                    coat = cat.coat,
                    personalityTags = cat.tags,
                    healthSummary = cat.healthStatus,
                    statusCode = com.catlink.app.data.CatStatus.fromName(cat.status).code,
                    archiveReason = cat.archiveReason,
                    photoUrls = urls,
                )
            ).fold(
                onSuccess = { generated ->
                    code = generated
                    qr = withContext(Dispatchers.Default) {
                        runCatching {
                            val matrix = QRCodeWriter().encode(
                                "catlink://share/$generated",
                                BarcodeFormat.QR_CODE,
                                512,
                                512,
                            )
                            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                            for (x in 0 until 512) {
                                for (y in 0 until 512) {
                                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                }
                            }
                            bmp
                        }.getOrNull()
                    }
                    busy = false
                },
                onFailure = {
                    error = (it as? com.catlink.app.network.OnlineErrorException)?.error?.userMessage ?: it.message
                    busy = false
                },
            )
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val thumbs = photoThumbs.toMutableList()
            val urls = photoUrls.toMutableList()
            var failed = false
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                            ?: error("无法读取")
                        var sample = 1
                        while (options.outWidth / sample > 512 || options.outHeight / sample > 512) sample *= 2
                        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
                        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
                            ?: error("解码失败")
                        val file = File.createTempFile("share-", ".jpg", context.cacheDir)
                        try {
                            file.outputStream().use { require(bmp.compress(Bitmap.CompressFormat.JPEG, 65, it)) }
                            val url = app.container.shareApi.uploadMedia(file).getOrElse { error("上传失败") }
                            Pair(url, bmp)
                        } finally {
                            file.delete()
                        }
                    }
                }
                result.onSuccess { (url, bmp) ->
                    thumbs.add(bmp)
                    urls.add(url)
                }.onFailure { failed = true }
            }
            photoThumbs = thumbs
            photoUrls = urls
            busy = false
            if (failed) error = "部分照片上传失败"
            // 照片变化后重新生成分享链接（包含照片）
            if (urls.isNotEmpty()) createShareLink(urls)
        }
    }

    LaunchedEffect(Unit) {
        createShareLink(emptyList())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(CatRadii.card))
                .padding(CatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.md),
        ) {
            Text(
                "分享「${cat.nickname}」",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 照片选择（最多 4 张）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photoThumbs, key = { it.hashCode() }) { bmp ->
                        Box {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "分享照片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, RoundedCornerShape(CatRadii.compact)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .clickable {
                                        val idx = photoThumbs.indexOf(bmp)
                                        if (idx >= 0) {
                                            photoThumbs = photoThumbs.toMutableList().also { it.removeAt(idx) }
                                            photoUrls = photoUrls.toMutableList().also { it.removeAt(idx) }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (photoThumbs.size < 4) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(CatRadii.compact))
                            .clickable(enabled = !busy) {
                                pickPhotos.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Add, "添加照片", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            when {
                busy -> Text("正在生成分享链接…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error != null -> Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                qr != null && code != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = qr!!.asImageBitmap(),
                            contentDescription = "分享二维码",
                            modifier = Modifier
                                .size(150.dp)
                                .background(Color.White, RoundedCornerShape(CatRadii.compact))
                                .padding(8.dp),
                        )
                        Spacer(Modifier.width(CatSpacing.md))
                        Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
                            Text(
                                "扫描二维码或在其他设备打开链接查看猫咪档案",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "catlink://share/$code",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(
                    onClick = { onPublish() },
                    enabled = !busy && error == null,
                ) { Text("发布到公开档案", color = MaterialTheme.colorScheme.primary) }
                TextButton(
                    onClick = { code?.let(onPreview) },
                    enabled = code != null,
                ) { Text("预览档案页", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
