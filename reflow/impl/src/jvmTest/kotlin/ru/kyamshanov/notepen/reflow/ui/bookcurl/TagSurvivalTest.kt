package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards that captureReflowTexture returns an sRGB-TAGGED bitmap (survives to every draw site). */
class TagSurvivalTest {
    @Test
    fun captureReturnsSrgbTaggedBitmap() {
        val content: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(Color(0xFFF2EAD9)))
        }
        val bmp = runBlocking { captureReflowTexture(48, 48, Density(2f), content) }
        assertNotNull(bmp, "capture returned null")
        val cs = bmp.asSkiaBitmap().colorSpace
        println("CAPTURE colorSpace isSRGB=${cs?.isSRGB}")
        assertTrue(cs?.isSRGB == true, "captured texture must be sRGB-tagged so the GPU color-manages it")
    }
}
