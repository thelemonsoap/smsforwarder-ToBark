package com.lanbing.smsforwarder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.SmsForwarderApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h840dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutSheetTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var appRoot: View
    private var density = 2f

    @Before fun prepare() {
        RuntimeEnvironment.getApplication().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().putBoolean(Constants.PREF_PRIVACY_AGREED, true)
            .putBoolean(Constants.PREF_ENABLED, false).commit()
    }
    private fun launch(fontScale: Float = 1f) {
        rule.setContent {
            val view = LocalView.current
            val actualDensity = LocalDensity.current
            SideEffect { appRoot = view.rootView; density = actualDensity.density }
            CompositionLocalProvider(LocalDensity provides Density(actualDensity.density, fontScale)) {
                LiquidGlassTheme(dark = true) { SmsForwarderApp(0, 0, {}, {}, {}, {}) }
            }
        }
        rule.waitForIdle()
    }
    private fun open() {
        rule.onNodeWithContentDescription("关于").performClick()
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        rule.onNodeWithTag("about-sheet").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭关于").assertIsDisplayed()
    }
    private fun popupRoot(): View = WindowInspector.getGlobalWindowViews()
        .last { it !== appRoot && it.width > 0 && it.height > 0 }

    private fun assertCoveredAndCapture(name: String) {
        val sheet = rule.onNodeWithTag("about-sheet").fetchSemanticsNode().boundsInRoot
        lateinit var popup: Bitmap
        lateinit var app: Bitmap
        rule.runOnIdle {
            val root = popupRoot()
            popup = HardwareSnapshot.capture(root)
            app = HardwareSnapshot.capture(appRoot)
        }
        assertEquals(app.width, popup.width)
        assertEquals(app.height, popup.height)
        assertEquals(0f, sheet.left, 2f)
        assertEquals(popup.width.toFloat(), sheet.right, 2f)
        // The surface's caller modifier precedes Material's slide offset. Check the actual
        // rendered lower edge rather than treating that pre-offset semantics box as pixels.
        val composite = Bitmap.createBitmap(app.width, app.height, Bitmap.Config.ARGB_8888)
        Canvas(composite).apply { drawBitmap(app, 0f, 0f, null); drawBitmap(popup, 0f, 0f, null) }
        File("build/about-previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            assertTrue(composite.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val surface = android.graphics.Color.rgb(18, 18, 18)
        // The full-width opaque lower edge must cover BOTH previous dock corners and any nav gap.
        for (x in listOf(1, (12 * density).toInt(), popup.width / 2, popup.width - (12 * density).toInt(), popup.width - 2)) {
            for (y in listOf(popup.height - 2, popup.height - (4 * density).toInt())) {
                assertEquals("Dock must not show through at ($x,$y)", surface, popup.getPixel(x, y))
            }
        }
        for (y in (popup.height - 72 * density).toInt() until popup.height step (4 * density).toInt()) {
            for (x in listOf(1, popup.width - 2)) {
                assertEquals("Entire dock-side strip must stay covered", surface, popup.getPixel(x, y))
            }
        }
    }

    @Test fun infoSheetCoversBothDockCornersAndBottomTapsDoNotReachTheDock() {
        launch()
        val dockTarget = rule.onNodeWithTag("dock-tab-4", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        open()
        assertCoveredAndCapture("about-open")
        rule.onNode(isPopup()).performTouchInput { click(dockTarget) }
        rule.onNodeWithTag("about-sheet").assertExists()
        rule.onNodeWithContentDescription("关闭关于").performClick()
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithTag("about-sheet").assertDoesNotExist()
        rule.onNodeWithTag("dock-tab-0", useUnmergedTree = true).assertIsSelected()
        rule.onNodeWithContentDescription("关于").assertIsDisplayed()
    }
    @Test fun sourceAttributionAndLicenseStayReachableWhileCloseRemainsFixed() {
        launch()
        open()
        rule.onNodeWithText("查看原项目版权与 MIT 许可全文").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭关于").assertIsDisplayed()
        assertCoveredAndCapture("about-scrolled")
        rule.onNodeWithText("查看原项目版权与 MIT 许可全文").performClick()
        rule.onNodeWithText("原项目版权与 MIT 许可证").assertIsDisplayed()
        rule.onNode(hasText("关闭") and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("about-sheet").assertExists()
        rule.onNodeWithContentDescription("关闭关于").performClick()
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithTag("about-sheet").assertDoesNotExist()
    }
    @Test
    @Config(qualifiers = "zh-rCN-w840dp-h360dp-xhdpi")
    fun landscapeAndLargeTextKeepFullCoverageAndAnAccessibleCloseButton() {
        launch(fontScale = 1.5f)
        open()
        assertCoveredAndCapture("about-landscape-large-text")
        rule.onNodeWithContentDescription("关闭关于").performClick()
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithTag("about-sheet").assertDoesNotExist()
        rule.onNodeWithTag("dock-tab-0", useUnmergedTree = true).assertIsSelected()
    }
}
