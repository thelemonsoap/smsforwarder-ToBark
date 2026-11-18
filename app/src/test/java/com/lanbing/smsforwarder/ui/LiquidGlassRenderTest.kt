package com.lanbing.smsforwarder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.SmsForwarderApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassRenderTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var renderedView: View
    private var backdropColor = Color.White
    private lateinit var renderedHaze: dev.chrisbanes.haze.HazeState
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun prepare() {
        prefs.edit().clear().putBoolean(Constants.PREF_PRIVACY_AGREED, true)
            .putBoolean(Constants.PREF_ENABLED, false).putString(GLASS_THEME_MODE, "light").commit()
    }

    private fun launch(fontScale: Float = 1f) {
        rule.setContent {
            val view = LocalView.current
            SideEffect { renderedView = view.rootView }
            val appearance = rememberGlassAppearance()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LiquidGlassTheme(glassDarkMode(appearance), appearance.reduced) {
                    val background = MaterialTheme.colorScheme.background
                    val haze = LocalGlassBackdrop.current!!
                    SideEffect { backdropColor = background; renderedHaze = haze }
                    SmsForwarderApp(0, 0, {}, {}, {}, {})
                }
            }
        }
        rule.waitForIdle()
    }

    private fun nav(label: String) = rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun assertReadableHeading(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val foreground = layouts.single().layoutInput.style.color.luminance()
        val background = backdropColor.luminance()
        val ratio = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
        assertTrue("Heading '$text' contrast ratio was $ratio", ratio >= 4.5f)
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        lateinit var bitmap: Bitmap
        rule.runOnIdle {
            bitmap = HardwareSnapshot.capture(renderedView)
        }
        assertTrue(bitmap.width > 300 && bitmap.height > 600)
        if (name.startsWith("dark")) assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(1, bitmap.height / 2))
        val file = File("build/glass-previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun allFiveTabsRenderAndNavigateInLightAndDarkThemes() {
        launch()
        for (mode in listOf("light", "dark")) {
            rule.runOnIdle { prefs.edit().putString(GLASS_THEME_MODE, mode).commit() }
            listOf("首页", "关键词", "通道", "设置", "日志").forEachIndexed { index, label ->
                nav(label).performClick()
                rule.mainClock.advanceTimeBy(700)
                nav(label).assertIsSelected()
                if (index == 0) {
                    assertReadableHeading("转发中心")
                    assertReadableHeading("短信转发助手")
                }
                capture("$mode-$index")
            }
        }
    }

    @Test fun scrolledContentRemainsBehindBothFloatingGlassBars() {
        prefs.edit().putString(GLASS_THEME_MODE, "dark").commit()
        launch()
        nav("设置").performClick()
        rule.onNodeWithTag("settings-hub").performScrollToNode(hasTestTag("settings-entry-APPEARANCE"))
        rule.onNodeWithTag("settings-entry-APPEARANCE").performClick()
        rule.onNodeWithTag("settings-detail-APPEARANCE").performScrollToIndex(1)
        rule.waitForIdle()
        nav("设置").assertIsSelected()
        rule.onNodeWithContentDescription("关于").assertIsDisplayed()
        assertEquals(2, renderedHaze.areas.count { it.isValid })
        capture("dark-settings-scrolled")
    }

    @Test fun appearanceControlsPreserveExistingForwardingConfiguration() {
        prefs.edit().putBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, true)
            .putBoolean(Constants.PREF_CONTACT_NAME_ENABLED, true).commit()
        launch()
        nav("设置").performClick()
        rule.onNodeWithTag("settings-hub").performScrollToNode(hasTestTag("settings-entry-APPEARANCE"))
        rule.onNodeWithTag("settings-entry-APPEARANCE").performClick()
        rule.onNodeWithText("深色").performClick()
        rule.runOnIdle { assertEquals("dark", prefs.getString(GLASS_THEME_MODE, "")) }
        rule.onNodeWithContentDescription("减少透明效果").performClick()
        rule.runOnIdle {
            assertTrue(prefs.getBoolean(GLASS_REDUCE_TRANSPARENCY, false))
            assertTrue(prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false))
            assertTrue(prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false))
            assertFalse(prefs.getBoolean(Constants.PREF_ENABLED, true))
        }
        capture("dark-reduced-settings")
    }

    @Test fun largeTextKeepsNavigationAndHomeControlsAccessible() {
        launch(1.5f)
        rule.onNodeWithText("转发中心").assertIsDisplayed()
        rule.onNodeWithContentDescription("关于").assertIsDisplayed()
        rule.onNodeWithText("首页").assertIsDisplayed()
        capture("light-large-text")
    }

    @Test fun reducedTransparencyModeRendersWithoutGlassLayers() {
        prefs.edit().putBoolean(GLASS_REDUCE_TRANSPARENCY, true).commit()
        launch()
        rule.onNodeWithText("转发中心").assertIsDisplayed()
        capture("light-reduced-home")
    }
}
