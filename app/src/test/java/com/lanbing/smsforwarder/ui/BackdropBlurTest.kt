package com.lanbing.smsforwarder.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackdropBlurTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View
    private lateinit var state: HazeState
    private var area = Rect.Zero
    private var glass by mutableStateOf(false)
    private var reduced by mutableStateOf(false)

    private fun launch() {
        rule.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            LiquidGlassTheme(dark = true, reducedTransparency = reduced) {
                val haze = LocalGlassBackdrop.current!!
                SideEffect { state = haze }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.fillMaxSize().liquidContentBackdrop()) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stripe = 4.dp.toPx()
                            for (i in 0..(size.width / stripe).toInt()) {
                                drawRect(if (i % 2 == 0) Color.Black else Color.White,
                                    topLeft = Offset(i * stripe, 0f), size = Size(stripe, size.height))
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp).height(180.dp)
                        .onGloballyPositioned { area = it.boundsInWindow() }
                        .then(if (glass) Modifier.liquidChrome(RoundedCornerShape(24.dp)) else Modifier)) {
                        Text("清晰文字", Modifier.padding(16.dp), color = Color.White)
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun snapshot(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        rule.runOnIdle { bitmap = HardwareSnapshot.capture(root) }
        val f = File("build/glass-previews/$name.png").apply { parentFile!!.mkdirs() }
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun deviation(bitmap: Bitmap): Double {
        val values = ((area.left + 50).toInt() until (area.right - 50).toInt()).map {
            AndroidColor.red(bitmap.getPixel(it, area.center.y.toInt())).toDouble()
        }
        val average = values.average()
        return sqrt(values.map { (it - average) * (it - average) }.average())
    }
    @Test fun actualBackdropPixelsAreBlurredRatherThanOnlyTinted() {
        launch()
        val before = snapshot("hardware-sharp-source")
        val unblurred = deviation(before)
        assertTrue("Control stripes must be sharp", unblurred > 90)
        rule.runOnIdle { glass = true }
        rule.waitForIdle()
        assertEquals(1, state.areas.count { it.isValid })
        val after = snapshot("hardware-real-backdrop-blur")
        val blurred = deviation(after)
        File("build/glass-previews/hardware-blur-metrics.txt").writeText("sharpStd=$unblurred\nblurredStd=$blurred\nratio=${blurred / unblurred}\n")
        assertTrue("Backdrop deviation $blurred must be <30% of $unblurred; a tint alone cannot pass", blurred < unblurred * .30)
    }
    @Test fun reducedTransparencyDetachesAllBlurAreas() {
        glass = true
        launch()
        assertEquals(1, state.areas.count { it.isValid })
        rule.runOnIdle { reduced = true }
        rule.waitForIdle()
        assertEquals(0, state.areas.size)
        val opaque = snapshot("hardware-reduced-opaque")
        val pixel = opaque.getPixel(area.center.x.toInt(), area.center.y.toInt())
        assertEquals(18, AndroidColor.red(pixel))
        assertEquals(AndroidColor.red(pixel), AndroidColor.blue(pixel))
    }
}
