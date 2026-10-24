package org.opendnevnik.android.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF245C45), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDDD), onPrimaryContainer = Color(0xFF123B29),
    secondary = Color(0xFF55664C), secondaryContainer = Color(0xFFE0EACF),
    tertiary = Color(0xFF78593C), tertiaryContainer = Color(0xFFF7E3CE),
    background = Color(0xFFF7F8F2), surface = Color(0xFFF7F8F2),
    surfaceContainer = Color(0xFFEEF1E8), surfaceContainerLow = Color(0xFFF0F3EC),
    onSurface = Color(0xFF19241C), onSurfaceVariant = Color(0xFF455247),
    outline = Color(0xFF707C70), outlineVariant = Color(0xFFD1D8CB),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA0D4B3), onPrimary = Color(0xFF083823),
    primaryContainer = Color(0xFF245C45), onPrimaryContainer = Color(0xFFD5EDDD),
    secondary = Color(0xFFBFCDAD), tertiary = Color(0xFFE7BD95),
    background = Color(0xFF101710), surface = Color(0xFF101710),
    surfaceContainer = Color(0xFF1D271E), surfaceContainerLow = Color(0xFF19221A),
    onSurface = Color(0xFFE1E9DF), onSurfaceVariant = Color(0xFFBAC7B8),
)

@Composable
fun DiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
