package ru.kyamshanov.notepen.reflow

/**
 * Жёсткие лимиты на разбор недоверенных PDF в режиме reflow.
 *
 * PDF приходят из произвольных файлов пользователя: документ с десятками тысяч
 * страниц или со страницей-«простынёй» гигантских размеров может исчерпать
 * память при извлечении/растеризации. Вместо OOM разбор прерывается понятным
 * [PdfTooLargeException].
 *
 * Лимиты выбраны с большим запасом над реальными документами: A4 ≈ 595×842 pt,
 * крупный плакат A0 ≈ 2384×3370 pt — всё это далеко ниже [MAX_PAGE_DIMENSION_PT].
 */
internal object PdfReflowLimits {
    /** Максимальное число страниц для извлечения. */
    const val MAX_PAGES: Int = 5_000

    /** Максимальная ширина/высота mediaBox (в пунктах) до выделения буферов. */
    const val MAX_PAGE_DIMENSION_PT: Float = 20_000f

    /**
     * Бросает [PdfTooLargeException], если число страниц документа превышает
     * [MAX_PAGES]. Вызывается перед обходом всех страниц.
     */
    fun requirePageCount(pageCount: Int) {
        if (pageCount > MAX_PAGES) {
            throw PdfTooLargeException("PDF has more than $MAX_PAGES pages ($pageCount)")
        }
    }

    /**
     * Бросает [PdfTooLargeException], если размеры mediaBox страницы превышают
     * [MAX_PAGE_DIMENSION_PT]. Вызывается до любого выделения, зависящего от
     * размера страницы.
     */
    fun requirePageDimensions(
        pageIndex: Int,
        widthPt: Float,
        heightPt: Float,
    ) {
        if (widthPt > MAX_PAGE_DIMENSION_PT || heightPt > MAX_PAGE_DIMENSION_PT) {
            throw PdfTooLargeException(
                "PDF page $pageIndex is ${widthPt}x$heightPt pt, exceeding $MAX_PAGE_DIMENSION_PT pt",
            )
        }
    }
}

/**
 * PDF превышает допустимые лимиты разбора ([PdfReflowLimits]) — отвергаем его,
 * не доводя до OOM. Подтип [IllegalArgumentException], как и прочие ошибки
 * невалидного входа в этом модуле.
 */
internal class PdfTooLargeException(
    message: String,
) : IllegalArgumentException(message)
