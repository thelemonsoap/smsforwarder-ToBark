package com.lanbing.smsforwarder.utils

import android.content.Context
import com.lanbing.smsforwarder.Constants
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PushTemplatesTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences(Constants.PREFS_NAME,Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun appNameCanMoveToTheEndWithoutAnyForcedHeader() {
        PushTemplates.save(prefs,PushTemplateKind.APP_NOTIFICATION,"{{Title}}\n{{Content}}\n来自 {{AppName}}")
        assertEquals("张三\n会议改到下午三点。\n来自 微信",PushMessageFactory.example(PushTemplateKind.APP_NOTIFICATION).render(prefs,"Bark"))
    }
    @Test fun valuesAreInsertedOnceAndUnknownTokensStayLiteral() {
        val result=TemplateTokens.render("{{AppName}}/{CONTENT}/{Unknown}",mapOf("appname" to "{{Content}}","Content" to "{AppName} 🔑"))
        assertEquals("{{Content}}/{AppName} 🔑/{Unknown}",result)
    }
    @Test fun smsAcceptsBothBraceStylesAndKeepsContactNamePrivacy() {
        val p=MessageTemplateRenderer.TemplateParams("13800000000","{Sender} 🔑",contactName="张三")
        assertEquals("张三/{Sender} 🔑/张三",MessageTemplateRenderer.render("{{Sender}}/{CONTENT}/{sender}",p))
    }
    @Test fun everyTypeHasAnIndependentTemplateAndResetRestoresOnlyThatType() {
        for (kind in PushTemplateKind.values()) PushTemplates.save(prefs,kind,"${kind.id}:{{Content}}")
        for (kind in PushTemplateKind.values()) assertTrue(PushMessageFactory.example(kind).render(prefs,"Bark").startsWith(kind.id+":"))
        PushTemplates.save(prefs,PushTemplateKind.CALL_INCOMING,null)
        assertNull(PushTemplates.saved(prefs,PushTemplateKind.CALL_INCOMING))
        assertNotNull(PushTemplates.saved(prefs,PushTemplateKind.CALL_MISSED))
    }
    @Test fun perAppOverrideFallsBackToSharedTemplateWhenRemoved() {
        val example=PushMessageFactory.example(PushTemplateKind.APP_NOTIFICATION)
        PushTemplates.save(prefs,example.kind,"全部 {{AppName}}")
        PushTemplates.save(prefs,example.kind,"单独 {{Title}}","com.example.chat")
        assertEquals("单独 张三",example.render(prefs,"Bark"))
        assertEquals("全部 微信",example.copy(appPackage="com.other.chat").render(prefs,"Bark"))
        PushTemplates.save(prefs,example.kind,null,"com.example.chat")
        assertEquals("全部 微信",example.render(prefs,"Bark"))
    }
    @Test fun withoutOverridesAllPreviousMessagesAreByteForBytePreserved() {
        for (kind in PushTemplateKind.values()) {
            val example=PushMessageFactory.example(kind)
            assertEquals(kind.id,example.defaultMessage,example.render(prefs,"Bark"))
        }
    }
    @Test fun blankRenderingFallsBackToVisibleDefaultRatherThanEmptyMessage() {
        assertEquals("原消息",PushTemplates.render("{{ContactName}}",mapOf("ContactName" to ""),"原消息"))
        assertEquals("原消息",PushTemplates.render(" \n",emptyMap(),"原消息"))
    }
    @Test fun callerTemplatesNeverReintroduceTheNumberWhenAContactMatches() {
        val alert=CallAlert(CallAlertKind.INCOMING,"13800000000",0)
        for (supplement in listOf(false,true)) {
            val example=PushMessageFactory.call(alert,"12:30","Samsung","SIM 1","张三",supplement)
            PushTemplates.save(prefs,example.kind,"{{Caller}} / {Sender} / {{ContactName}}")
            assertEquals("张三 / 张三 / 张三",example.render(prefs,"Bark"))
        }
    }
    @Test fun batteryVariablesContainActualLevelCrossedStepAndChargingState() {
        val example=PushMessageFactory.battery(PushTemplateKind.BATTERY_STEP_DOWN,"原格式",49,false,"SIM 1",step=BatteryStep(50,false),thresholdNote="电量较低",time="12:30",device="Samsung")
        PushTemplates.save(prefs,example.kind,"{{BatteryLevel}}|{{Threshold}}|{{Direction}}|{{BatteryChange}}|{{ChargingState}}|{{ThresholdNote}}|{{PhoneInfo}}|{{Time}}|{{Device}}|{{Channel}}")
        assertEquals("49|50|下降|下降经过50%档位|使用|电量较低|SIM 1|12:30|Samsung|Bark",example.render(prefs,"Bark"))
    }
    @Test fun lteAnd5gCanUseDifferentText() {
        PushTemplates.save(prefs,PushTemplateKind.SIGNAL_LTE,"网络 {{Signal}}")
        PushTemplates.save(prefs,PushTemplateKind.SIGNAL_5G,"已切换：{{Signal}}")
        assertEquals("网络 LTE",PushMessageFactory.example(PushTemplateKind.SIGNAL_LTE).render(prefs,"Bark"))
        assertEquals("已切换：5G",PushMessageFactory.example(PushTemplateKind.SIGNAL_5G).render(prefs,"Bark"))
    }
    @Test fun customAppMessageIsLimitedAfterRenderingWithoutSplittingUnicode() {
        val example=PushMessageFactory.app(AppNotification("test","com.example.chat","微信","标题","🔑中文".repeat(2000),0),"12:30","Samsung")
        PushTemplates.save(prefs,example.kind,"{{AppName}}\n{{Content}}")
        val message=example.render(prefs,"Bark")
        assertTrue(message.toByteArray(Charsets.UTF_8).size<=3000)
        assertTrue(message.endsWith("…（内容已截断）"))
        assertFalse(message.toByteArray().toString(Charsets.UTF_8).contains('\uFFFD'))
    }
    @Test fun oldBackupsLeaveNewTemplatesAloneAndMalformedMapsAreRejected() {
        assertNull(PushTemplates.importedMap(JSONObject("{}"),"pushTemplates"))
        assertEquals(emptyMap<String,String>(),PushTemplates.importedMap(JSONObject("{\"pushTemplates\":{}}"),"pushTemplates"))
        try { PushTemplates.importedMap(JSONObject("{\"pushTemplates\":{\"call_incoming\":123}}"),"pushTemplates");fail("Non-string template must be rejected") } catch (_: IllegalArgumentException) { }
    }
    @Test fun customTemplatesPersistWithAllWhitespaceAndLineBreaks() {
        val value="  {{AppName}}\n\n{{Content}}  "
        PushTemplates.save(prefs,PushTemplateKind.APP_NOTIFICATION,value)
        assertEquals(value,PushTemplates.saved(prefs,PushTemplateKind.APP_NOTIFICATION))
    }
    @Test fun variableMenuListsEveryAcceptedVariableIncludingAliases() {
        for (kind in PushTemplateKind.values()) {
            val expected=if (kind==PushTemplateKind.SMS) MessageTemplateRenderer.getPlaceholderHints().map { it.first.trim('{','}') }.toSet()
                else PushMessageFactory.example(kind).values.keys + setOf("Channel","DefaultMessage")
            val actual=PushTemplates.variables(kind).map { it.name.lowercase(java.util.Locale.ROOT) }
            assertEquals("Duplicate entry for ${kind.id}",actual.size,actual.toSet().size)
            assertEquals(kind.id,expected.map { it.lowercase(java.util.Locale.ROOT) }.toSet(),actual.toSet())
        }
    }
}
