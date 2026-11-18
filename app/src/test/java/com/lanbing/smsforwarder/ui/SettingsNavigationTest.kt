package com.lanbing.smsforwarder.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
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
class SettingsNavigationTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View
    private lateinit var activity: ComponentActivity
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun prepare() {
        prefs.edit().clear().putBoolean(Constants.PREF_PRIVACY_AGREED, true)
            .putBoolean(Constants.PREF_ENABLED, false).putString(GLASS_THEME_MODE, "dark").commit()
    }
    private fun launch() {
        rule.setContent {
            val view = LocalView.current
            val context = LocalContext.current
            SideEffect { root = view.rootView; activity = context as ComponentActivity }
            LiquidGlassTheme(dark = true) { SmsForwarderApp(0, 0, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("dock-tab-3", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(800)
    }
    private fun open(section: SettingsSection) {
        rule.onNodeWithTag("settings-hub").performScrollToNode(hasTestTag("settings-entry-${section.name}"))
        // Floating chrome is outside the lazy viewport. Bring the row clear of it before tapping,
        // just as a user scrolls a bottom entry above the dock.
        val row = rule.onNodeWithTag("settings-entry-${section.name}").fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("navigation-dock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        if (row.bottom > dock.top) {
            rule.onNodeWithTag("settings-hub").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, row.bottom - dock.top + 32f)
            }
        }
        rule.onNodeWithTag("settings-entry-${section.name}").performClick()
        rule.onNodeWithTag("settings-detail-${section.name}").assertExists()
        rule.onNodeWithTag("settings-hub").assertDoesNotExist()
    }
    private fun back() { rule.onNodeWithContentDescription("返回设置").performClick() }
    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        lateinit var bitmap: Bitmap
        rule.runOnIdle { bitmap = HardwareSnapshot.capture(root) }
        File("build/settings-previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
        }
    }

    @Test fun allCategoriesOpenAsSeparatePagesAndReturnToTheSameMenuPosition() {
        launch()
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
        capture("settings-hub")
        SettingsSection.values().forEach { section ->
            open(section)
            rule.onNodeWithContentDescription("返回设置").assertIsDisplayed()
            if (section in listOf(SettingsSection.APPEARANCE, SettingsSection.CALLS, SettingsSection.BATTERY)) capture("detail-${section.name}")
            back()
            rule.onNodeWithTag("settings-entry-${section.name}").assertIsDisplayed()
            rule.onNodeWithTag("settings-detail-${section.name}").assertDoesNotExist()
        }
    }
    @Test fun androidBackReturnsFromDetailAndDockSelectionResetsTheRoute() {
        launch()
        open(SettingsSection.SIGNAL)
        rule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("settings-hub").assertExists()
        open(SettingsSection.CALLS)
        rule.onNodeWithTag("dock-tab-0", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithContentDescription("返回设置").assertDoesNotExist()
        rule.onNodeWithTag("dock-tab-3", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("settings-hub").assertExists()
    }
    @Test fun templateSavesThroughExistingCallbackAndRemainsAfterReopening() {
        launch()
        open(SettingsSection.SMS)
        rule.onNodeWithText("模板设置").performClick()
        rule.onNodeWithTag("sms-template-variables-toggle").assertExists()
        rule.onNodeWithTag("sms-template-variables-list").assertDoesNotExist()
        rule.onNode(hasSetTextAction() and hasText("消息模板")).performTextReplacement("【短信】{sender}：{content}")
        rule.onNodeWithText("保存模板").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("【短信】{sender}：{content}", prefs.getString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE, "")) }
        back()
        open(SettingsSection.SMS)
        rule.onNodeWithText("模板设置").performClick()
        rule.onNode(hasSetTextAction() and hasText("消息模板")).assertTextContains("【短信】{sender}：{content}")
        rule.onNodeWithTag("settings-detail-SMS").performScrollToNode(hasText("发送者过滤"))
        rule.onNodeWithText("发送者过滤").assertIsDisplayed()
        rule.onNodeWithTag("settings-detail-SMS").performScrollToNode(hasText("内容过滤"))
        rule.onNodeWithText("内容过滤").assertIsDisplayed()
    }
    @Test fun fullscreenPreferenceDefaultsOnAndDoesNotAlterForwardingOrWidgetSettings() {
        prefs.edit().putBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, true)
            .putBoolean(Constants.PREF_CONTACT_NAME_ENABLED, true).commit()
        launch()
        open(SettingsSection.APPEARANCE)
        rule.onNodeWithContentDescription("全屏沉浸").assertIsOn().performClick()
        rule.runOnIdle {
            assertFalse(prefs.getBoolean(GLASS_FULLSCREEN, true))
            assertTrue(prefs.getBoolean(Constants.PREF_SIGNAL_REMINDER_ENABLED, false))
            assertTrue(prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false))
        }
        back()
        open(SettingsSection.APPEARANCE)
        rule.onNodeWithContentDescription("全屏沉浸").assertIsOff()
        rule.onNodeWithTag("settings-detail-APPEARANCE").performScrollToNode(hasText("桌面小组件"))
        rule.onNodeWithText("桌面小组件").assertIsDisplayed()
    }
}
