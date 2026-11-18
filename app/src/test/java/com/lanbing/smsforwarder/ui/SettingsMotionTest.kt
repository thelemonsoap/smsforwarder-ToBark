package com.lanbing.smsforwarder.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class SettingsMotionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    @Before fun prepare() {
        prefs.edit().clear().putBoolean(Constants.PREF_PRIVACY_AGREED, true).putBoolean(Constants.PREF_ENABLED, false)
            .putString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE, "{sender}：{content}").commit()
    }
    private fun launch() {
        rule.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            LiquidGlassTheme(dark = true) { SmsForwarderApp(0, 0, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("dock-tab-3", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(700)
        rule.mainClock.autoAdvance = false
    }
    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun capture(name: String) {
        lateinit var image: Bitmap
        rule.runOnIdle { image = HardwareSnapshot.capture(root) }
        File("build/motion-previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))
        }
    }
    @Test fun enteringAndReturningUseVisibleFiniteMotionInOppositeDirections() {
        launch()
        val width = bounds("settings-transition").width
        capture("menu-start")
        rule.onNodeWithTag("settings-entry-SMS").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        val entering = bounds("settings-frame-SMS")
        assertTrue("Incoming settings should visibly move in, not jump", entering.left > 1f && entering.left < width / 5)
        capture("enter-mid")
        for (i in 0..8) { rule.mainClock.advanceTimeBy(32); capture("enter-$i") }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0f, bounds("settings-frame-SMS").left, 1f)
        rule.onNodeWithTag("settings-hub").assertDoesNotExist()
        capture("detail-settled")
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        assertTrue("Returning should move the detail to the right", bounds("settings-frame-SMS").left > 1f)
        capture("return-mid")
        for (i in 0..8) { rule.mainClock.advanceTimeBy(32); capture("return-$i") }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("settings-detail-SMS").assertDoesNotExist()
        rule.onNodeWithTag("settings-hub").assertIsDisplayed()
        capture("menu-settled")
    }
    @Test fun fastBackAndAnotherSelectionSettleOnTheCorrectPageWithoutChangingConfig() {
        launch()
        rule.onNodeWithTag("settings-entry-SMS").performClick()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("settings-entry-CALLS").performClick()
        rule.mainClock.advanceTimeBy(900)
        capture("rapid-target")
        rule.onNodeWithTag("settings-detail-CALLS").assertIsDisplayed()
        rule.onNodeWithTag("settings-detail-SMS").assertDoesNotExist()
        rule.onNodeWithTag("settings-hub").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("{sender}：{content}", prefs.getString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE, ""))
            assertFalse(prefs.getBoolean(Constants.PREF_ENABLED, true))
        }
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.mainClock.advanceTimeBy(900)
        rule.onNodeWithTag("settings-entry-CALLS").assertIsDisplayed()
    }
}
