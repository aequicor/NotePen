package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument

/**
 * Ключ дискового кэша расчётной раскладки. Раскладка — это `heights` и
 * `lineBottoms` всех текстовых блоков при конкретной ширине колонки и
 * типографических настройках. Если те же — раскладка детерминированно та же
 * (см. `BlockHeightCalculator`), и можно избежать ~4 секунд фонового обмера
 * через `TextMeasurer`.
 *
 * [docFingerprint] — быстрый FNV-1a 64-bit хеш по содержимому документа
 * (`ReflowBlock.text`/`pageIndex`/`aspectRatio` и тип блока). Меняется ровно
 * тогда, когда меняется значимое для раскладки содержимое.
 *
 * Остальные поля — все типографические настройки, которые влияют на
 * раскладку через `paragraphStyle()`/`headingStyle()` и пагинацию через
 * `pageWindows()` (ширина страницы, поля, межстрочный, кегль и т.д.). Любое
 * изменение → новый ключ → cache miss → пересчёт + запись.
 *
 * Не входит: цвет текста, фон, яркость, выравнивание панели — это purely
 * рендеровые параметры, не влияющие на раскладку.
 */
public data class LayoutCacheKey(
    val docFingerprint: Long,
    val contentWidthPx: Int,
    val fontFamilyId: String,
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingSp: Float,
    val wordSpacingSp: Float,
    val hyphenation: Boolean,
    val align: String,
    val bionic: Boolean,
    val columnChars: Int,
    val contentPaddingDp: Float,
)

/**
 * Закэшированная детерминированная раскладка для пары
 * `(docFingerprint, contentWidthPx + типо-настройки)`.
 *
 * Хранятся только высоты текстовых блоков (Heading/Paragraph/ListItem/
 * Blockquote/Table/Divider). Figure-блоки имеют аналитическую высоту
 * (см. P3 `figureHeights`), её всегда быстрее пересчитать на месте, чем
 * сериализовать; кроме того, она зависит от `contentWidthPx`, которая уже
 * входит в ключ.
 *
 * `lineBottoms` — только для делимых блоков (Paragraph/ListItem/Blockquote);
 * для остальных — пустые.
 */
public data class CachedLayout(
    val textHeights: Map<Int, Int>,
    val textLineBottoms: Map<Int, List<Float>>,
    /**
     * Аналитические высоты Figure-блоков, посчитанные при записи кэша
     * (`round(contentWidthPx / aspectRatio)`). Хранятся здесь, а не пересчитываются
     * на чтении, чтобы избежать transient-«дёргания» страницы: при первой
     * композиции `BoxWithConstraints.maxWidth` может на пару пикселей плыть, что
     * меняет `round(...)` для пары Figure-блоков → меняет общую высоту → второй
     * `paginate` с другим page-count → visual content shift через ~500 мс после
     * first-page. Закэшированные значения — пиксель-в-пиксель те же, что были
     * при записи; пагинация детерминирована с первой композиции.
     */
    val figureHeights: Map<Int, Int> = emptyMap(),
)

/**
 * Дисковый кэш раскладки `ReflowDocument`. Реализация платформенная
 * (см. `FileSystemReflowLayoutCache` в `:app:byCompose:common`), инжектится
 * через [LocalReflowLayoutCache]. Ошибки/несоответствия версии — `null` на
 * чтении (вызывающая сторона пересчитывает); failures на записи логируются и
 * проглатываются (кэш необязателен).
 */
public interface ReflowLayoutCache {
    public suspend fun read(key: LayoutCacheKey): CachedLayout?

    public suspend fun write(
        key: LayoutCacheKey,
        layout: CachedLayout,
    )
}

/** No-op fallback для тестов и невнедрённых сценариев. */
public object NoopReflowLayoutCache : ReflowLayoutCache {
    override suspend fun read(key: LayoutCacheKey): CachedLayout? = null

    override suspend fun write(
        key: LayoutCacheKey,
        layout: CachedLayout,
    ) = Unit
}

public val LocalReflowLayoutCache: ProvidableCompositionLocal<ReflowLayoutCache> =
    staticCompositionLocalOf { NoopReflowLayoutCache }

private const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325 как signed Long
private const val FNV_PRIME = 1099511628211L // 0x100000001b3

/**
 * FNV-1a 64-bit хеш по значимому для раскладки содержимому документа. Не
 * криптостойкий — используется только как ключ кэша; коллизий на 7000+
 * уникальных документов практически невозможны (теоретически 2^-64).
 */
public fun fingerprintDocument(document: ReflowDocument): Long {
    var h = FNV_OFFSET
    h = mixInt(h, document.kind.ordinal)
    h = mixInt(h, document.blocks.size)
    document.blocks.forEach { block ->
        h = mixInt(h, blockTag(block).code)
        when (block) {
            is ReflowBlock.Heading -> {
                h = mixInt(h, block.level)
                h = mixString(h, block.text)
            }
            is ReflowBlock.Paragraph -> h = mixString(h, block.text)
            is ReflowBlock.ListItem -> h = mixString(h, block.text)
            is ReflowBlock.Blockquote -> h = mixString(h, block.text)
            is ReflowBlock.Table ->
                block.rows.forEach { row ->
                    row.cells.forEach { cell -> h = mixString(h, cell.text) }
                }
            is ReflowBlock.Figure -> {
                h = mixInt(h, block.pageIndex)
                h = mixInt(h, block.aspectRatio.toRawBits())
            }
            is ReflowBlock.Code -> h = mixString(h, block.text)
            is ReflowBlock.Footnote -> h = mixString(h, block.text)
            ReflowBlock.Divider -> Unit
        }
    }
    return h
}

private fun mixInt(
    h: Long,
    value: Int,
): Long {
    var x = h
    var v = value
    repeat(4) {
        x = (x xor (v and 0xFF).toLong()) * FNV_PRIME
        v = v ushr 8
    }
    return x
}

private fun mixString(
    h: Long,
    s: String,
): Long {
    var x = h
    for (i in s.indices) {
        val code = s[i].code
        x = (x xor (code and 0xFF).toLong()) * FNV_PRIME
        x = (x xor ((code shr 8) and 0xFF).toLong()) * FNV_PRIME
    }
    return x
}

private fun blockTag(b: ReflowBlock): Char =
    when (b) {
        is ReflowBlock.Heading -> 'H'
        is ReflowBlock.Paragraph -> 'P'
        is ReflowBlock.ListItem -> 'L'
        is ReflowBlock.Blockquote -> 'Q'
        is ReflowBlock.Table -> 'T'
        is ReflowBlock.Figure -> 'F'
        is ReflowBlock.Code -> 'C'
        is ReflowBlock.Footnote -> 'N'
        ReflowBlock.Divider -> 'D'
    }
