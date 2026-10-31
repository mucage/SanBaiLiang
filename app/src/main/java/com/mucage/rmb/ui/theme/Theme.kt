package com.mucage.rmb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

/** 品牌绿 */
val GreenPrimary = Color(0xFF147D64)
val GreenPrimaryDark = Color(0xFF4FC3A0)

/** 浅色模式配色 */
private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF0E1),
    onPrimaryContainer = Color(0xFF06362A),
    secondary = Color(0xFFF5B301),
    onSecondary = Color(0xFF3A2E00),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Color(0xFF5A6066),
    outline = Color(0xFFD4D8DD),
    error = Color(0xFFE5484D),
    onError = Color.White,
)

/** 深色模式配色 */
private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF06362A),
    primaryContainer = Color(0xFF0B4C3C),
    onPrimaryContainer = Color(0xFFCFF0E1),
    secondary = Color(0xFFF5C84A),
    onSecondary = Color(0xFF3A2E00),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE6E9ED),
    surface = Color(0xFF1B1E21),
    onSurface = Color(0xFFE6E9ED),
    surfaceVariant = Color(0xFF26292D),
    onSurfaceVariant = Color(0xFFB9C0C7),
    outline = Color(0xFF3A3F45),
    error = Color(0xFFFF6B70),
    onError = Color(0xFF2A0506),
)

/** 支出金额颜色（跟随明暗模式） */
val ExpenseColor = Color(0xFFE59A2F)
/** 收入金额颜色（跟随明暗模式） */
val IncomeColor = Color(0xFF2FA84F)

@Composable
fun JianZhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
