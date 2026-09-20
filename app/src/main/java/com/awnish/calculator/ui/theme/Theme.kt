package com.awnish.calculator.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Light = lightColorScheme(primary = Color(0xFF355E50), secondary = Color(0xFF57655D), tertiary = Color(0xFF765B2A), surfaceVariant = Color(0xFFE6E9E4))
private val Dark = darkColorScheme(primary = Color(0xFFA8D0BC), secondary = Color(0xFFBECABD), tertiary = Color(0xFFE8C98C))
@Composable fun AwnishTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) { MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content) }
