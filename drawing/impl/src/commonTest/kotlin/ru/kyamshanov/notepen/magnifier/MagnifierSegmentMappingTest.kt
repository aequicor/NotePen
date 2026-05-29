package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Инвариант лупы: panel-local → page-normalized (ввод пера) и обратное
 * page-normalized → panel-local (рендер) должны быть точными взаимными
 * обратными для **любого** размера панели — иначе содержимое уезжает
 * относительно курсора при ресайзе окна.
 */
class MagnifierSegmentMappingTest {
    private val singleSeg =
        MagnifierPageSegment(
            pageIndex = 0,
            targetOnPage = Rect(0.4f, 0.4f, 0.6f, 0.6f),
            panelTopFrac = 0f,
            panelBottomFrac = 1f,
        )

    // Нижний сегмент мульти-страничного выделения: занимает нижнюю половину
    // панели (panelTopFrac = 0.5). Именно для таких сегментов важен ненулевой
    // segTop в рендере — наивное «убрать segTop» сломало бы этот кейс.
    private val lowerSeg =
        MagnifierPageSegment(
            pageIndex = 1,
            targetOnPage = Rect(0.1f, 0.0f, 0.3f, 0.25f),
            panelTopFrac = 0.5f,
            panelBottomFrac = 1f,
        )

    @Test
    fun `round-trip is identity for single segment across panel sizes`() {
        assertRoundTrip(singleSeg)
    }

    @Test
    fun `round-trip is identity for lower multi-page segment across panel sizes`() {
        assertRoundTrip(lowerSeg)
    }

    @Test
    fun `segment top maps to panel strip top`() {
        // page-y == target.top должна лечь ровно на верх полосы сегмента.
        val panel = Size(300f, 800f)
        val local = pageToPanelLocalInSegment(Offset(0.2f, 0.0f), panel, lowerSeg)
        assertNear(lowerSeg.panelTopFrac * panel.height, local.y) // 400
    }

    @Test
    fun `segment bottom maps to panel strip bottom`() {
        val panel = Size(300f, 800f)
        val local = pageToPanelLocalInSegment(Offset(0.2f, 0.25f), panel, lowerSeg)
        assertNear(lowerSeg.panelBottomFrac * panel.height, local.y) // 800
    }

    /**
     * Симуляция ресайза: при разных `panel.height` точка под одним и тем же
     * пикселем (в пределах полосы сегмента) должна маппиться в одну и ту же
     * page-точку И возвращаться в тот же пиксель. Покрывает корень дефекта
     * вертикального дрейфа на ресайзе.
     *
     * fy сэмплируем только внутри полосы `panelTopFrac..panelBottomFrac` —
     * вход вне полосы контроллер клампит (точка не принадлежит этому сегменту),
     * поэтому round-trip там и не обязан быть тождественным.
     */
    private fun assertRoundTrip(segment: MagnifierPageSegment) {
        val sizes =
            listOf(
                Size(400f, 200f),
                Size(400f, 400f), // square
                Size(400f, 800f), // resized taller
                Size(640f, 240f), // resized wider/shorter
                Size(120f, 120f), // min
            )
        val top = segment.panelTopFrac
        val bottom = segment.panelBottomFrac
        for (panel in sizes) {
            for (fx in listOf(0f, 0.5f, 1f)) {
                for (t in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                    val fy = top + t * (bottom - top)
                    val px = Offset(fx * panel.width, fy * panel.height)
                    val page = panelLocalToPageInSegment(px, panel, segment)
                    val back = pageToPanelLocalInSegment(page, panel, segment)
                    assertNear(px.x, back.x, msg = "x @ panel=$panel f=($fx,$fy)")
                    assertNear(px.y, back.y, msg = "y @ panel=$panel f=($fx,$fy)")
                }
            }
        }
    }

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-3f,
        msg: String = "",
    ) {
        assertTrue(
            abs(expected - actual) <= eps,
            "Expected $expected, got $actual (diff > $eps) $msg",
        )
    }
}
