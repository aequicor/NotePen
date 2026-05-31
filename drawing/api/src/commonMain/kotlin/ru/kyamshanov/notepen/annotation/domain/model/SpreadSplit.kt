package ru.kyamshanov.notepen.annotation.domain.model

/**
 * Нормализованный прямоугольник-вырезка одной *исходной* страницы PDF,
 * который образует одну *логическую* страницу.
 *
 * Координаты в долях `[0, 1]` ширины/высоты исходной страницы **в её
 * собственной (до пользовательского поворота) системе координат**, ось Y
 * вниз. По умолчанию [FULL] — вся страница (`0,0 → 1,1`), что соответствует
 * исходному отображению 1:1 без разделения.
 *
 * @property leftN левый край вырезки (доля ширины исходной страницы).
 * @property topN верхний край вырезки (доля высоты).
 * @property rightN правый край вырезки.
 * @property bottomN нижний край вырезки.
 */
public data class PageCropRect(
    val leftN: Float = 0f,
    val topN: Float = 0f,
    val rightN: Float = 1f,
    val bottomN: Float = 1f,
) {
    /** Ширина вырезки в долях ширины исходной страницы. */
    public val widthN: Float get() = rightN - leftN

    /** Высота вырезки в долях высоты исходной страницы. */
    public val heightN: Float get() = bottomN - topN

    /** `true`, если вырезка покрывает всю исходную страницу (нет разделения). */
    public val isFull: Boolean
        get() = leftN <= 0f && topN <= 0f && rightN >= 1f && bottomN >= 1f

    public companion object {
        /** Вся исходная страница — отображение 1:1 без разделения. */
        public val FULL: PageCropRect = PageCropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Один источник логической страницы: индекс *исходной* страницы PDF и
 * [crop], выделяющий из неё прямоугольник. Передаётся рендереру и в раскладку.
 *
 * @property sourceIndex нулевой индекс исходной страницы в документе.
 * @property crop вырезка из исходной страницы (для целой страницы — [PageCropRect.FULL]).
 */
public data class PageSource(
    val sourceIndex: Int,
    val crop: PageCropRect = PageCropRect.FULL,
)

/**
 * Ручное разделение «разворотов»: некоторые PDF печатают ДВЕ логические
 * страницы рядом на одной физической (2-up скан). Когда разделение включено,
 * каждая исходная страница `S` даёт две логические — левую (`2S`) и правую
 * (`2S+1`) половины, удваивая число страниц. Когда выключено — отображение 1:1.
 *
 * Это **документ-уровневое** ручное переключение (НЕ авто-детект). Все функции
 * чистые и детерминированные (без Compose/SDK) — тестируются отдельно от UI.
 *
 * **Пространство индексов.** При включённом разделении вся «валюта» вьювера —
 * индекс штрихов (`PdfDrawingState`), индекс поворота ([AnnotationViewState.pageRotations]),
 * навигация, счётчик страниц — становится ЛОГИЧЕСКОЙ. Рендерер же получает
 * исходный индекс + [PageCropRect] (см. [sourceFor]).
 *
 * **Координаты штрихов.** Штрихи хранятся нормированными `[0, 1]` относительно
 * ЛОГИЧЕСКОЙ страницы. При включении разделения старые штрихи исходной страницы
 * мигрируют в две половины ([splitPoint] / [splitPath]); при выключении —
 * объединяются обратно ([mergePoint] / [mergePath]). Граница раздела — `x = 0.5`
 * исходной страницы; точка относится к половине по своей координате X (точки на
 * самой границе попадают в правую половину). Штрих целиком относится к половине
 * по X его ПЕРВОЙ точки — редкие штрихи через корешок не дробятся (см. [splitPath]).
 *
 * **Сосуществование с поворотом.** Разделение применяется к ИСХОДНОЙ
 * (не повёрнутой) странице, а пользовательский поворот — к уже выделенной
 * логической половине. Поэтому при переключении разделения карта поворотов
 * пере-строится: при включении каждая половина наследует поворот исходной
 * страницы (`q[2S] = q[2S+1] = q_src[S]`), при выключении левая половина
 * (`q[2S]`) задаёт поворот объединённой страницы. Так поворот не теряется и не
 * применяется дважды (см. [splitRotations] / [mergeRotations]).
 */
public object SpreadSplit {
    /** Граница раздела разворота по X исходной страницы. */
    public const val GUTTER_X: Float = 0.5f

    /** Логических страниц на одну исходную при включённом разделении. */
    public const val HALVES_PER_PAGE: Int = 2

    /** Индекс левой логической страницы для исходной [sourceIndex]. */
    public fun leftLogical(sourceIndex: Int): Int = sourceIndex * HALVES_PER_PAGE

    /** Индекс правой логической страницы для исходной [sourceIndex]. */
    public fun rightLogical(sourceIndex: Int): Int = sourceIndex * HALVES_PER_PAGE + 1

    /** `true`, если [logicalIndex] — правая половина разворота. */
    public fun isRightHalf(logicalIndex: Int): Boolean = logicalIndex % HALVES_PER_PAGE == 1

    /** Исходный индекс страницы для логического [logicalIndex] при включённом разделении. */
    public fun sourceIndexOf(logicalIndex: Int): Int = logicalIndex / HALVES_PER_PAGE

    /** Вырезка для логической страницы [logicalIndex]: левая или правая половина исходной. */
    public fun cropOf(logicalIndex: Int): PageCropRect =
        if (isRightHalf(logicalIndex)) {
            PageCropRect(leftN = GUTTER_X, topN = 0f, rightN = 1f, bottomN = 1f)
        } else {
            PageCropRect(leftN = 0f, topN = 0f, rightN = GUTTER_X, bottomN = 1f)
        }

    /**
     * Источник (исходный индекс + вырезка) логической страницы [logicalIndex].
     * При [splitEnabled] = `false` — тождественное отображение (`sourceIndex =
     * logicalIndex`, [PageCropRect.FULL]).
     */
    public fun sourceFor(
        logicalIndex: Int,
        splitEnabled: Boolean,
    ): PageSource =
        if (splitEnabled) {
            PageSource(sourceIndex = sourceIndexOf(logicalIndex), crop = cropOf(logicalIndex))
        } else {
            PageSource(sourceIndex = logicalIndex, crop = PageCropRect.FULL)
        }

    /** Число логических страниц при [splitEnabled] над [sourceCount] исходными. */
    public fun logicalCount(
        sourceCount: Int,
        splitEnabled: Boolean,
    ): Int = if (splitEnabled) sourceCount * HALVES_PER_PAGE else sourceCount

    /**
     * Соотношение сторон (ширина/высота в экранном пространстве) ЛОГИЧЕСКОЙ
     * половины разворота из исходного [sourceAspect] (ширина/высота). Половина
     * вдвое уже → её aspect вдвое меньше исходного.
     */
    public fun halfAspect(sourceAspect: Float): Float = sourceAspect * GUTTER_X

    // ── Координаты штрихов: исходная страница → две половины и обратно ──────────

    /**
     * Переводит точку [point] исходной страницы в систему координат той половины,
     * которой она принадлежит. X масштабируется к `[0, 1]` внутри половины; Y не
     * меняется. Возвращает пару `(правая ли половина, новая точка)`.
     *
     * Левая (`x < 0.5`): `x' = x / 0.5 = x * 2`.
     * Правая (`x >= 0.5`): `x' = (x - 0.5) / 0.5 = (x - 0.5) * 2`.
     */
    public fun splitPoint(point: DrawingPoint): Pair<Boolean, DrawingPoint> {
        val right = point.x >= GUTTER_X
        val newX = if (right) (point.x - GUTTER_X) / GUTTER_X else point.x / GUTTER_X
        return right to point.copy(x = newX)
    }

    /**
     * Обратный к [splitPoint]: переводит точку половины обратно в систему
     * координат исходной (объединённой) страницы. [right] — правая ли половина.
     *
     * Левая: `x = x' * 0.5`. Правая: `x = 0.5 + x' * 0.5`.
     */
    public fun mergePoint(
        point: DrawingPoint,
        right: Boolean,
    ): DrawingPoint {
        val newX = if (right) GUTTER_X + point.x * GUTTER_X else point.x * GUTTER_X
        return point.copy(x = newX)
    }
}

// ── Преобразования штрихов и пер-страничных карт ────────────────────────────────
// Вынесены top-level (а не в объект [SpreadSplit]), чтобы объект не разрастался
// функциями (detekt TooManyFunctions); это агрегации над примитивами объекта.

/**
 * Распределяет штрих [path] исходной страницы в левую/правую половину при
 * включении разделения. Принадлежность определяется по X **первой точки** штриха
 * (простое, детерминированное правило); штрих не дробится по корешку. Все точки
 * штриха пересчитываются в систему координат выбранной половины.
 *
 * Толщина штриха нормирована к ширине; половина вдвое уже, поэтому видимая
 * толщина сохраняется удвоением нормированной (`w' = w * 2`).
 *
 * @return пара `(правая ли половина, штрих в координатах половины)`.
 */
public fun SpreadSplit.splitPath(path: DrawingPath): Pair<Boolean, DrawingPath> {
    val firstX = path.points.firstOrNull()?.x ?: 0f
    val right = firstX >= GUTTER_X
    val remapped =
        path.points.map { p ->
            val newX = if (right) (p.x - GUTTER_X) / GUTTER_X else p.x / GUTTER_X
            p.copy(x = newX)
        }
    return right to path.copy(points = remapped, strokeWidth = path.strokeWidth / GUTTER_X)
}

/**
 * Обратный к [splitPath]: переводит штрих [path] половины ([right] — правая ли)
 * обратно в координаты исходной (объединённой) страницы. Толщина делится обратно
 * (`w = w' * 0.5`).
 */
public fun SpreadSplit.mergePath(
    path: DrawingPath,
    right: Boolean,
): DrawingPath {
    val remapped =
        path.points.map { p ->
            val newX = if (right) GUTTER_X + p.x * GUTTER_X else p.x * GUTTER_X
            p.copy(x = newX)
        }
    return path.copy(points = remapped, strokeWidth = path.strokeWidth * GUTTER_X)
}

/**
 * Пере-строит карту штрихов по индексу страницы из ИСХОДНОГО пространства в
 * ЛОГИЧЕСКОЕ при включении разделения: штрихи исходной страницы `S` раскидываются
 * по половинам `2S` (левая) и `2S+1` (правая) через [splitPath].
 */
public fun SpreadSplit.splitStrokesByPage(source: Map<Int, List<DrawingPath>>): Map<Int, List<DrawingPath>> {
    val result = HashMap<Int, MutableList<DrawingPath>>()
    for ((srcPage, paths) in source) {
        for (path in paths) {
            val (right, remapped) = splitPath(path)
            val logical = if (right) rightLogical(srcPage) else leftLogical(srcPage)
            result.getOrPut(logical) { mutableListOf() }.add(remapped)
        }
    }
    return result
}

/**
 * Обратный к [splitStrokesByPage]: объединяет логические половины обратно в штрихи
 * исходных страниц через [mergePath].
 */
public fun SpreadSplit.mergeStrokesByPage(logical: Map<Int, List<DrawingPath>>): Map<Int, List<DrawingPath>> {
    val result = HashMap<Int, MutableList<DrawingPath>>()
    for ((logicalPage, paths) in logical) {
        val srcPage = sourceIndexOf(logicalPage)
        val right = isRightHalf(logicalPage)
        for (path in paths) {
            result.getOrPut(srcPage) { mutableListOf() }.add(mergePath(path, right))
        }
    }
    return result
}

/**
 * Пере-строит карту поворотов из ИСХОДНОГО пространства в ЛОГИЧЕСКОЕ при включении
 * разделения: каждая половина наследует поворот исходной страницы.
 */
public fun SpreadSplit.splitRotations(source: Map<Int, Int>): Map<Int, Int> {
    val result = HashMap<Int, Int>()
    for ((srcPage, q) in source) {
        if (q == 0) continue
        result[leftLogical(srcPage)] = q
        result[rightLogical(srcPage)] = q
    }
    return result
}

/**
 * Обратный к [splitRotations]: поворот объединённой страницы берётся из ЛЕВОЙ
 * половины (правая отбрасывается — после слияния половин одна страница может иметь
 * лишь один поворот).
 */
public fun SpreadSplit.mergeRotations(logical: Map<Int, Int>): Map<Int, Int> {
    val result = HashMap<Int, Int>()
    for ((logicalPage, q) in logical) {
        if (q == 0 || isRightHalf(logicalPage)) continue
        result[sourceIndexOf(logicalPage)] = q
    }
    return result
}

/**
 * Обрезает прямоугольник [r] (координаты исходной страницы) к одной половине
 * разворота и пересчитывает X в систему координат этой половины `[0, 1]`. Режется
 * только по корешку (`x = 0.5`); внешний край сохраняет лёгкий выход за `[0..1]`,
 * как и у обычной (неразделённой) подсветки. `null` — прямоугольник не пересекает
 * выбранную половину. Y не меняется.
 */
private fun SpreadSplit.clipRectToHalf(
    r: NormalizedRect,
    right: Boolean,
): NormalizedRect? {
    val left = if (right) maxOf(r.left, GUTTER_X) else r.left
    val rightEdge = if (right) r.right else minOf(r.right, GUTTER_X)
    if (rightEdge <= left) return null
    val base = if (right) GUTTER_X else 0f
    return r.copy(
        left = (left - base) / GUTTER_X,
        right = (rightEdge - base) / GUTTER_X,
    )
}

/**
 * Пере-строит карту липких выделений из ИСХОДНОГО пространства в ЛОГИЧЕСКОЕ при
 * включении разделения. В отличие от штриха (целиком уходит в половину по первой
 * точке), подсветку РЕЖЕМ по корешку: каждый прямоугольник обрезается к левой и
 * правой половине отдельно ([clipRectToHalf]) и попадает в свою логическую страницу.
 * Иначе широкая подсветка, целиком отнесённая к одной половине и отмасштабированная
 * (`right / 0.5` → до 2×), вылезала бы за край половины в гаттер.
 */
public fun SpreadSplit.splitHighlightsByPage(source: Map<Int, List<StickyHighlight>>): Map<Int, List<StickyHighlight>> {
    val result = HashMap<Int, MutableList<StickyHighlight>>()
    source.forEach { (srcPage, hs) ->
        hs.forEach { h ->
            val leftRects = h.rects.mapNotNull { clipRectToHalf(it, right = false) }
            val rightRects = h.rects.mapNotNull { clipRectToHalf(it, right = true) }
            if (leftRects.isNotEmpty()) {
                result.getOrPut(leftLogical(srcPage)) { mutableListOf() }.add(h.copy(rects = leftRects))
            }
            if (rightRects.isNotEmpty()) {
                result.getOrPut(rightLogical(srcPage)) { mutableListOf() }.add(h.copy(rects = rightRects))
            }
        }
    }
    return result
}

/**
 * Обратный к [splitHighlightsByPage]: объединяет логические половины обратно в
 * выделения исходных страниц, пересчитывая X каждого прямоугольника из системы
 * координат половины в исходную (`left → x * 0.5`, `right → 0.5 + x * 0.5`).
 */
public fun SpreadSplit.mergeHighlightsByPage(logical: Map<Int, List<StickyHighlight>>): Map<Int, List<StickyHighlight>> {
    val result = HashMap<Int, MutableList<StickyHighlight>>()
    logical.forEach { (logicalPage, hs) ->
        val srcPage = sourceIndexOf(logicalPage)
        val right = isRightHalf(logicalPage)
        hs.forEach { h ->
            val remapped =
                h.rects.map { r ->
                    if (right) {
                        r.copy(left = GUTTER_X + r.left * GUTTER_X, right = GUTTER_X + r.right * GUTTER_X)
                    } else {
                        r.copy(left = r.left * GUTTER_X, right = r.right * GUTTER_X)
                    }
                }
            result.getOrPut(srcPage) { mutableListOf() }.add(h.copy(rects = remapped))
        }
    }
    return result
}

/**
 * Пере-строит карту текстовых заметок из ИСХОДНОГО пространства в ЛОГИЧЕСКОЕ при
 * включении разделения. Как штрих, заметка целиком относится к половине по X её
 * первого прямоугольника (не дробится по корешку); X пересчитывается в систему
 * координат половины, а [PageNote.pageIndex] переписывается на логический.
 */
public fun SpreadSplit.splitNotesByPage(source: Map<Int, List<PageNote>>): Map<Int, List<PageNote>> {
    val result = HashMap<Int, MutableList<PageNote>>()
    source.forEach { (srcPage, ns) ->
        ns.forEach { n ->
            val firstRectLeft = n.rects.firstOrNull()?.left ?: 0f
            val right = firstRectLeft >= GUTTER_X
            val remapped =
                n.rects.map { r ->
                    if (right) {
                        r.copy(left = (r.left - GUTTER_X) / GUTTER_X, right = (r.right - GUTTER_X) / GUTTER_X)
                    } else {
                        r.copy(left = r.left / GUTTER_X, right = r.right / GUTTER_X)
                    }
                }
            val target = if (right) rightLogical(srcPage) else leftLogical(srcPage)
            result.getOrPut(target) { mutableListOf() }.add(n.copy(rects = remapped, pageIndex = target))
        }
    }
    return result
}

/**
 * Обратный к [splitNotesByPage]: объединяет логические половины обратно в заметки
 * исходных страниц, пересчитывая X прямоугольников и [PageNote.pageIndex] в исходное
 * пространство.
 */
public fun SpreadSplit.mergeNotesByPage(logical: Map<Int, List<PageNote>>): Map<Int, List<PageNote>> {
    val result = HashMap<Int, MutableList<PageNote>>()
    logical.forEach { (logicalPage, ns) ->
        val srcPage = sourceIndexOf(logicalPage)
        val right = isRightHalf(logicalPage)
        ns.forEach { n ->
            val remapped =
                n.rects.map { r ->
                    if (right) {
                        r.copy(left = GUTTER_X + r.left * GUTTER_X, right = GUTTER_X + r.right * GUTTER_X)
                    } else {
                        r.copy(left = r.left * GUTTER_X, right = r.right * GUTTER_X)
                    }
                }
            result.getOrPut(srcPage) { mutableListOf() }.add(n.copy(rects = remapped, pageIndex = srcPage))
        }
    }
    return result
}
