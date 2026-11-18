package com.lanbing.smsforwarder.widget

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.R
import com.lanbing.smsforwarder.ui.HardwareSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetMaterialTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun materialTransmitsWallpaperKeepsTextReadableAndClipsItsGrain() {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Constants.PREF_PRIVACY_AGREED, true)
            .putBoolean(Constants.PREF_ENABLED, true).commit()

        val white = render(ColorDrawable(Color.WHITE), "control-white", null)
        val black = render(ColorDrawable(Color.BLACK), "control-black", null)
        // Sample a text-free interior band. An opaque material would transmit no difference.
        val brightValues = (110..610).map { Color.red(white.getPixel(it, 530)) }
        val darkValues = (110..610).map { Color.red(black.getPixel(it, 530)) }
        val transmitted = brightValues.zip(darkValues).map { (a, b) -> a - b }.average()
        assertTrue("Expected roughly 40% wallpaper transmission, got $transmitted/255",
            transmitted in 88.0..115.0)
        assertTrue("Small light text must remain legible even above white wallpaper",
            contrast(Color.rgb(243, 243, 243), brightValues.max()) >= 4.5)
        for ((image, backdrop) in listOf(white to Color.WHITE, black to Color.BLACK)) {
            for ((x, y) in listOf(31 to 91, 688 to 91, 31 to 548, 688 to 548)) {
                assertEquals("Grain must not leak outside the rounded card", backdrop, image.getPixel(x, y))
            }
        }
        render(SampleWallpaper(light = true), "material-light", "浅色测试壁纸")
        render(SampleWallpaper(light = false), "material-dark", "深色测试壁纸")
        File("build/widget-previews/material-metrics.txt").writeText(
            "wallpaperTransmission=$transmitted/255\nsmallTextContrast=" +
                contrast(Color.rgb(243, 243, 243), brightValues.max()) +
                "\nThese are native RemoteViews renders on test backgrounds, not launcher screenshots.\n" +
                "Static grain and translucency only; wallpaper is not blurred or read.\n")
    }

    private fun render(backdrop: Drawable, name: String, label: String?): Bitmap {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val host = FrameLayout(activity).apply { background = backdrop }
            val widget = ServiceStatusWidget.buildViews(context, running = true,
                latest = SentMessageStore.Snapshot("【短信转发】张三\n会议改到下午三点。", 1793404800000L))
                .apply(activity, host)
            host.addView(widget, FrameLayout.LayoutParams(660, 460).apply {
                leftMargin = 30; topMargin = 90
            })
            if (label != null) host.addView(TextView(activity).apply {
                text = label
                textSize = 12f
                setTextColor(if (name.endsWith("light")) Color.DKGRAY else Color.LTGRAY)
            }, FrameLayout.LayoutParams(660, 42).apply { leftMargin = 34; topMargin = 28 })
            activity.setContentView(host)
            host.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(620, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 720, 620)
            val image = HardwareSnapshot.capture(host)
            File("build/widget-previews/$name.png").apply { parentFile!!.mkdirs() }
                .outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return image
        } finally { controller.pause().stop().destroy() }
    }

    private fun contrast(text: Int, grey: Int): Double {
        fun linear(value: Int): Double = (value / 255.0).let {
            if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4)
        }
        val textLuminance = .2126 * linear(Color.red(text)) + .7152 * linear(Color.green(text)) +
            .0722 * linear(Color.blue(text))
        return (textLuminance + .05) / (linear(grey) + .05)
    }

    private class SampleWallpaper(private val light: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            paint.shader = LinearGradient(0f, 0f, w, h,
                if (light) Color.rgb(232, 229, 221) else Color.rgb(28, 32, 43),
                if (light) Color.rgb(168, 190, 202) else Color.rgb(53, 68, 77), Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
            paint.shader = RadialGradient(w * .8f, h * .25f, w * .7f,
                if (light) Color.rgb(237, 219, 191) else Color.rgb(75, 60, 75),
                Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
