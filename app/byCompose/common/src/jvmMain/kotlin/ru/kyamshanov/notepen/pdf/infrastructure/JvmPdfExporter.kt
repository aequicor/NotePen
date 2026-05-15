package ru.kyamshanov.notepen.pdf.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.port.PdfExporter
import java.io.File

/**
 * JVM [PdfExporter] backed by Apache PDFBox.
 *
 * Each annotated page gets a new content stream in APPEND mode so the original
 * page content is preserved. Strokes are drawn as cubic Bézier paths (Catmull-Rom
 * approximation), matching the Compose canvas rendering in [DrawablePdfPage].
 *
 * PDF coordinate origin is bottom-left; normalised Y is flipped accordingly.
 *
 * @param ioDispatcher dispatcher for blocking file I/O
 */
class JvmPdfExporter(private val ioDispatcher: CoroutineDispatcher) : PdfExporter {

    override suspend fun export(
        sourcePdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        outputPath: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Loader.loadPDF(File(sourcePdfPath)).use { doc ->
                for ((pageIndex, paths) in annotations) {
                    if (pageIndex < 0 || pageIndex >= doc.numberOfPages) continue
                    if (paths.isEmpty()) continue

                    val page = doc.pages[pageIndex]
                    val wPt = page.mediaBox.width
                    val hPt = page.mediaBox.height

                    PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { cs ->
                        for (path in paths) {
                            if (path.points.size < 2) continue
                            val argb = path.colorArgb.toInt()
                            val r = ((argb shr 16) and 0xFF) / 255f
                            val g = ((argb shr 8) and 0xFF) / 255f
                            val b = (argb and 0xFF) / 255f
                            val a = ((argb ushr 24) and 0xFF) / 255f

                            cs.saveGraphicsState()
                            cs.setLineWidth(path.strokeWidth * wPt)
                            cs.setStrokingColor(r, g, b)

                            if (a < 1f) {
                                val gs = PDExtendedGraphicsState()
                                gs.strokingAlphaConstant = a
                                gs.nonStrokingAlphaConstant = a
                                cs.setGraphicsStateParameters(gs)
                            }

                            drawPathOnStream(cs, path.points, wPt, hPt)
                            cs.stroke()
                            cs.restoreGraphicsState()
                        }
                    }
                }
                doc.save(File(outputPath))
            }
        }
    }

    /**
     * Draws a [DrawingPath] as a Catmull-Rom → cubic-Bézier approximation on [cs].
     *
     * Sub-strokes (points with [DrawingPoint.isNewPath] == true) are independent paths.
     * PDF Y-axis is flipped: y_pdf = hPt * (1 - y_normalised).
     */
    private fun drawPathOnStream(
        cs: PDPageContentStream,
        points: List<DrawingPoint>,
        wPt: Float,
        hPt: Float,
    ) {
        val starts = points.indices.filter { i -> i == 0 || points[i].isNewPath }

        for ((si, start) in starts.withIndex()) {
            val end = if (si + 1 < starts.size) starts[si + 1] else points.size
            val seg = points.subList(start, end)

            if (seg.size < 2) continue

            cs.moveTo(seg[0].x * wPt, hPt * (1f - seg[0].y))

            if (seg.size == 2) {
                cs.lineTo(seg[1].x * wPt, hPt * (1f - seg[1].y))
                continue
            }

            for (i in 0 until seg.size - 1) {
                val p0 = if (i > 0) seg[i - 1] else seg[0]
                val p1 = seg[i]
                val p2 = seg[i + 1]
                val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

                val x1 = p1.x * wPt
                val y1 = hPt * (1f - p1.y)
                val x2 = p2.x * wPt
                val y2 = hPt * (1f - p2.y)

                // Control points: same Catmull-Rom formula, but Y deltas are negated
                // because flipping Y reverses the direction of the tangent's Y component.
                val cx1 = x1 + (p2.x - p0.x) * wPt / 6f
                val cy1 = y1 - (p2.y - p0.y) * hPt / 6f
                val cx2 = x2 - (p3.x - p1.x) * wPt / 6f
                val cy2 = y2 + (p3.y - p1.y) * hPt / 6f

                cs.curveTo(cx1, cy1, cx2, cy2, x2, y2)
            }
        }
    }
}
