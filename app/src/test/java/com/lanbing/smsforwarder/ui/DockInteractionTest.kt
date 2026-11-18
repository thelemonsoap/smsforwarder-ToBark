package com.lanbing.smsforwarder.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockInteractionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View
    private var density = 2f
    private lateinit var haze: HazeState

    private fun launch(width: Dp = 393.dp, fontScale: Float = 1f, rtl: Boolean = false, stripes: Boolean = false) {
        rule.setContent {
            val view = LocalView.current
            val actualDensity = LocalDensity.current
            SideEffect { root = view.rootView; density = actualDensity.density }
            CompositionLocalProvider(LocalDensity provides Density(actualDensity.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                LiquidGlassTheme(dark = true) {
                    val backdrop = LocalGlassBackdrop.current!!
                    SideEffect { haze = backdrop }
                    var selected by remember { mutableStateOf(0) }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        if (stripes) {
                            Canvas(Modifier.fillMaxSize().liquidContentBackdrop().testTag("chrome-test-source")) {
                                val step = 4.dp.toPx()
                                for (i in 0..(size.width / step).toInt()) drawRect(
                                    if (i % 2 == 0) Color.Black else Color.White,
                                    topLeft = Offset(i * step, 0f), size = Size(step, size.height))
                            }
                            LiquidAppHeader(false, {}, statusInsets = WindowInsets(top = 24.dp))
                        }
                        Box(Modifier.width(width).align(Alignment.BottomCenter)) {
                            LiquidNavigationBar(selected, { selected = it }, navigationInsets = WindowInsets(bottom = if (stripes) 24.dp else 0.dp))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun screenshot(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle { bitmap = HardwareSnapshot.capture(root) }
        val f = File("build/dock-previews/$name.png").apply { parentFile!!.mkdirs() }
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    @Test fun largerIconsAndCompactLabelsKeepFullTouchTargets() {
        launch()
        assertTrue(bounds("navigation-dock").width <= 340 * density)
        for (i in 0..4) {
            val icon = bounds("dock-icon-$i")
            val label = bounds("dock-label-$i")
            val tab = bounds("dock-tab-$i")
            assertTrue(icon.height >= 28 * density)
            assertTrue(icon.height > label.height * 1.5f)
            assertTrue(label.top - icon.bottom in 0f..(3f * density))
            assertTrue(tab.width >= 48 * density && tab.height >= 48 * density)
        }
        screenshot("dock-default")
    }
    @Test fun oneSliderMovesContinuouslyAndRetargetsInFlight() {
        launch()
        rule.mainClock.autoAdvance = false
        val start = bounds("dock-slider").left
        val destination = bounds("dock-tab-4").left
        screenshot("slide-start")
        rule.onNodeWithText("日志").performClick()
        rule.mainClock.advanceTimeByFrame()
        for (i in 0..8) {
            rule.mainClock.advanceTimeBy(40)
            screenshot("slide-$i")
        }
        // A second pass with an interruption verifies that the same slider takes the new target.
        rule.onNodeWithText("首页").performClick()
        rule.mainClock.advanceTimeByFrame()
        for (i in 0..8) {
            rule.mainClock.advanceTimeBy(40)
            screenshot("return-$i")
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithText("日志").performClick()
        rule.mainClock.advanceTimeBy(80)
        val middle = bounds("dock-slider").left
        assertTrue("Slider must travel between cells, not jump", middle > start + 2 && middle < destination - 2)
        rule.onAllNodesWithTag("dock-slider", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("关键词").performClick()
        rule.mainClock.advanceTimeBy(900)
        val end = bounds("dock-slider").left
        assertTrue(abs(end - bounds("dock-tab-1").left) <= 2 * density)
        rule.onNodeWithTag("dock-tab-1", useUnmergedTree = true).assertIsSelected()
        screenshot("dock-retargeted")
    }
    @Test fun narrowRtlDockAndLargeTextRemainUsable() {
        launch(width = 264.dp, fontScale = 1.5f, rtl = true)
        for (i in 0..4) {
            rule.onNodeWithTag("dock-label-$i", useUnmergedTree = true).assertIsDisplayed()
            val tab = bounds("dock-tab-$i")
            assertTrue(tab.width >= 48 * density && tab.height >= 48 * density)
        }
        rule.onNodeWithText("日志").performClick()
        rule.mainClock.advanceTimeBy(900)
        assertTrue(abs(bounds("dock-slider").left - bounds("dock-tab-4").left) <= 2 * density)
        screenshot("dock-narrow-rtl-large-text")
    }
    @Test fun onlyTheRoundedHeaderAndDockBlurWhileTheirSurroundingsStaySharp() {
        launch(stripes = true)
        val areas = haze.areas.filter { it.isValid }
        assertEquals("Exactly one glass area per floating bar", 2, areas.size)
        val image = screenshot("bar-only-glass")
        val source = bounds("chrome-test-source")
        val screenOrigin = IntArray(2).also { root.getLocationOnScreen(it) }
        var checked = 0
        fun assertSharp(x: Int, y: Int) {
            val stripe = ((x - source.left) / (4 * density)).toInt()
            val expected = if (stripe % 2 == 0) AndroidColor.BLACK else AndroidColor.WHITE
            assertEquals("Outside glass at ($x,$y) must match the unblurred source", expected, image.getPixel(x, y))
            checked++
        }
        // Status/navigation insets are transparent; neither gets a full-width blur rectangle.
        for (y in listOf(source.top + 8 * density, source.bottom - 8 * density)) {
            for (x in (source.left + 8 * density).toInt() until (source.right - 8 * density).toInt()) {
                assertSharp(x, y.toInt())
            }
        }
        val deviations = mutableListOf<Double>()
        for (area in areas) {
            val topLeft = area.positionOnScreen - Offset(screenOrigin[0].toFloat(), screenOrigin[1].toFloat())
            val rect = Rect(topLeft, area.size)
            assertTrue(rect.width < source.width)
            // Horizontal margins and the four rounded cutouts must remain completely sharp.
            for (x in listOf(source.left + 5 * density, source.right - 5 * density)) {
                assertSharp(x.toInt(), rect.center.y.toInt())
            }
            for (x in listOf(rect.left + 4 * density, rect.right - 4 * density)) {
                for (y in listOf(rect.top + 4 * density, rect.bottom - 4 * density)) {
                    assertSharp(x.toInt(), y.toInt())
                }
            }
            // Empty top padding inside each pane must still show real blur.
            val y = (rect.top + 2 * density).toInt()
            val values = ((rect.left + 40 * density).toInt() until (rect.right - 40 * density).toInt())
                .map { AndroidColor.red(image.getPixel(it, y)).toDouble() }
            val mean = values.average()
            val std = sqrt(values.map { (it - mean) * (it - mean) }.average())
            assertTrue("Bar interior must retain real blur, std=$std", std < 15)
            deviations += std
        }
        assertTrue(bounds("dock-tab-0").bottom <= source.bottom - 24 * density)
        File("build/dock-previews/glass-boundary-metrics.txt").writeText(
            "glassAreas=${areas.size}\nunchangedOutsidePixels=$checked\ninsideBlurStd=$deviations\n")
    }
}
