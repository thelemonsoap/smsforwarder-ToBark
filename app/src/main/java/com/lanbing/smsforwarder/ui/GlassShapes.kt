package com.lanbing.smsforwarder.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared outer geometry; nested dock selection follows the same 4dp inset. */
object GlassShapes {
    val containerRadius = 26.dp
    val container = RoundedCornerShape(containerRadius)
    val dockSelection = RoundedCornerShape(containerRadius - 4.dp)
    // A bottom-attached sheet must keep its lower edge square to cover the dock corners.
    val bottomSheet = RoundedCornerShape(topStart = containerRadius, topEnd = containerRadius)
}
