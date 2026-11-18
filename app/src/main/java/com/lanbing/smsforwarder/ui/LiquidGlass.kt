package com.lanbing.smsforwarder.ui

import android.os.Build
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

const val GLASS_THEME_MODE = "glass_theme_mode"
const val GLASS_REDUCE_TRANSPARENCY = "glass_reduce_transparency"
const val GLASS_FULLSCREEN = "glass_fullscreen"
data class GlassAppearance(val mode: String = "system", val reduced: Boolean = false, val fullscreen: Boolean = true)
data class GlassStyle(val dark: Boolean = false, val reduced: Boolean = false)
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }
val LocalGlassInsets = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

@Composable fun glassPagePadding(extra: androidx.compose.ui.unit.Dp = 16.dp): PaddingValues {
    val insets = LocalGlassInsets.current
    return PaddingValues(top = insets.calculateTopPadding() + extra, bottom = insets.calculateBottomPadding() + extra)
}

/** Haze's RenderNode backend is available on API32+. Older systems stay opaque. */
@Composable fun Modifier.liquidContentBackdrop(): Modifier {
    val style = LocalGlassStyle.current
    val state = LocalGlassBackdrop.current
    val base = background(MaterialTheme.colorScheme.background)
    return if (state != null && !style.reduced && Build.VERSION.SDK_INT >= 32)
        base.haze(state, HazeStyle(tint = Color.Transparent, blurRadius = 22.dp, noiseFactor = 0f))
    else base
}

@Composable fun rememberGlassAppearance(): GlassAppearance {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    fun read() = GlassAppearance(prefs.getString(GLASS_THEME_MODE, "system") ?: "system", prefs.getBoolean(GLASS_REDUCE_TRANSPARENCY, false), prefs.getBoolean(GLASS_FULLSCREEN, true))
    var appearance by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == GLASS_THEME_MODE || key == GLASS_REDUCE_TRANSPARENCY || key == GLASS_FULLSCREEN) appearance = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return appearance
}

@Composable fun glassDarkMode(appearance: GlassAppearance): Boolean = when (appearance.mode) {
    "light" -> false; "dark" -> true; else -> isSystemInDarkTheme()
}

@Composable fun LiquidGlassTheme(dark: Boolean, reducedTransparency: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF080808), primaryContainer = Color(0xFF292929), onPrimaryContainer = Color(0xFFF5F5F5),
        tertiary = Color(0xFFD8BA80), tertiaryContainer = Color(0xFF30291E), onTertiaryContainer = Color(0xFFF5DFB6),
        secondary = Color(0xFFCBCBCB), secondaryContainer = Color(0xFF262626), onSecondaryContainer = Color(0xFFF1F1F1),
        background = Color.Black, onBackground = Color(0xFFF3F3F3), surface = Color(0xFF121212), onSurface = Color(0xFFF3F3F3),
        surfaceVariant = Color(0xFF202020), onSurfaceVariant = Color(0xFFB9B9B9), outline = Color(0xFF8B8B8B), outlineVariant = Color(0xFF303030),
        error = Color(0xFFFFB4AE), errorContainer = Color(0xFF3D2020), onErrorContainer = Color(0xFFFFDAD6)
    ) else lightColorScheme(
        primary = Color(0xFF222222), onPrimary = Color.White, primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Color(0xFF181818),
        tertiary = Color(0xFF85602A), tertiaryContainer = Color(0xFFFFEBC9), onTertiaryContainer = Color(0xFF5A3E18),
        secondary = Color(0xFF555555), secondaryContainer = Color(0xFFE9E9E9), onSecondaryContainer = Color(0xFF242424),
        background = Color(0xFFF5F5F5), onBackground = Color(0xFF171717), surface = Color.White, onSurface = Color(0xFF171717),
        surfaceVariant = Color(0xFFEEEEEE), onSurfaceVariant = Color(0xFF626262), outline = Color(0xFF858585), outlineVariant = Color(0xFFE0E0E0),
        error = Color(0xFFB32635), errorContainer = Color(0xFFFCE5E6), onErrorContainer = Color(0xFF76232C)
    )
    val typography = Typography(
        headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
        labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    )
    val backdrop = remember { HazeState() }
    CompositionLocalProvider(LocalGlassStyle provides GlassStyle(dark, reducedTransparency), LocalGlassBackdrop provides backdrop) {
        MaterialTheme(colorScheme = colors, typography = typography,
            shapes = Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(12.dp), GlassShapes.container, GlassShapes.container, GlassShapes.container),
            content = content)
    }
}

/** Pure black in dark mode; no ambient color clouds or animated background. */
@Composable fun Modifier.liquidBackdrop(): Modifier = background(MaterialTheme.colorScheme.background)

/** Background pixels are blurred by the shared Haze source; foreground content stays sharp. */
@Composable fun Modifier.liquidChrome(shape: Shape = GlassShapes.container): Modifier {
    val style = LocalGlassStyle.current
    val state = LocalGlassBackdrop.current
    val supported = !style.reduced && Build.VERSION.SDK_INT >= 32 && state != null
    val surface = MaterialTheme.colorScheme.surface
    val pane = if (supported) {
        hazeChild(state!!, shape, HazeStyle(
            tint = if (style.dark) Color.White.copy(alpha = .025f) else Color.White.copy(alpha = .20f),
            blurRadius = 22.dp, noiseFactor = 0f))
    } else this
    val edge = if (style.dark) Color.White else Color(0xFF808080)
    val border = Brush.linearGradient(listOf(
        edge.copy(alpha = if (style.reduced) .12f else .38f),
        edge.copy(alpha = .05f), edge.copy(alpha = .09f), edge.copy(alpha = .21f)))
    val highlight = Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (supported) .055f else 0f), Color.Transparent, Color.Transparent))
    return pane.shadow(if (style.dark) 0.dp else 5.dp, shape, clip = false).clip(shape)
        .background(if (supported) surface.copy(alpha = if (style.dark) .36f else .42f) else surface)
        .background(highlight).border(.75.dp, border, shape)
}

@Composable fun LiquidCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = GlassShapes.container,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick == null) Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    else Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
}

@Composable fun LiquidNavigationBar(
    selected: Int,
    onSelected: (Int) -> Unit,
    navigationInsets: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
) {
    val labels = listOf("首页", "关键词", "通道", "设置", "日志")
    val icons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.Label, Icons.Outlined.Cloud, Icons.Outlined.Settings, Icons.Outlined.History)
    BoxWithConstraints(Modifier.fillMaxWidth().imePadding(), contentAlignment = Alignment.Center) {
        val sidePadding = if (maxWidth < 300.dp) 4.dp else 12.dp
        Box(Modifier.fillMaxWidth().windowInsetsPadding(navigationInsets).padding(horizontal = sidePadding, vertical = 6.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.widthIn(max = 340.dp).fillMaxWidth().liquidChrome().padding(4.dp).testTag("navigation-dock")) {
                val cellWidth = maxWidth / labels.size
                val sliderPosition by animateFloatAsState(
                    targetValue = selected.toFloat(),
                    animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
                    label = "dockSliderPosition")
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.offset(x = cellWidth * sliderPosition).width(cellWidth).fillMaxHeight()
                        .clip(GlassShapes.dockSelection).background(MaterialTheme.colorScheme.primaryContainer)
                        .border(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), GlassShapes.dockSelection)
                        .testTag("dock-slider"))
                }
                Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    labels.forEachIndexed { index, label ->
                        val active = selected == index
                        val tint by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            tween(180), label = "tabTint")
                        Column(Modifier.weight(1f).clip(GlassShapes.dockSelection)
                            .selectable(active, role = Role.Tab, onClick = { onSelected(index) })
                            .heightIn(min = 58.dp).padding(horizontal = 2.dp, vertical = 7.dp)
                            .testTag("dock-tab-$index"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)) {
                            Icon(icons[index], contentDescription = null, tint = tint,
                                modifier = Modifier.size(30.dp).testTag("dock-icon-$index"))
                            Text(label, color = tint, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.testTag("dock-label-$index"))
                        }
                    }
                }
            }
        }
    }
}

@Composable fun LiquidPageHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun AppIdentityIcon(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF2563EB)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_glass_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable fun LiquidAppHeader(
    enabled: Boolean,
    onAbout: () -> Unit,
    statusInsets: WindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).union(WindowInsets.captionBar),
    title: String? = null,
    onBack: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(statusInsets).padding(horizontal = 16.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth().liquidChrome().padding(horizontal = 16.dp, vertical = 10.dp).testTag("app-header"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack == null) AppIdentityIcon(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)))
            else IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title ?: "短信转发助手", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).background(if (enabled) Color(0xFF23956F) else MaterialTheme.colorScheme.outline, CircleShape))
                    Text(if (onBack != null) "设置" else if (enabled) "服务运行中" else "服务已停止", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onAbout, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))) {
                Icon(Icons.Outlined.Info, contentDescription = "关于", tint = MaterialTheme.colorScheme.primary)
            }
        }
        }
    }
}

@Composable fun GlassAppearanceCard() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    val appearance = rememberGlassAppearance()
    LiquidCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("外观与显示", style = MaterialTheme.typography.titleLarge)
            Text("黑白配色 · 玻璃导航", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("全屏沉浸", style = MaterialTheme.typography.bodyLarge)
                    Text("收起状态栏和手势栏，边缘滑动可临时显示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = appearance.fullscreen, onCheckedChange = { prefs.edit().putBoolean(GLASS_FULLSCREEN, it).apply() },
                    modifier = Modifier.semantics { contentDescription = "全屏沉浸" })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    FilterChip(selected = appearance.mode == mode, onClick = { prefs.edit().putString(GLASS_THEME_MODE, mode).apply() },
                        label = { Text(label) }, shape = CircleShape)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("减少透明效果", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = appearance.reduced, onCheckedChange = { prefs.edit().putBoolean(GLASS_REDUCE_TRANSPARENCY, it).apply() },
                    modifier = Modifier.semantics { contentDescription = "减少透明效果" })
            }
            Text("关闭背景模糊与透光，使用实色导航，保持清晰易读。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
