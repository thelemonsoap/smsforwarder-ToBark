package com.lanbing.smsforwarder.ui

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.LogTab
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h840dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogLayoutTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View
    private var density = 2f

    private fun launch(fontScale: Float = 1f) {
        val messages = listOf("电话状态：SIM 1 空闲", "已连接 Wi-Fi：暂停 LTE／5G 检测和信号提醒", "电话提醒监听已开启：SIM 1；通道 Bark", "通知权限已开启", "电量监听器已注册", "应用通知监听已连接")
        val logs = List(200) { "[2026-10-31 00:24:11] 第 ${it + 1} 条：${messages[it % messages.size]}" }
        rule.setContent {
            val view = LocalView.current
            val actualDensity = LocalDensity.current
            SideEffect { root = view.rootView; density = actualDensity.density }
            CompositionLocalProvider(LocalDensity provides Density(actualDensity.density, fontScale)) {
                LiquidGlassTheme(dark = true) {
                    Scaffold(containerColor = Color.Transparent,
                        topBar = { LiquidAppHeader(false, {}, statusInsets = WindowInsets(top = 24.dp)) },
                        bottomBar = { LiquidNavigationBar(4, {}, navigationInsets = WindowInsets(bottom = 24.dp)) }
                    ) { insets ->
                        CompositionLocalProvider(LocalGlassInsets provides insets) {
                            Box(Modifier.fillMaxSize().liquidContentBackdrop()) {
                                LogTab(logs, autoRefresh = true, onRefresh = {}, onClear = {}, onAutoRefreshChange = {})
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun assertContained(inner: Rect, outer: Rect) {
        assertTrue("$inner must fit inside $outer", inner.left >= outer.left && inner.right <= outer.right && inner.top >= outer.top && inner.bottom <= outer.bottom)
    }
    private fun verifyLayoutAndCapture(name: String) {
        val panel = bounds("log-panel")
        val dock = bounds("navigation-dock")
        assertTrue("Log panel must end above the dock", panel.bottom <= dock.top - 12 * density)
        val title = bounds("log-title")
        val actions = bounds("log-actions")
        val status = bounds("log-refresh-status")
        assertContained(title, panel); assertContained(actions, panel); assertContained(status, panel)
        assertFalse("Title and actions cannot overlap", title.overlaps(actions))
        assertFalse("Status and actions cannot overlap", status.overlaps(actions))
        lateinit var image: Bitmap
        rule.runOnIdle { image = HardwareSnapshot.capture(root) }
        File("build/log-previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val text = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("log-title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(text) }
        val titleLayout = text.single()
        // Intrinsic CJK widths may round up by a fraction of a pixel; inspect rendered line
        // extent with one-pixel raster tolerance rather than the integer overflow flag.
        assertFalse("The title must not be ellipsized", titleLayout.isLineEllipsized(0))
        assertTrue("The complete log title must fit", titleLayout.getLineRight(0) - titleLayout.getLineLeft(0) <= titleLayout.size.width + 1f)

        listOf("自动刷新", "刷新", "清除").forEach { label ->
            val control = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertContained(control, panel)
            assertTrue(control.width >= 48 * density && control.height >= 48 * density)
        }
        assertContained(bounds("log-list"), panel)
        rule.onNodeWithTag("log-list").performScrollToIndex(199)
        val last = rule.onNodeWithText("第 200 条：", substring = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertContained(last, panel)
        assertTrue(last.bottom < dock.top)
    }
    @Test fun normalScreenKeepsLogHeadingToolbarAndLastEntryInsideThePanel() {
        launch()
        verifyLayoutAndCapture("logs-360")
    }
    @Test
    @Config(qualifiers = "zh-rCN-w264dp-h840dp-xhdpi")
    fun narrowScreenAndLargeTextWrapTheToolbarWithoutAnyOverlap() {
        launch(fontScale = 1.5f)
        verifyLayoutAndCapture("logs-narrow-large-text")
    }
}
