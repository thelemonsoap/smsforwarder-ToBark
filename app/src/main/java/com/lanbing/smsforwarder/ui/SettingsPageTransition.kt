package com.lanbing.smsforwarder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex

/** Finite page motion with the active page above outgoing pages, including interrupted returns. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsPageTransition(section: SettingsSection?, content: @Composable (SettingsSection?) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val transition = updateTransition(section, label = "settingsPage")
    val forward = transition.currentState == null && section != null
    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
    Box(Modifier.fillMaxSize().clipToBounds().testTag("settings-transition")) {
        // Hidden slots do not compose their settings controls or run their effects.
        (listOf<SettingsSection?>(null) + SettingsSection.values()).forEach { page ->
            transition.AnimatedVisibility(
                visible = { it == page },
                modifier = Modifier.fillMaxSize().zIndex(if (page == section) 1f else 0f),
                enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { sign * it / 5 } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -sign * it / 12 } + fadeOut(tween(180)),
            ) {
                Box(Modifier.fillMaxSize().testTag("settings-frame-${page?.name ?: "hub"}")) { content(page) }
            }
        }
    }
}
