package com.lanbing.smsforwarder.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.utils.*
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
@Config(sdk=[34],qualifiers="zh-rCN-w360dp-h840dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PushTemplateUiTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var root: View
    private val context get()=RuntimeEnvironment.getApplication()
    private val prefs get()=context.getSharedPreferences(Constants.PREFS_NAME,Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().putString(Constants.PREF_APP_NOTIFICATION_PACKAGES,"[\"com.example.chat\"]")
        .putString(Constants.PREF_APP_NOTIFICATION_KNOWN_APPS,"{\"com.example.chat\":\"微信\"}").commit() }
    private fun launch(fontScale:Float=1f) {
        rule.setContent {
            val view=LocalView.current;val density=LocalDensity.current
            SideEffect { root=view.rootView }
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                LiquidGlassTheme(dark=true) {
                    var sms by remember { mutableStateOf("{content}") }
                    Scaffold(topBar={ LiquidAppHeader(false,{},statusInsets=WindowInsets(top=24.dp)) },
                        bottomBar={ LiquidNavigationBar(3,{},navigationInsets=WindowInsets(bottom=24.dp)) }) { insets ->
                        CompositionLocalProvider(LocalGlassInsets provides insets) {
                            LazyColumn(Modifier.fillMaxSize().liquidContentBackdrop().padding(horizontal=16.dp).testTag("template-settings-list"),contentPadding=glassPagePadding()) {
                                item { PushTemplateSettings(sms) { sms=it;prefs.edit().putString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE,it).apply() } }
                            }
                        }
                    }
                }
            }
        };rule.waitForIdle()
    }
    private fun capture(name:String) {
        lateinit var image:Bitmap
        rule.runOnIdle {
            val window=WindowInspector.getGlobalWindowViews().lastOrNull { it !== root && it.isAttachedToWindow && it.width>0 && it.height>0 } ?: root
            image=HardwareSnapshot.capture(window)
        }
        File("build/template-previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    private fun clickSetting(tag:String) {
        val node=rule.onNodeWithTag(tag).performScrollTo()
        val box=node.fetchSemanticsNode().boundsInRoot
        val header=rule.onNodeWithTag("app-header",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val dock=rule.onNodeWithTag("navigation-dock",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val delta=if (box.bottom>dock.top-32) box.bottom-dock.top+32 else if (box.top<header.bottom+32) box.top-header.bottom-32 else 0f
        if (delta!=0f) rule.onNodeWithTag("template-settings-list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f,delta) }
        node.performClick()
    }
    @Test fun variableInsertionReplacesSelectedTextAtTheActualCursor() {
        val value=insertTemplateVariable(TextFieldValue("前面选中后面",TextRange(2,4)),"{{AppName}}")
        assertEquals("前面{{AppName}}后面",value.text);assertEquals(TextRange(13),value.selection)
    }
    @Test fun appNameMovesToTheEndThenSaveCancelAndResetBehaveIndependently() {
        launch();capture("templates-list")
        clickSetting("template-row-app_notification")
        rule.onNodeWithTag("push-template-input").performTextReplacement("{{Title}}\n{{Content}}\n来自：")
        rule.onNodeWithTag("template-variables-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("template-variable-AppName").performScrollTo().performClick()
        rule.onNodeWithTag("push-template-preview").assertTextEquals("张三\n会议改到下午三点。\n来自：微信")
        rule.onNodeWithTag("template-variables-toggle").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(300)
        capture("app-template-editor")
        rule.onNodeWithTag("save-push-template").performClick()
        assertEquals("{{Title}}\n{{Content}}\n来自：{{AppName}}",PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION))
        clickSetting("template-row-app_notification")
        rule.onNodeWithTag("push-template-input").performTextReplacement("未保存")
        rule.onNodeWithContentDescription("关闭模板编辑").performClick()
        assertFalse(PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION)!!.contains("未保存"))
        clickSetting("template-row-app_notification")
        rule.onNodeWithTag("reset-push-template").performClick()
        assertNull(PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION))
    }
    @Test fun perAppSaveDoesNotChangeSharedTemplateAndSmsUsesExistingKey() {
        launch();PushTemplates.save(prefs,PushTemplateKind.APP_NOTIFICATION,"通用 {{AppName}}")
        assertEquals(setOf("com.example.chat"),NotificationSettings.selectedPackages(prefs))
        rule.onNodeWithText("按应用单独设置（1）").assertExists()
        clickSetting("app-template-overrides")
        clickSetting("app-template-com.example.chat")
        rule.onNodeWithTag("push-template-input").performTextReplacement("专属 {{Content}}")
        rule.onNodeWithTag("save-push-template").performClick()
        assertEquals("专属 {{Content}}",PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION,"com.example.chat"))
        assertEquals("通用 {{AppName}}",PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION))
        clickSetting("template-row-sms")
        rule.onNodeWithTag("push-template-input").performTextReplacement("{{Sender}}：{{Content}}")
        rule.onNodeWithTag("save-push-template").performClick()
        assertEquals("{{Sender}}：{{Content}}",prefs.getString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE,null))
    }
    @Test @Config(qualifiers="zh-rCN-w264dp-h840dp-xhdpi")
    fun narrowLargeFontEditorKeepsSaveAndCloseAccessible() {
        launch(1.5f)
        clickSetting("template-row-app_notification")
        capture("editor-narrow-before-save")
        rule.onNodeWithTag("save-push-template").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭模板编辑").assertIsDisplayed()
        rule.onNodeWithTag("template-variables-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("template-variable-Content").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("save-push-template").performClick()
        assertNotNull(PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION))
    }
    @Test fun variableSubmenuStartsClosedShowsEveryVariableAndKeepsTheDraft() {
        launch()
        clickSetting("template-row-battery_step_down")
        val draft="当前电量：{{BatteryLevel}}%，{{ChargingState}}"
        rule.onNodeWithTag("push-template-input").performTextReplacement(draft)
        rule.onNodeWithTag("template-variables-list").assertDoesNotExist()
        rule.onNodeWithTag("template-variables-toggle").assertHasClickAction()
        rule.onNodeWithTag("push-template-preview").assertTextEquals("当前电量：50%，使用")
        capture("variables-collapsed")
        rule.onNodeWithTag("template-variables-toggle").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(300)
        val variables=PushTemplates.variables(PushTemplateKind.BATTERY_STEP_DOWN)
        variables.forEach { rule.onNodeWithTag("template-variable-${it.name}").assertExists() }
        capture("variables-expanded")
        rule.onNodeWithTag("template-variable-DefaultMessage").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("template-variables-toggle").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("template-variables-list").assertDoesNotExist()
        rule.onNodeWithTag("push-template-input").assertTextContains(draft)
        rule.onNodeWithTag("push-template-preview").assertTextEquals("当前电量：50%，使用")
        rule.onNodeWithTag("save-push-template").performClick()
        assertEquals(draft,PushTemplates.saved(prefs,PushTemplateKind.BATTERY_STEP_DOWN))
    }
}
