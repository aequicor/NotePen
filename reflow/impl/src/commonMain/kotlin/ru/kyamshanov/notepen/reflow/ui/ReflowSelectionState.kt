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

/** Конец выделения, который тянет ручка-курсор: [ANCHOR] — начало, [FOCUS] — конец. */
internal enum class SelectionEnd { ANCHOR, FOCUS }

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
     * переводит позицию в смещение символа. Вне блоков — no-op (предыдущая точка
     * сохраняется).
     *
     * Режим зависит от [end]:
     * - `null` (жест выделения): при [anchoring] = `true` (начало) задаёт и начало, и
     *   конец; иначе двигает только конец;
     * - [SelectionEnd] (перетаскивание ручки-курсора после фиксации): двигает именно
     *   этот конец, сохраняя другой на месте; [anchoring] игнорируется.
     */
    fun moveTo(
        pos: Offset,
        anchoring: Boolean,
        end: SelectionEnd? = null,
    ) {
        val point = pointAt(pos) ?: return
        when (end) {
            SelectionEnd.ANCHOR -> anchor = point
            SelectionEnd.FOCUS -> focus = point
            null -> {
                if (anchoring) anchor = point
                focus = point
            }
        }
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
     * выделению блоков (есть и раскладка, и живые координаты) берёт тот, чьи границы
     * накрывают `y` (между блоками — ближайший по `y`), и переводит позицию в смещение
     * символа. Так как
     * кандидаты заведомо имеют и границы, и раскладку, ближайший по `y` блок всегда
     * даёт [SelPoint] — старт жеста чуть выше/ниже строки всё равно цепляет соседнюю.
     * `null`, если ни один блок ещё не пригоден.
     *
     * Грубый выбор блока (какой именно текст) идёт по `y`-полосе из габаритов
     * ([localBoundingBoxOf]); но точное смещение символа считается уже **не** вычитанием
     * угла этого габарита, а переводом [pos] прямо в локальную систему координат
     * `BasicText`-узла ([LayoutCoordinates.localPositionOf]). Иначе расхождение между
     * `top` габарита и истинным началом текстового узла (округление, `LineHeightStyle`)
     * сдвигало `getOffsetForPosition` на строку выше/ниже выбранной — это и был дефект
     * «выделяется не та строка».
     */
    private fun pointAt(pos: Offset): SelPoint? {
        val container = containerCoordinates?.takeIf { it.isAttached } ?: return null
        // Пригодные блоки → их габариты в системе контейнера. Считаем на месте хит-теста,
        // где контейнер заведомо позиционирован; отсоединённые/без раскладки пропускаем.
        val usable =
            blockCoordinates.mapNotNull { (index, coordinates) ->
                if (index !in layouts || !coordinates.isAttached) return@mapNotNull null
                index to container.localBoundingBoxOf(coordinates, clipBounds = false)
            }
        val inside = usable.firstOrNull { (_, rect) -> pos.y >= rect.top && pos.y <= rect.bottom }
        val hit =
            inside ?: usable.minByOrNull { (_, rect) ->
                if (pos.y < rect.top) rect.top - pos.y else pos.y - rect.bottom
            }
        return hit?.let { (index, _) ->
            val layout = layouts.getValue(index)
            val coordinates = blockCoordinates[index]?.takeIf { it.isAttached } ?: return@let null
            // pos выражена относительно контейнера; localPositionOf переводит её в
            // собственную систему текстового узла — ровно то, что ждёт getOffsetForPosition.
            val local = coordinates.localPositionOf(container, pos)
            SelPoint(index, layout.getOffsetForPosition(local))
        }
    }

    /**
     * Прямоугольник одного из концов выделения ([SelectionEnd.ANCHOR]/[SelectionEnd.FOCUS])
     * в координатах контейнера — для размещения ручек-курсоров и плавающей панели действий.
     * `null`, если выделения нет либо блок конца ещё не позиционирован/не имеет раскладки.
     *
     * Берётся курсор-прямоугольник символа конца ([TextLayoutResult.getCursorRect]) и
     * переводится из локальной системы текстового узла в систему контейнера — той же,
     * в которой ведётся жест и рисуется оверлей ручек.
     */
    fun endRect(end: SelectionEnd): Rect? {
        val point = if (end == SelectionEnd.ANCHOR) anchor else focus
        val container = containerCoordinates?.takeIf { it.isAttached }
        val coordinates = point?.let { blockCoordinates[it.blockIndex] }?.takeIf { it.isAttached }
        val layout = point?.let { layouts[it.blockIndex] }
        // Единый «успешный» путь по nullable-цепочке — без множественных return-guard'ов
        // (ReturnCount) и без длинного условия (ComplexCondition).
        return point?.let { p ->
            container?.let { c ->
                coordinates?.let { coords ->
                    layout?.let { l ->
                        val length = l.layoutInput.text.length
                        val cursor = l.getCursorRect(p.charOffset.coerceIn(0, length))
                        Rect(
                            c.localPositionOf(coords, cursor.topLeft),
                            c.localPositionOf(coords, cursor.bottomRight),
                        )
                    }
                }
            }
        }
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

/**
 * Собирает выделенный текст из [document] по списку [anchors] (по одному на блок,
 * упорядочены по индексу блока — как отдаёт [ReflowSelectionState.anchorsForSelection]).
 * Диапазоны зажимаются по фактической длине текста блока; блоки без собственного текста
 * (картинки/разделители/таблицы) пропускаются. Куски разных блоков склеиваются переводом
 * строки — это «то, что выделил пользователь» для буфера обмена.
 */
internal fun selectedText(
    document: ru.kyamshanov.notepen.reflow.api.ReflowDocument,
    anchors: List<TextAnchor>,
): String =
    anchors
        .mapNotNull { anchor ->
            val text = blockText(document.blocks.getOrNull(anchor.blockIndex)) ?: return@mapNotNull null
            val start = anchor.charStart.coerceIn(0, text.length)
            val end = anchor.charEnd.coerceIn(start, text.length)
            if (end > start) text.substring(start, end) else null
        }.joinToString("\n")

/** Собственный текст блока (для извлечения выделения), либо `null` — у блока его нет. */
private fun blockText(block: ru.kyamshanov.notepen.reflow.api.ReflowBlock?): String? =
    when (block) {
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Heading -> block.text
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Paragraph -> block.text
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Blockquote -> block.text
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.ListItem -> block.text
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Code -> block.text
        is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Footnote -> block.text
        else -> null
    }
