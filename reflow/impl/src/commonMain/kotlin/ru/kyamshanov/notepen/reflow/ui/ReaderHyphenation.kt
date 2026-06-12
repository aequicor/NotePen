package ru.kyamshanov.notepen.reflow.ui

/** Символ мягкого переноса (U+00AD): невидимая точка разрыва строки. */
internal const val SOFT_HYPHEN = '­'

/**
 * Видимый дефис (U+002D), в который промоутится мягкий перенос на разорванной строке. Именно
 * HYPHEN-MINUS: он есть в любом шрифте без fallback-подмены и по UAX#14 сам остаётся точкой
 * разрыва после себя — промоутнутый слот не теряет свойства разрыва при перемере.
 */
internal const val PROMOTED_HYPHEN = '-'

/**
 * Текст блока с расставленными мягкими переносами плюс двусторонняя карта смещений plain↔hyph.
 *
 * Зачем карта: вставка [SOFT_HYPHEN] сдвигает все символьные смещения. Публичный контракт ридера
 * (`TextAnchor`, выделение, source-спаны, anchors-подсветки) живёт в PLAIN-смещениях (стабильны между
 * платформами и при переключении тогла переноса). [TextLayoutResult] же измеряет [hyphenated] и отдаёт
 * HYPH-смещения. Карта конвертирует ровно на стыке этих двух пространств.
 *
 * Карта ключуется ПОЗИЦИЯМИ вставки, а не значением символа: слот может рендериться и мягким
 * переносом, и видимым [PROMOTED_HYPHEN] (см. [promoted] и `ReaderHyphenPromotion`) — конвертации
 * от этого не меняются.
 *
 * Идентичность — когда переносов нет (Android/выключенный тогл): [hyphenated] == [plain],
 * обе конвертации — тождество.
 *
 * @property plain исходный текст блока (без переносов)
 * @property positions сортированные индексы вставки в [plain]; позиция k — перенос перед `plain[k]`
 * @property promoted маска по слотам: `true` — слот рендерится видимым дефисом (движок меряет и
 *   рисует его как обычный глиф), `false` — невидимым мягким переносом
 */
internal class ReaderHyphenation(
    val plain: String,
    val positions: IntArray,
    val promoted: BooleanArray = BooleanArray(positions.size),
) {
    init {
        require(promoted.size == positions.size) {
            "promoted mask size ${promoted.size} != positions size ${positions.size}"
        }
    }

    /** Текст со вставленными переносами (мягкими или промоутнутыми) — то, что отдаётся в BasicText/TextMeasurer. */
    val hyphenated: String by lazy {
        if (positions.isEmpty()) {
            plain
        } else {
            buildString(plain.length + positions.size) {
                var prev = 0
                for (m in positions.indices) {
                    val pos = positions[m]
                    append(plain, prev, pos)
                    append(if (promoted[m]) PROMOTED_HYPHEN else SOFT_HYPHEN)
                    prev = pos
                }
                append(plain, prev, plain.length)
            }
        }
    }

    /** Длина текста в hyph-пространстве. */
    val hyphLength: Int get() = plain.length + positions.size

    /** Есть ли вообще вставленные слоты переноса (на Android/при выключенном тогле — нет). */
    val hasSoftHyphens: Boolean get() = positions.isNotEmpty()

    /** HYPH-индекс символа m-го слота в [hyphenated] (перед ним стоят m предыдущих вставок). */
    fun slotHyphIndex(m: Int): Int = positions[m] + m

    /** PLAIN-смещение → HYPH-смещение (перед каким символом hyph-строки оно стоит). */
    fun plainToHyph(plainOffset: Int): Int {
        if (positions.isEmpty()) return plainOffset
        // Кол-во переносов с позицией <= plainOffset (они стоят перед этим смещением).
        return plainOffset + countAtMost(plainOffset)
    }

    /** HYPH-смещение → PLAIN-смещение (мягкий перенос «схлопывается» в позицию слева). */
    fun hyphToPlain(hyphOffset: Int): Int {
        if (positions.isEmpty()) return hyphOffset
        var shyBefore = 0
        for (m in positions.indices) {
            // m-й перенос стоит в hyph-строке по индексу positions[m] + m.
            if (positions[m] + m < hyphOffset) shyBefore++ else break
        }
        return hyphOffset - shyBefore
    }

    /** Число позиций <= value (positions сортированы). */
    private fun countAtMost(value: Int): Int {
        var lo = 0
        var hi = positions.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (positions[mid] <= value) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * Позиции вставки мягких переносов в [text] для текущей платформы. На десктопе (Skiko) — Кнут-Лиэнг
 * по паттернам `hyph-ru` (там родной `Hyphens.Auto` не работает). На Android — пусто: там перенос
 * делает системный переносчик через `Hyphens.Auto`, а карта остаётся тождественной.
 */
internal expect fun platformSoftHyphenPositions(text: String): IntArray

/**
 * Строит [ReaderHyphenation] для текста блока. Если перенос выключен или платформа не расставляет
 * мягкие переносы (Android) — возвращает тождественную карту (нулевая стоимость).
 */
internal fun readerHyphenation(
    plain: String,
    enabled: Boolean,
): ReaderHyphenation =
    if (enabled) {
        ReaderHyphenation(plain, platformSoftHyphenPositions(plain))
    } else {
        ReaderHyphenation(plain, EMPTY_POSITIONS)
    }

private val EMPTY_POSITIONS = IntArray(0)
