package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import ru.kyamshanov.notepen.reflow.api.TextAnchor

/** Точка выделения: блок [blockIndex] и смещение символа [charOffset] в его тексте. */
internal data class SelPoint(
    val blockIndex: Int,
    val charOffset: Int,
)

/**
 * Состояние сквозного (между блоками) выделения ридера. Один экземпляр живёт на
 * корневом контейнере контента; блоки регистрируют через него свою раскладку
 * ([TextLayoutResult]) и собственную [LayoutCoordinates], а также читают свой
 * текущий диапазон выделения для превью — всё ambient'ом ([LocalReflowSelectionState]),
 * чтобы не пробрасывать это параметрами и не раздувать сигнатуры блоков.
 *
 * Границы блока в системе контейнера считаются лениво в момент хит-теста
 * ([boundsOf]) из живых [LayoutCoordinates] — а не кэшируются заранее. Так ни одна
 * ранняя регистрация не теряется из-за порядка позиционирования при (под)композиции
 * списка/пейджера: во время жеста контейнер заведомо позиционирован, и каждый
 * присоединённый блок отдаёт актуальный прямоугольник. Позиции жеста приходят в той
 * же системе координат, поэтому хит-тест и пересчёт в смещение символа — без переводов.
 */
@Stable
internal class ReflowSelectionState {
    private val layouts = mutableStateMapOf<Int, TextLayoutResult>()
    private val blockCoordinates = mutableStateMapOf<Int, LayoutCoordinates>()

    /**
     * Раскладка корневого контейнера контента: относительно неё считаются границы блоков
     * (`localBoundingBoxOf`), а жест приходит в той же системе координат.
     */
    var containerCoordinates: LayoutCoordinates? = null

    /**
     * Немедленный режим выделения (активен маркер): любой drag сразу выделяет, поэтому
     * скролл контента отключаем заранее ([scrollLocked]) — иначе вложенный список/пейджер
     * перехватил бы вертикальный drag раньше жеста выделения на контейнере.
     */
    var immediate by mutableStateOf(false)

    /** Начало выделения (где опустили палец); `null` — выделения нет. */
    var anchor by mutableStateOf<SelPoint?>(null)
        private set

    /** Текущий конец выделения (куда тянем); `null` — выделения нет. */
    var focus by mutableStateOf<SelPoint?>(null)
        private set

    /** Идёт ли выделение прямо сейчас (есть начальная точка). */
    val isActive: Boolean get() = anchor != null

    /**
     * Нужно ли блокировать скролл контента: в немедленном режиме — всегда (drag = выделение),
     * иначе — пока выделение в процессе (после долгого нажатия), чтобы не уезжало под пальцем.
     */
    val scrollLocked: Boolean get() = immediate || isActive

    /**
     * Блок [index] публикует свою [LayoutCoordinates]. Границы в системе контейнера из
     * них считаются лениво при хит-тесте ([usableBlocks]) — поэтому, в отличие от раннего
     * кэширования прямоугольника, регистрация не теряется, даже если блок позиционируется
     * раньше контейнера при (под)композиции списка/пейджера.
     */
    fun reportCoordinates(
        index: Int,
        coordinates: LayoutCoordinates,
    ) {
        blockCoordinates[index] = coordinates
    }

    /** Блок [index] публикует свою раскладку текста (для перевода позиции в смещение). */
    fun reportLayout(
        index: Int,
        layout: TextLayoutResult,
    ) {
        layouts[index] = layout
    }

    /** Блок [index] выходит из композиции — убираем его раскладку/координаты. */
    fun forget(index: Int) {
        layouts.remove(index)
        blockCoordinates.remove(index)
    }

    /**
     * Двигает выделение в позицию [pos] (координаты контейнера): хит-тестит блок и
     * переводит позицию в смещение символа. При [anchoring] = `true` (начало жеста)
     * задаёт и начало, и конец; иначе двигает только конец. Вне блоков — no-op
     * (предыдущая точка сохраняется).
     */
    fun moveTo(
        pos: Offset,
        anchoring: Boolean,
    ) {
        val point = pointAt(pos) ?: return
        if (anchoring) anchor = point
        focus = point
    }

    /** Сбрасывает выделение (отмена/после фиксации). */
    fun clear() {
        anchor = null
        focus = null
    }

    /**
     * Текущий анкер выделения для блока [index] — для превью прямо в тексте
     * ([TextAnchor.charEnd] исключителен). `null`, если блок вне выделения или
     * выделение в этом блоке вырождено.
     */
    fun selectionAnchorFor(index: Int): TextAnchor? {
        val from = anchor
        val to = focus
        return if (from != null && to != null) anchorForBlock(from, to, index) else null
    }

    /**
     * Собирает анкеры по всем покрытым блокам (по одному на блок), упорядоченные по
     * индексу блока. Пусто, если выделение отсутствует или вырождено.
     */
    fun anchorsForSelection(): List<TextAnchor> {
        val from = anchor
        val to = focus
        if (from == null || to == null) return emptyList()
        val lo = minOf(from, to, COMPARATOR)
        val hi = maxOf(from, to, COMPARATOR)
        return (lo.blockIndex..hi.blockIndex).mapNotNull { block -> anchorForBlock(from, to, block) }
    }

    /**
     * Анкер блока [index] для пары точек [from]/[to] (в любом порядке): концы
     * выделения дают частичный диапазон в своём блоке, промежуточные блоки — целиком.
     * `null` — блок вне диапазона или диапазон вырожден. `charEnd` исключителен.
     */
    private fun anchorForBlock(
        from: SelPoint,
        to: SelPoint,
        index: Int,
    ): TextAnchor? {
        val lo = minOf(from, to, COMPARATOR)
        val hi = maxOf(from, to, COMPARATOR)
        val inRange = index in lo.blockIndex..hi.blockIndex
        val length = if (inRange) layouts[index]?.layoutInput?.text?.length else null
        if (length == null) return null
        val startOffset = if (index == lo.blockIndex) lo.charOffset else 0
        val endOffset = if (index == hi.blockIndex) hi.charOffset else length
        val start = startOffset.coerceIn(0, length)
        val end = endOffset.coerceIn(start, length)
        return if (end > start) TextAnchor(index, start, end) else null
    }

    /**
     * Хит-тест позиции [pos] (координаты контейнера) в [SelPoint]: среди готовых к
     * выделению блоков ([usableBlocks]) берёт тот, чьи границы накрывают `y` (между
     * блоками — ближайший по `y`), и переводит позицию в смещение символа. Так как
     * кандидаты заведомо имеют и границы, и раскладку, ближайший по `y` блок всегда
     * даёт [SelPoint] — старт жеста чуть выше/ниже строки всё равно цепляет соседнюю.
     * `null`, если ни один блок ещё не пригоден.
     */
    private fun pointAt(pos: Offset): SelPoint? {
        val usable = usableBlocks()
        val inside = usable.entries.firstOrNull { (_, rect) -> pos.y >= rect.top && pos.y <= rect.bottom }
        val hit =
            inside ?: usable.entries.minByOrNull { (_, rect) ->
                if (pos.y < rect.top) rect.top - pos.y else pos.y - rect.bottom
            }
        return hit?.let { (index, rect) ->
            val layout = layouts.getValue(index)
            SelPoint(index, layout.getOffsetForPosition(Offset(pos.x - rect.left, pos.y - rect.top)))
        }
    }

    /**
     * Блоки, пригодные для выделения прямо сейчас (`index → Rect`): есть и раскладка
     * ([layouts]), и считаемые из живых координат границы в системе контейнера. Считается
     * на месте хит-теста, где контейнер заведомо позиционирован; отсоединённые блоки
     * (вышли из композиции / ещё не позиционированы) пропускаются. Поскольку в выборку
     * попадают только блоки с раскладкой, в [pointAt] её чтение безопасно.
     */
    private fun usableBlocks(): Map<Int, Rect> {
        val container = containerCoordinates?.takeIf { it.isAttached } ?: return emptyMap()
        return blockCoordinates
            .mapNotNull { (index, coordinates) ->
                if (index !in layouts || !coordinates.isAttached) return@mapNotNull null
                index to container.localBoundingBoxOf(coordinates, clipBounds = false)
            }.toMap()
    }

    private companion object {
        /** Лексикографический порядок точек: сначала по блоку, затем по смещению. */
        val COMPARATOR: Comparator<SelPoint> =
            compareBy({ it.blockIndex }, { it.charOffset })
    }
}

/**
 * Ambient-доступ к сквозному выделению для блоков ридера. По умолчанию — отдельный
 * «пустой» экземпляр (вне ридера регистрация ни на что не влияет).
 */
internal val LocalReflowSelectionState: ProvidableCompositionLocal<ReflowSelectionState> =
    staticCompositionLocalOf { ReflowSelectionState() }
