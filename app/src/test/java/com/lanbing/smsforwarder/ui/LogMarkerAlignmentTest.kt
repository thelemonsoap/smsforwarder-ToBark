package com.lanbing.smsforwarder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.LogItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h840dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogMarkerAlignmentTest {
    @get:Rule val rule = createComposeRule()

    private fun verify(fontScale: Float, rtl: Boolean) {
        rule.setContent {
            val actual = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(actual.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                LiquidGlassTheme(dark = true) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        LogItem("[2026-10-31 09:58:02] 电话状态：SIM 1 空闲")
                        LogItem("[2026-10-31 09:58:02] 电话提醒监听已开启：新电话接口，SIM 1；\n通道 Bark")
                        LogItem("应用通知转发成功")
                    }
                }
            }
        }
        rule.waitForIdle()
        val dots = rule.onAllNodesWithTag("log-status-dot", useUnmergedTree = true)
        val texts = rule.onAllNodesWithTag("log-message", useUnmergedTree = true)
        dots.assertCountEquals(3); texts.assertCountEquals(3)
        for (i in 0..2) {
            val layouts = mutableListOf<TextLayoutResult>()
            texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val text = texts[i].fetchSemanticsNode().boundsInRoot
            val dot = dots[i].fetchSemanticsNode().boundsInRoot
            val layout = layouts.single()
            val firstLineCenter = text.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
            assertTrue("Marker $i must align with first line, not the whole record: $dot vs $firstLineCenter",
                abs(dot.center.y - firstLineCenter) <= 1f)
            if (rtl) assertTrue(dot.left > text.right) else assertTrue(dot.right < text.left)
            if (i == 1) assertTrue("Control record must wrap", layout.lineCount >= 2)
        }
    }
    @Test fun singleAndMultipleLineMessagesAlignWithOrWithoutTimestamps() = verify(1f, false)
    @Test fun largerTextAndRtlKeepTheMarkerOnTheFirstLine() = verify(1.5f, true)
}
