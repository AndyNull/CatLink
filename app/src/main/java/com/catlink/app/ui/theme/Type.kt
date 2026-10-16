package com.catlink.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.catlink.app.R

/**
 * 字体体系：Plus Jakarta Sans 用于标题（圆润几何感），
 * Be Vietnam Pro 用于正文与数据录入（当代人文主义）。
 */

// Plus Jakarta Sans 可变字体：一个文件覆盖全部字重
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

// 注意：Noto Sans SC 可变字体在 Android 上存在多行文本绘制裁剪 bug
// （布局高度正常但第二行起不渲染），已移除 fallback，中文回退系统字体稳定渲染。

val JakartaFamily = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
)

val BeVietnamFamily = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

val CatLinkTypography = Typography(
    // 页面大标题：26sp / 700 / -2% 字距（headline-lg-mobile）
    headlineLarge = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.02).sp,
    ),
    // 区块标题：22px / 600（headline-md）
    headlineMedium = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // 次级区块标题：20px / 600
    headlineSmall = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = JakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    // 长文与数据录入
    bodyLarge = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // 标签：+5% 字距，小号清晰
    labelLarge = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BeVietnamFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.sp,
    ),
)
