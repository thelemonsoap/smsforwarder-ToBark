package com.lanbing.smsforwarder.ui

import android.app.Activity
import android.graphics.Color
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowInsets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmersiveSystemBarsTest {
    @Test fun systemBarsAndDividerAreTransparentWithNoForcedScrims() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val window = activity.window
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.isStatusBarContrastEnforced = true
            window.isNavigationBarContrastEnforced = true
            ImmersiveSystemBars.apply(activity, dark = true, fullscreen = false)
            assertEquals(Color.TRANSPARENT, window.statusBarColor)
            assertEquals(Color.TRANSPARENT, window.navigationBarColor)
            assertEquals(Color.TRANSPARENT, window.navigationBarDividerColor)
            assertFalse(window.isStatusBarContrastEnforced)
            assertFalse(window.isNavigationBarContrastEnforced)
            assertEquals(0, window.attributes.flags and (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION))
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS != 0)
            assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, window.attributes.layoutInDisplayCutoutMode)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun fullscreenHidesBothSystemBarsAndAllowsTransientSwipe() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val bars = activity.window.insetsController!!
            ImmersiveSystemBars.apply(activity, dark = true, fullscreen = true)
            val hidden = activity.window.decorView.rootWindowInsets
            assertFalse(hidden.isVisible(WindowInsets.Type.statusBars()))
            assertFalse(hidden.isVisible(WindowInsets.Type.navigationBars()))
            assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, bars.systemBarsBehavior)
            ImmersiveSystemBars.apply(activity, dark = true, fullscreen = false)
            val visible = activity.window.decorView.rootWindowInsets
            assertTrue(visible.isVisible(WindowInsets.Type.statusBars()))
            assertTrue(visible.isVisible(WindowInsets.Type.navigationBars()))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun themeChangesKeepSystemIconsLegibleWithoutRestoringContrastBands() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val insets = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            ImmersiveSystemBars.apply(activity, dark = false)
            assertTrue(insets.isAppearanceLightStatusBars)
            assertTrue(insets.isAppearanceLightNavigationBars)
            ImmersiveSystemBars.apply(activity, dark = true)
            assertFalse(insets.isAppearanceLightStatusBars)
            assertFalse(insets.isAppearanceLightNavigationBars)
            assertFalse(activity.window.isStatusBarContrastEnforced)
            assertFalse(activity.window.isNavigationBarContrastEnforced)
        } finally { controller.pause().stop().destroy() }
    }
}
