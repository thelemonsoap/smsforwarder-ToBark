package com.lanbing.smsforwarder.utils

import android.content.Context
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.ExportConfig
import com.lanbing.smsforwarder.widget.SentMessageStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class PushTemplateDeliveryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(Constants.PREFS_NAME,Context.MODE_PRIVATE)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun actualBarkRequestAndWidgetSnapshotUseTheRenderedBody() {
        val server=MockWebServer();server.start()
        try {
            PushTemplates.save(prefs,PushTemplateKind.APP_NOTIFICATION,"{{Content}}\n[{{AppName}}] {{Channel}}")
            server.enqueue(MockResponse().setResponseCode(200))
            val text=PushMessageFactory.example(PushTemplateKind.APP_NOTIFICATION).render(prefs,"Bark")
            assertTrue(WebhookSender.sendTextMessage(server.url("/device/").toString(),text,ChannelType.GENERIC_WEBHOOK).success)
            val json=JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("会议改到下午三点。\n[微信] Bark",json.getString("body"))
            assertEquals(json.getString("body"),json.getString("message"))
            assertEquals(text,SentMessageStore.read(context).text)
        } finally { server.shutdown() }
    }
    private fun config()=ExportConfig(emptyList(),emptyList(),true,true,true,
        batteryReminderEnabled=false,lowBatteryReminderEnabled=true,highBatteryReminderEnabled=true,
        chargingReminderEnabled=true,batteryReminderChannelId=null,lowBatteryThreshold=20,highBatteryThreshold=90,
        customSim1Phone=null,customSim2Phone=null,startOnBoot=false,
        globalMessageTemplate="{{Sender}}：{{Content}}",
        pushTemplates=mapOf("app_notification" to "{{Content}}\n来自 {{AppName}}","signal_lte" to "LTE {{Time}}"),
        appNotificationTemplates=mapOf("com.example.chat" to "专属 {{Title}}"))
    @Test fun exportAndImportRoundTripBothSharedAndPerAppTemplates() {
        val json=ConfigManager.generateConfigJson(config())
        var completed=false
        ConfigManager.importConfigFromJson(context,json) { completed=true }
        assertTrue(completed)
        assertEquals(config().pushTemplates,PushTemplates.readMap(prefs,Constants.PREF_PUSH_TEMPLATES))
        assertEquals(config().appNotificationTemplates,PushTemplates.readMap(prefs,Constants.PREF_APP_PUSH_TEMPLATES))
        assertEquals(config().globalMessageTemplate,prefs.getString(Constants.PREF_GLOBAL_MESSAGE_TEMPLATE,null))
    }
    @Test fun importingAnOlderBackupDoesNotEraseNewTemplates() {
        PushTemplates.save(prefs,PushTemplateKind.SIGNAL_5G,"5G {{Time}}")
        val json=JSONObject(ConfigManager.generateConfigJson(config())).apply { remove("pushTemplates");remove("appNotificationTemplates") }
        var completed=false;ConfigManager.importConfigFromJson(context,json.toString()) { completed=true }
        assertTrue(completed);assertEquals("5G {{Time}}",PushTemplates.saved(prefs,PushTemplateKind.SIGNAL_5G))
    }
    @Test fun invalidTemplateImportCannotPartlyOverwriteExistingChannels() {
        val channel=Channel("old","原通道",ChannelType.GENERIC_WEBHOOK,"https://example.invalid/")
        ChannelLoader.saveChannels(prefs,listOf(channel))
        val json=JSONObject(ConfigManager.generateConfigJson(config())).put("pushTemplates",JSONObject().put("signal_lte",42))
        var completed=false;ConfigManager.importConfigFromJson(context,json.toString()) { completed=true }
        assertFalse(completed);assertEquals(listOf(channel),ChannelLoader.loadChannels(prefs))
    }
}
