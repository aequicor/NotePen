package ru.kyamshanov.notepen.pdf.infrastructure

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import java.io.File
import java.io.FileOutputStream

/**
 * Android [PdfExporter] that flattens annotations by:
 *  1. Rendering each source page to a [Bitmap] using [PdfRenderer];
 *  2. Drawing annotation strokes on top via [Canvas];
 *  3. Embedding the bitmaps into a new [PdfDocument] written to [outputPath].
 *
 * The rasterisation resolution is [EXPORT_WIDTH_PX] pixels wide; height is
 * scaled to preserve the page aspect ratio.
 *
 * @param ioDispatcher dispatcher for blocking file I/O and rendering
 */
class AndroidPdfExporter(
    private val ioDispatcher: CoroutineDispatcher,
) : PdfExporter {
    override suspend fun export(
        sourcePdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        outputPath: String,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val sourceFile = File(sourcePdfPath)
                ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        PdfDocument().use { outDoc ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                                    val bitmapWidth = EXPORT_WIDTH_PX
                                    val bitmapHeight = (bitmapWidth * aspectRatio).toInt()

                                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                    annotations[i]?.let { paths ->
                                        val canvas = Canvas(bitmap)
                                        drawPaths(canvas, paths, bitmapWidth.toFloat(), bitmapHeight.toFloat())
                                    }

                                    val pageInfo = PdfDocument.PageInfo.Builder(bitmapWidth, bitmapHeight, i + 1).create()
                                    val outPage = outDoc.startPage(pageInfo)
                                    outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                                    outDoc.finishPage(outPage)
                                    bitmap.recycle()
                                }
                            }
                            FileOutputStream(outputPath).use { fos -> outDoc.writeTo(fos) }
                        }
                    }
                }
            }
        }

    private fun drawPaths(
        canvas: Canvas,
        paths: List<DrawingPath>,
        w: Float,
        h: Float,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        for (path in paths) {
            if (path.points.size < 2) continue
            val argb = path.colorArgb.toInt()
            paint.color = argb
            paint.strokeWidth = path.strokeWidth * w
            canvas.drawPath(toCatmullRomPath(path.points, w, h), paint)
        }
    }

    private fun toCatmullRomPath(
        points: List<DrawingPoint>,
        w: Float,
        h: Float,
    ): Path =
        Path().apply {
            if (points.isEmpty()) return@apply
            val starts = points.indices.filter { i -> i == 0 || points[i].isNewPath }

            for ((si, start) in starts.withIndex()) {
                val end = if (si + 1 < starts.size) starts[si + 1] else points.size
                val seg = points.subList(start, end)

                moveTo(seg[0].x * w, seg[0].y * h)
                if (seg.size < 2) continue
                if (seg.size == 2) {
                    lineTo(seg[1].x * w, seg[1].y * h)
                    continue
                }

                for (i in 0 until seg.size - 1) {
                    val p0 = if (i > 0) seg[i - 1] else seg[0]
                    val p1 = seg[i]
                    val p2 = seg[i + 1]
                    val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

                    val x1 = p1.x * w
                    val y1 = p1.y * h
                    val x2 = p2.x * w
                    val y2 = p2.y * h

                    cubicTo(
                        x1 + (p2.x - p0.x) * w / 6f,
                        y1 + (p2.y - p0.y) * h / 6f,
                        x2 - (p3.x - p1.x) * w / 6f,
                        y2 - (p3.y - p1.y) * h / 6f,
                        x2,
                        y2,
                    )
                }
            }
        }

    companion object {
        private const val EXPORT_WIDTH_PX = 2480
    }
}

private fun PdfRenderer.use(block: (PdfRenderer) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

private fun PdfRenderer.Page.use(block: (PdfRenderer.Page) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

private fun PdfDocument.use(block: (PdfDocument) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
