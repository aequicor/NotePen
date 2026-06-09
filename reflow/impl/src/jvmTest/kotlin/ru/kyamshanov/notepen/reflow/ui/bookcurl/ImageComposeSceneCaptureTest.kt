package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * De-risk: does ImageComposeScene rasterize real text to a non-blank bitmap on this desktop/Skiko?
 * GraphicsLayer.toImageBitmap() returns blank for reader content; ImageComposeScene is the candidate
 * replacement for the page-curl texture capture.
 */
class ImageComposeSceneCaptureTest {
    @Test
    fun rendersTextToNonBlankBitmap() {
        val scene =
            ImageComposeScene(width = 400, height = 200, density = Density(1f)) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    BasicText(
                        text = "Hello reflow page — does this rasterize?",
                        style = TextStyle(color = Color.Black, fontSize = 20.sp),
                    )
                }
            }
        val bmp =
            try {
                scene.render().toComposeImageBitmap()
            } finally {
                scene.close()
            }

        val pm = bmp.toPixelMap()
        var lo = 9f
        var hi = -9f
        var y = 0
        while (y < pm.height) {
            var x = 0
            while (x < pm.width) {
                val c = pm[x, y]
                val l = c.red + c.green + c.blue
                if (l < lo) lo = l
                if (l > hi) hi = l
                x += 4
            }
            y += 4
        }
        assertTrue(hi - lo > 0.1f, "ImageComposeScene must rasterize text to a non-blank bitmap: range=${hi - lo}")
    }
}
