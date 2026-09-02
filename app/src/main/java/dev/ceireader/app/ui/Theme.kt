package dev.ceireader.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Secure document" palette: deep navy + a refined, sparingly-used gold
 * accent. [ic_launcher_background] in `res/values/colors.xml` shares the
 * same navy so the launcher icon and in-app theme read as one brand.
 */
private val NavyPrimaryLight = Color(0xFF22447F)
private val NavyPrimaryDark = Color(0xFF9FC0FF)
private val GoldAccentLight = Color(0xFFA9791C)
private val GoldAccentDark = Color(0xFFE7C069)

private val LightColors = lightColorScheme(
    primary = NavyPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3F7),
    onPrimaryContainer = Color(0xFF0F2A52),
    secondary = Color(0xFF5B6B82), // muted slate
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E7ED),
    onSecondaryContainer = Color(0xFF2B3648),
    tertiary = GoldAccentLight, // refined gold accent -- used sparingly
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E3BE),
    onTertiaryContainer = Color(0xFF4A3607),
    background = Color(0xFFF5F6F9),
    onBackground = Color(0xFF1A2233),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF48505F),
    outline = Color(0xFF7C8494),
)

private val DarkColors = darkColorScheme(
    primary = NavyPrimaryDark, // light blue
    onPrimary = Color(0xFF0B2545),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFF9AA8BF), // muted slate
    onSecondary = Color(0xFF1B2333),
    secondaryContainer = Color(0xFF2A3446),
    onSecondaryContainer = Color(0xFFD3D9E3),
    tertiary = GoldAccentDark, // warm gold accent -- used sparingly
    onTertiary = Color(0xFF2E2000),
    tertiaryContainer = Color(0xFF4A3A12),
    onTertiaryContainer = Color(0xFFF6E3B4),
    background = Color(0xFF0E131F),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF182034),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF232C40),
    onSurfaceVariant = Color(0xFFB7BFCE),
    outline = Color(0xFF7C8494),
)

private val CeiShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private val CeiTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun CeiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic (wallpaper-derived) color is intentionally off: the brand's
    // navy + gold "secure document" identity should always show, not a
    // per-device Material You palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = CeiShapes,
        typography = CeiTypography,
        content = content,
    )
}
