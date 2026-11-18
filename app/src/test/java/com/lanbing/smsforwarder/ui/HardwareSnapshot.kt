package com.lanbing.smsforwarder.ui

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.media.ImageReader
import android.view.View
import java.nio.ByteOrder

/** Render a real hardware display list, including RenderEffect; a bitmap Canvas cannot do this. */
object HardwareSnapshot {
    fun capture(view: View): Bitmap {
        val width = view.width
        val height = view.height
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        val renderer = HardwareRenderer()
        val node = RenderNode("glass-test-root")
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording()
        check(canvas.isHardwareAccelerated)
        try { view.draw(canvas) } finally { node.endRecording() }
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = checkNotNull(reader.acquireNextImage()) { "No hardware-rendered image" }
            image.use {
                val plane = it.planes[0]
                val buffer = plane.buffer.order(ByteOrder.nativeOrder())
                val pixels = IntArray(width * height)
                for (y in 0 until height) for (x in 0 until width) {
                    pixels[y * width + x] = buffer.getInt(y * plane.rowStride + x * plane.pixelStride)
                }
                return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }
        } finally {
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
        }
    }
}
