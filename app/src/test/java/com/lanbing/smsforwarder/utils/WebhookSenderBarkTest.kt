package com.lanbing.smsforwarder.utils

import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.ForwardErrorType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Exercise the public send paths and inspect the actual HTTP JSON payload. */
class WebhookSenderBarkTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        WebhookSender.onMessageSent = null
        server.shutdown()
    }

    private fun reply(code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody("{\"code\":$code}"))
    }

    private fun takeJson(): JSONObject {
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/example-device-key/", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        return JSONObject(request.body.readUtf8())
    }

    private fun url() = server.url("/example-device-key/").toString()

    @Test fun successfulOutgoingMessageUpdatesObserverForEveryChannel() {
        val delivered = mutableListOf<String>()
        WebhookSender.onMessageSent = { delivered += it }
        for (type in ChannelType.values()) {
            reply()
            assertTrue(WebhookSender.sendTextMessage(url(), "【来电提醒】张三", type).success)
            takeJson()
            assertEquals("【来电提醒】张三", delivered.last())
        }
        assertEquals(ChannelType.values().size, delivered.size)
    }

    @Test fun failuresDoNotReplaceLastSuccessfulMessageAndRetrySuccessDoes() {
        val delivered = mutableListOf<String>()
        WebhookSender.onMessageSent = { delivered += it }
        reply()
        assertTrue(WebhookSender.sendTextMessage(url(), "成功消息", ChannelType.GENERIC_WEBHOOK).success)
        takeJson()
        reply(500)
        assertFalse(WebhookSender.sendTextMessage(url(), "失败消息", ChannelType.GENERIC_WEBHOOK).success)
        takeJson()
        assertEquals(listOf("成功消息"), delivered)
        reply()
        assertTrue(WebhookSender.sendTextMessage(url(), "失败消息", ChannelType.GENERIC_WEBHOOK).success)
        takeJson()
        assertEquals(listOf("成功消息", "失败消息"), delivered)
    }

    @Test fun widgetFailureNeverTurnsSuccessfulDeliveryIntoRetry() {
        WebhookSender.onMessageSent = { throw IllegalStateException("launcher unavailable") }
        reply()
        assertTrue(WebhookSender.sendTextMessage(url(), "成功", ChannelType.GENERIC_WEBHOOK).success)
        takeJson()
    }

    @Test fun widgetGetsRenderedSmsBodyIncludingContactAndTemplate() {
        var visible = ""
        WebhookSender.onMessageSent = { visible = it }
        reply()
        assertTrue(WebhookSender.sendSmsForward(url(), "13800138000", "正文内容", null,
            ChannelType.GENERIC_WEBHOOK, true, false, "{sender}：{content}", senderContactName = "张三").success)
        val payload = takeJson()
        assertEquals(payload.getString("body"), visible)
        assertTrue(visible.contains("张三"))
        assertFalse(visible.contains("13800138000"))
    }

    @Test
    fun continuousBatteryAlertsHaveVisibleBarkBodyBothDirections() {
        for (charging in listOf(true, false)) {
            reply()
            val text = formatBatteryStepReminder(50, BatteryStep(50, charging), charging, "SIM 1")
            assertTrue(WebhookSender.sendTextMessage(url(), text, ChannelType.GENERIC_WEBHOOK).success)
            val json = takeJson()
            assertEquals(text, json.getString("body"))
            assertEquals(text, json.getString("message"))
        }
    }

    @Test
    fun callNumberSupplementIsSentAsVisibleBarkBody() {
        reply()
        val text = formatCallNumberSupplement(CallAlert(CallAlertKind.INCOMING, "10086", 100L), "12:51", "SIM 1")
        assertTrue(WebhookSender.sendTextMessage(url(), text, ChannelType.GENERIC_WEBHOOK).success)
        val json = takeJson()
        assertEquals(text, json.getString("body"))
        assertTrue(json.getString("body").contains("来电号码补充"))
    }

    @Test
    fun signalAlertsHaveNonemptyBarkBody() {
        for (signal in MobileSignal.values()) {
            reply()
            val message = formatSignalAlert(signal, "SIM 1")
            assertTrue(WebhookSender.sendTextMessage(url(), message, ChannelType.GENERIC_WEBHOOK).success)
            val json = takeJson()
            assertEquals(message, json.getString("body"))
            assertEquals(message, json.getString("message"))
        }
    }

    @Test
    fun smsBodyPreservesUnicodeQuotesNewlinesAndOriginalWebhookFields() {
        reply()
        val message = "【银行】验证码654321。\n引号：\"中文\"；路径：C:\\短信；emoji：🔑"
        val result = WebhookSender.sendSmsForward(
            url(), "10086", message, "13800000000", ChannelType.GENERIC_WEBHOOK,
            showSenderPhone = true, highlightVerificationCode = true,
            matchedKeyword = "银行", channelName = "Bark"
        )
        assertTrue(result.success)
        val json = takeJson()
        assertTrue(json.getString("body").endsWith(message))
        assertTrue(json.getString("body").contains("来自: 10086"))
        assertEquals(message, json.getString("content"))
        assertEquals("10086", json.getString("sender"))
        assertEquals("13800000000", json.getString("receiver"))
        assertEquals("654321", json.getString("verificationCode"))
        assertEquals("银行", json.getString("matchedKeyword"))
        assertEquals("Bark", json.getString("channelName"))
        assertTrue(json.getLong("timestamp") > 0)
    }

    @Test
    fun customTemplateIsRenderedAsTextInBarkBody() {
        reply()
        val result = WebhookSender.sendSmsForward(
            url(), "10086", "短信正文", "13800000000", ChannelType.GENERIC_WEBHOOK,
            showSenderPhone = false, highlightVerificationCode = false,
            messageTemplate = "来自 {sender}\n本机 {sim}\n{content}", channelName = "Bark"
        )
        assertTrue(result.success)
        val json = takeJson()
        assertEquals("来自 10086\n本机 13800000000\n短信正文", json.getString("body"))
        assertEquals(json.getString("content"), json.getString("body"))
        assertFalse(json.has("sender"))
        assertFalse(json.has("verificationCode"))
    }

    @Test
    fun whitespaceTemplateUsesOriginalSms() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(
            url(), "10086", "原短信", null, ChannelType.GENERIC_WEBHOOK,
            false, false, messageTemplate = " \n\t"
        ).success)
        assertEquals("原短信", takeJson().getString("body"))
    }

    @Test
    fun missingCodeDoesNotProduceAnEmptyBarkNotification() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(
            url(), "10086", "没有验证码的短信", null, ChannelType.GENERIC_WEBHOOK,
            false, true, messageTemplate = "{code}"
        ).success)
        val json = takeJson()
        assertEquals("", json.getString("content"))
        assertEquals("没有验证码的短信", json.getString("body"))
    }

    @Test
    fun channelTestSendsAVisibleBarkBody() {
        reply()
        assertTrue(WebhookSender.sendTestMessage(url(), ChannelType.GENERIC_WEBHOOK, "Bark").success)
        assertTrue(takeJson().getString("body").contains("这是一条测试消息"))
    }

    @Test
    fun batteryAndChargingMessagesKeepMessageAndAddBody() {
        for (message in listOf("电量低于20%", "设备开始充电", "设备结束充电")) {
            reply()
            assertTrue(WebhookSender.sendTextMessage(url(), message, ChannelType.GENERIC_WEBHOOK).success)
            val json = takeJson()
            assertEquals(message, json.getString("message"))
            assertEquals(message, json.getString("body"))
        }
    }

    @Test
    fun otherChannelProtocolsKeepTheirExistingJsonShape() {
        for (type in listOf(ChannelType.WECHAT, ChannelType.DINGTALK, ChannelType.FEISHU)) {
            reply()
            assertTrue(WebhookSender.sendTextMessage(url(), "通道正文", type).success)
            val json = takeJson()
            assertFalse(json.has("body"))
            if (type == ChannelType.FEISHU) {
                assertEquals("text", json.getString("msg_type"))
                assertEquals("通道正文", json.getJSONObject("content").getString("text"))
            } else {
                assertEquals("text", json.getString("msgtype"))
                assertEquals("通道正文", json.getJSONObject("text").getString("content"))
            }
        }
    }

    @Test
    fun invalidDeviceKeyResponseRemainsNonRetryable() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"code\":400,\"message\":\"failed to get device token\"}"))
        val result = WebhookSender.sendTestMessage(url(), ChannelType.GENERIC_WEBHOOK)
        assertFalse(result.success)
        assertEquals(ForwardErrorType.NON_RETRYABLE, result.errorType)
        assertTrue(result.errorMessage.contains("HTTP 400"))
        takeJson()
    }
    @Test
    fun incomingCallTextIsSentAsBarkBody() {
        reply()
        val message = formatCallAlert(CallAlert(CallAlertKind.INCOMING, "10086", 100L), "2026-10-03 12:00:00", "Phone", "SIM 1")
        assertTrue(WebhookSender.sendTextMessage(url(), message, ChannelType.GENERIC_WEBHOOK).success)
        val json = takeJson()
        assertEquals(message, json.getString("body"))
        assertEquals(message, json.getString("message"))
    }

    @Test
    fun unknownMissedCallTextIsSentAsNonemptyBarkBody() {
        reply()
        val message = formatCallAlert(CallAlert(CallAlertKind.MISSED, "", 100L), "12:00", "Phone", "")
        assertTrue(WebhookSender.sendTextMessage(url(), message, ChannelType.GENERIC_WEBHOOK).success)
        assertTrue(takeJson().getString("body").contains("未知号码"))
    }
    @Test
    fun applicationNotificationIsPostedWithVisibleBarkBody() {
        reply()
        val notification = AppNotification("chat-1", "com.tencent.mm", "微信", "张三", "新消息\n引号：\"中文\"", 100L)
        val message = formatAppNotification(notification, "2026-10-03 11:00", "Samsung")
        assertTrue(WebhookSender.sendTextMessage(url(), message, ChannelType.GENERIC_WEBHOOK).success)
        val json = takeJson()
        assertEquals(message, json.getString("body"))
        assertEquals(message, json.getString("message"))
        assertTrue(json.getString("body").contains("新消息"))
    }
    @Test fun matchedContactIsVisibleInBarkBodyWithoutRawSenderNumber() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(url(), "13800138000", "你好", null,
            ChannelType.GENERIC_WEBHOOK, true, false, senderContactName = "张三").success)
        val json = takeJson()
        assertEquals("来自: 张三\n你好", json.getString("body"))
        assertEquals("张三", json.getString("sender"))
        assertEquals("你好", json.getString("content"))
        assertFalse(json.toString().contains("13800138000"))
    }

    @Test fun customSmsTemplateReceivesContactNameWithoutRawNumber() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(url(), "13800138000", "你好", null,
            ChannelType.GENERIC_WEBHOOK, true, false, messageTemplate = "{sender}：{content}", senderContactName = "张三").success)
        assertEquals("张三：你好", takeJson().getString("body"))
    }

    @Test fun hidingSenderPreventsBothNameAndNumberInDefaultBarkPayload() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(url(), "13800138000", "你好", null,
            ChannelType.GENERIC_WEBHOOK, false, false, senderContactName = "张三").success)
        val json = takeJson()
        assertEquals("你好", json.getString("body"))
        assertFalse(json.has("sender"))
        assertFalse(json.toString().contains("13800138000"))
        assertFalse(json.toString().contains("张三"))
    }

    @Test fun enterpriseWechatSmsAlsoDisplaysContactNameOnly() {
        reply()
        assertTrue(WebhookSender.sendSmsForward(url(), "13800138000", "你好", null,
            ChannelType.WECHAT, true, false, senderContactName = "张三").success)
        val json = takeJson()
        assertEquals("来自: 张三\n你好", json.getJSONObject("text").getString("content"))
        assertFalse(json.toString().contains("13800138000"))
    }

}
