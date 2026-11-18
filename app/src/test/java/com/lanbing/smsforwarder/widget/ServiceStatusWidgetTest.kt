package com.lanbing.smsforwarder.widget

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.MainActivity
import com.lanbing.smsforwarder.R
import com.lanbing.smsforwarder.SmsForegroundService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceStatusWidgetTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    @Before fun prepare() {
        prefs.edit().clear().commit()
        context.getSharedPreferences("widget_last_sent", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun closedOrUnconsentedNeverClaimsRunningEvenIfServiceExists() {
        assertEquals("请先完成设置", ServiceStatusWidget.statusText(false, true, true))
        assertEquals("请先完成设置", ServiceStatusWidget.statusText(false, false, false))
        assertEquals("转发已关闭", ServiceStatusWidget.statusText(true, false, true))
        assertEquals("转发已关闭", ServiceStatusWidget.statusText(true, false, false))
        assertEquals("服务未运行", ServiceStatusWidget.statusText(true, true, false))
        assertEquals("服务运行中", ServiceStatusWidget.statusText(true, true, true))
    }
    @Test fun onlyOwnForegroundServiceCountsAsRunning() {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val service = ActivityManager.RunningServiceInfo().apply {
            this.service = ComponentName(context, SmsForegroundService::class.java)
            foreground = true
        }
        shadowOf(manager).setServices(listOf(service))
        assertTrue(ServiceStatusWidget.isServiceRunning(context))
        service.foreground = false
        assertFalse(ServiceStatusWidget.isServiceRunning(context))
        service.foreground = true
        service.service = ComponentName(context, MainActivity::class.java)
        assertFalse(ServiceStatusWidget.isServiceRunning(context))
        shadowOf(manager).setServices(emptyList())
        assertFalse(ServiceStatusWidget.isServiceRunning(context))
    }
    private fun views(running: Boolean): View = ServiceStatusWidget.buildViews(context, running)
        .apply(context, FrameLayout(context))
    @Test fun rendersStatusAndLatestSuccessfulMessage() {
        prefs.edit().putBoolean(Constants.PREF_PRIVACY_AGREED, true).putBoolean(Constants.PREF_ENABLED, true).commit()
        SentMessageStore.record(context, "【短信转发】张三\n会议改到下午三点。", 1793354400000L)
        for ((name, running, enabled, expected) in listOf(
            State("running", true, true, "服务运行中"),
            State("stopped", false, true, "服务未运行"),
            State("disabled", false, false, "转发已关闭"))) {
            prefs.edit().putBoolean(Constants.PREF_ENABLED, enabled).commit()
            val view = views(running)
            assertEquals(expected, view.findViewById<TextView>(R.id.widget_status).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.widget_message).text.contains("会议改到下午三点"))
            assertTrue(view.findViewById<TextView>(R.id.widget_detail).text.startsWith("最近发送"))
            render(view, name)
        }
    }
    @Test fun cardOpensAppWithoutStartingServiceOrRefreshingItself() {
        val view = views(false)
        view.findViewById<View>(R.id.widget_root).performClick()
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
        assertNull(shadowOf(context).nextStartedService)
        assertTrue(shadowOf(context).broadcastIntents.none { it.component?.className == ServiceStatusWidget::class.java.name })
    }
    @Test fun repeatedStateDoesNotRedrawButSuccessfulSendDoes() {
        prefs.edit().putBoolean(Constants.PREF_PRIVACY_AGREED, true).putBoolean(Constants.PREF_ENABLED, true).commit()
        val manager = AppWidgetManager.getInstance(context)
        val widgetManager = shadowOf(manager)
        val id = widgetManager.createWidget(ServiceStatusWidget::class.java, R.layout.widget_service_status)
        val first = widgetManager.getViewFor(id)
        first.findViewById<TextView>(R.id.widget_message).text = "unchanged sentinel"
        ServiceStatusWidget.updateAll(context, running = false)
        assertEquals("unchanged sentinel", widgetManager.getViewFor(id).findViewById<TextView>(R.id.widget_message).text.toString())
        SentMessageStore.record(context, "【电量提醒】电量 50%", 500L)
        assertEquals("【电量提醒】电量 50%", widgetManager.getViewFor(id).findViewById<TextView>(R.id.widget_message).text.toString())
        prefs.edit().putBoolean(Constants.PREF_ENABLED, false).commit()
        ServiceStatusWidget.updateAll(context, running = false)
        assertEquals("转发已关闭", widgetManager.getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals("【电量提醒】电量 50%", widgetManager.getViewFor(id).findViewById<TextView>(R.id.widget_message).text.toString())
    }
    @Test fun applicationWiresSuccessfulSendsToPersistentSnapshotButIgnoresFailures() {
        assertTrue(context is com.lanbing.smsforwarder.ForwarderApplication)
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            val url = server.url("/test/").toString()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
            assertTrue(com.lanbing.smsforwarder.utils.WebhookSender.sendTextMessage(url, "已成功发送", com.lanbing.smsforwarder.ChannelType.GENERIC_WEBHOOK).success)
            assertEquals("已成功发送", SentMessageStore.read(context).text)
            val timestamp = SentMessageStore.read(context).sentAt
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(503))
            assertFalse(com.lanbing.smsforwarder.utils.WebhookSender.sendTextMessage(url, "本次失败", com.lanbing.smsforwarder.ChannelType.GENERIC_WEBHOOK).success)
            assertEquals("已成功发送", SentMessageStore.read(context).text)
            assertEquals(timestamp, SentMessageStore.read(context).sentAt)
        } finally { server.shutdown() }
    }
    @Test fun snapshotPersistsWithoutWidgetAndDoesNotExposeItBeforeConsent() {
        SentMessageStore.record(context, "测试正文".repeat(500), 1234L)
        assertEquals(1200, SentMessageStore.read(context).text.length)
        assertEquals(1234L, SentMessageStore.read(context).sentAt)
        assertFalse(views(false).findViewById<TextView>(R.id.widget_message).text.contains("测试正文"))
    }
    @Test fun widgetProviderHasNoPeriodicRefresh() {
        val component = ComponentName(context, ServiceStatusWidget::class.java)
        val info = context.packageManager.getReceiverInfo(component, android.content.pm.PackageManager.GET_META_DATA)
        assertFalse(info.exported)
        val xml = info.loadXmlMetaData(context.packageManager, "android.appwidget.provider")!!
        while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) xml.next()
        assertEquals("appwidget-provider", xml.name)
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals(0, xml.getAttributeIntValue(ns, "updatePeriodMillis", -1))
        assertEquals(R.layout.widget_service_status, xml.getAttributeResourceValue(ns, "initialLayout", 0))
        xml.close()
    }
    private data class State(val name: String, val running: Boolean, val enabled: Boolean, val expected: String)
    private fun render(view: View, name: String) {
        val width = 660; val height = 460
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/widget-previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
