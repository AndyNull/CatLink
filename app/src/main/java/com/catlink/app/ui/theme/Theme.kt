package com.catlink.app.ui.theme

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 主题风格：白色 / 奶油粉 / 夜间（三套独立主题，设置页可切换） */
enum class ThemeStyle(val label: String, val isDark: Boolean) {
    WHITE("白色", false),
    PINK("奶油粉", false),
    DARK("夜间护眼", true),
}

/** 业务扩展色：状态与装饰使用语义 token，业务页面不硬编码色值。 */
@Immutable
data class CatLinkExtraColors(
    val mint: Color,
    val onMint: Color,
    val accentPink: Color,
    val onAccentPink: Color,
    val warmShadow: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    CatLinkExtraColors(
        mint = Color(0xFFE0F2F1), onMint = Color(0xFF3A4A49),
        accentPink = Color(0xFFFFB7C5), onAccentPink = Color(0xFF6B3743),
        warmShadow = Color(0x0F000000),
    )
}

object CatLinkThemeColors {
    val extras: CatLinkExtraColors
        @Composable get() = LocalExtraColors.current
}

private val WhiteColors = lightColorScheme(
    primary = WhitePrimary,
    onPrimary = WhiteOnPrimary,
    primaryContainer = WhitePrimaryContainer,
    onPrimaryContainer = WhiteOnPrimaryContainer,
    secondary = WhiteSecondary,
    onSecondary = WhiteOnSecondary,
    secondaryContainer = WhiteSecondaryContainer,
    onSecondaryContainer = WhiteOnSecondaryContainer,
    tertiary = WhiteTertiary,
    onTertiary = WhiteOnTertiary,
    tertiaryContainer = WhiteTertiaryContainer,
    onTertiaryContainer = WhiteOnTertiaryContainer,
    background = WhiteBackground,
    onBackground = WhiteOnSurface,
    surface = WhiteSurface,
    onSurface = WhiteOnSurface,
    surfaceVariant = WhiteSurfaceContainer,
    onSurfaceVariant = WhiteOnSurfaceVariant,
    surfaceTint = WhitePrimary,
    error = WhiteError,
    onError = Color.White,
    errorContainer = WhiteErrorContainer,
    onErrorContainer = WhiteOnErrorContainer,
    outline = WhiteOutline,
    outlineVariant = WhiteOutlineVariant,
    surfaceBright = WhiteSurfaceLowest,
    surfaceContainerLowest = WhiteSurfaceLowest,
    surfaceContainerLow = WhiteSurfaceLow,
    surfaceContainer = WhiteSurfaceContainer,
    surfaceContainerHigh = WhiteSurfaceHigh,
    surfaceContainerHighest = WhiteSurfaceHighest,
)

private val PinkColors = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    primaryContainer = PinkPrimaryContainer,
    onPrimaryContainer = PinkOnPrimaryContainer,
    secondary = PinkSecondary,
    onSecondary = PinkOnSecondary,
    secondaryContainer = PinkSecondaryContainer,
    onSecondaryContainer = PinkOnSecondaryContainer,
    tertiary = PinkTertiary,
    onTertiary = PinkOnTertiary,
    tertiaryContainer = PinkTertiaryContainer,
    onTertiaryContainer = PinkOnTertiaryContainer,
    background = PinkBackground,
    onBackground = PinkOnSurface,
    surface = PinkSurface,
    onSurface = PinkOnSurface,
    surfaceVariant = PinkSurfaceContainer,
    onSurfaceVariant = PinkOnSurfaceVariant,
    surfaceTint = PinkPrimary,
    error = PinkError,
    onError = Color.White,
    errorContainer = PinkErrorContainer,
    onErrorContainer = PinkOnErrorContainer,
    outline = PinkOutline,
    outlineVariant = PinkOutlineVariant,
    surfaceBright = PinkSurfaceLowest,
    surfaceContainerLowest = PinkSurfaceLowest,
    surfaceContainerLow = PinkSurfaceLow,
    surfaceContainer = PinkSurfaceContainer,
    surfaceContainerHigh = PinkSurfaceHigh,
    surfaceContainerHighest = PinkSurfaceHighest,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceBright = Color(0xFF463E42),
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
)

private fun extrasFor(style: ThemeStyle): CatLinkExtraColors = when (style) {
    ThemeStyle.WHITE -> CatLinkExtraColors(
        mint = Color(0xFFE0F2F1),
        onMint = Color(0xFF2E5A57),
        accentPink = Color(0xFFFFB7C5),
        onAccentPink = Color(0xFF7B4551),
        warmShadow = Color(0x59E6E2D8), // 暖色纸感阴影 #E6E2D8 @ 35%（对齐设计稿 .soft-shadow）
    )
    ThemeStyle.PINK -> CatLinkExtraColors(
        mint = Color(0xFFE4F0EC),
        onMint = Color(0xFF2E5A50),
        accentPink = Color(0xFFFFC7D8),
        onAccentPink = Color(0xFF711C3E),
        warmShadow = Color(0x17B02B5C), // 淡粉色调阴影
    )
    ThemeStyle.DARK -> CatLinkExtraColors(
        mint = Color(0xFF23413F),
        onMint = Color(0xFFB8CAC9),
        accentPink = Color(0xFF5A3642),
        onAccentPink = Color(0xFFFFB1C8),
        warmShadow = Color(0x66000000),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CatLinkTheme(
    themeStyle: ThemeStyle = ThemeStyle.WHITE,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeStyle) {
        ThemeStyle.WHITE -> WhiteColors
        ThemeStyle.PINK -> PinkColors
        ThemeStyle.DARK -> DarkColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CatLinkTypography,
        shapes = CatLinkShapes,
        content = {
            // 全局禁用点击灰色 ripple（无原生按压效果），交互反馈由状态色/背景变化承担
            CompositionLocalProvider(
                LocalExtraColors provides extrasFor(themeStyle),
                LocalRippleConfiguration provides NoRippleConfig,
            ) {
                content()
            }
        },
    )
}

/** 透明 ripple：点击/按压不显示灰色波纹 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private val NoRippleConfig = RippleConfiguration(
    color = Color.Transparent,
    rippleAlpha = RippleAlpha(0f, 0f, 0f, 0f),
)
