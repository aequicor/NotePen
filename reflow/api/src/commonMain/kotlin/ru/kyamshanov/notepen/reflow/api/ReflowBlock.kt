package ru.kyamshanov.notepen.reflow.api

/**
 * Один блок переформатированного (reflow) документа.
 *
 * Блоки следуют в порядке чтения (см. [ReflowDocument.blocks]). Иерархия
 * закрытая (`sealed`) — расширение типами блоков происходит через добавление
 * новых наследников, а не через флаги.
 */
public sealed interface ReflowBlock {
    /**
     * Заголовок раздела: текст с укрупнённым относительно основного кеглем.
     *
     * @property text текст заголовка
     * @property level уровень вложенности от 1 (самый крупный); глубже —
     *   больше число
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class Heading(
        public val text: String,
        public val level: Int,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Абзац основного текста.
     *
     * Мягкие переносы строк внутри абзаца сняты, перенос по дефису на конце
     * строки склеен, слова разделены одиночными пробелами — текст готов к
     * повторной верстке под произвольную ширину.
     *
     * @property text текст абзаца
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class Paragraph(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Цитата (`blockquote`): врезка, набранная с отступом и выделением,
     * семантически отличная от абзаца. Текст уже готов к повторной верстке
     * (мягкие переносы сняты, слова разделены одиночными пробелами).
     *
     * @property text текст цитаты
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class Blockquote(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Элемент маркированного или нумерованного списка.
     *
     * Маркер (`•`, `–`, `1.`…) намеренно сохраняется в начале [text]: так
     * провенанс ([source]) не требует пересчёта смещений, а отступ-«висячая
     * строка» полностью на стороне ридера.
     *
     * @property text текст элемента списка вместе с исходным маркером
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd]
     *   индексируют [text]
     */
    public data class ListItem(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
        /**
         * Уровень вложенности списка: `0` — верхний уровень, `1` — первый
         * sub-уровень, и т.д. Определяется ассемблером по отступу маркера
         * (см. `BlockBuilder.computeListLevel`). UI может использовать для
         * визуального indent'а; default `0` сохраняет BC для плоских списков.
         */
        public val level: Int = 0,
    ) : ReflowBlock

    /**
     * Таблица: восстановленная по выравниванию текста сетка ячеек. PDF не несёт
     * структуры таблиц — она выводится из позиций глифов (выровненные колонки на
     * нескольких строках), поэтому реконструкция приблизительная.
     *
     * @property rows строки таблицы сверху вниз; первая обычно — заголовок
     * @property confidence агрегатная уверенность Stream-детектора: 1f — таблица
     *   чистая (много строк, плотно заполнена короткими ячейками, колонки строго
     *   выровнены); 0f — почти наверняка ложное срабатывание (широкая ячейка-абзац
     *   или OCR-шум). Reader-пайплайн при низкой уверенности подменяет таблицу
     *   на [Figure]-кроп исходной страницы — лучше показать картинкой, чем тащить
     *   соседний абзац в Table. Значение определяется на этапе сборки документа
     *   и не меняется в ридере.
     */
    public data class Table(
        public val rows: List<TableRow>,
        public val confidence: Float = 1f,
    ) : ReflowBlock

    /**
     * Строка таблицы — упорядоченный слева направо набор ячеек.
     *
     * @property cells ячейки строки; длина одинакова у всех строк таблицы
     */
    public data class TableRow(
        public val cells: List<TableCell>,
        /**
         * `true` для шапки таблицы. Детектируется
         * [ru.kyamshanov.notepen.reflow.ReflowAssembler] по типографским признакам
         * (доля полужирных ячеек ≥ TABLE_HEADER_BOLD_RATIO в первой строке).
         * UI рендерит такие строки с фоновой подложкой и/или bold-стилем.
         */
        public val isHeader: Boolean = false,
    )

    /**
     * Ячейка таблицы.
     *
     * @property text текст ячейки (перенос строк внутри ячейки склеен в пробел)
     * @property source фрагменты исходного текста с привязкой к странице
     *   (см. [SourceSpan]); [SourceSpan.charStart]/[SourceSpan.charEnd] индексируют [text]
     */
    public data class TableCell(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
    )

    /**
     * Нетекстовая область (картинка, таблица, формула), которую нельзя
     * осмысленно переформатировать в текст. Рендерится как кроп-изображение
     * соответствующей области исходной страницы внутри потока.
     *
     * @property pageIndex нулевой индекс исходной страницы
     * @property bounds область на странице в нормализованных координатах
     *   `[0..1]` (см. [ReflowRect])
     * @property aspectRatio истинное соотношение `width/height` картинки (в
     *   собственных пикселях/точках, **не** в нормализованных координатах
     *   страницы — те зависят от пропорций страницы). Хранится отдельно от
     *   [bounds], чтобы высота врезки при рендере была детерминирована: ридер
     *   считает её аналитически `round(contentWidthPx / aspectRatio)` ещё до
     *   композиции, и она не плывёт при колебаниях ширины контейнера на 1-2 px
     *   (HorizontalPager-transformations) или при повторной композиции страницы.
     */
    public data class Figure(
        public val pageIndex: Int,
        public val bounds: ReflowRect,
        public val aspectRatio: Float,
        /**
         * Внутренний маркер пайплайна: `true`, если Figure родилась из low-confidence
         * Stream-table fallback (см. `ReflowAssembler.tableAsFigureFallback`). Lattice-
         * рефайнер берёт такие фигуры в работу — пытается восстановить таблицу по
         * нарисованной сетке. UI не должен полагаться на флаг; default `false`
         * сохраняет BC для обычных image-фигур.
         */
        public val wasTableFallback: Boolean = false,
    ) : ReflowBlock

    /**
     * Горизонтальный разделитель (`hr`) — тематический разрыв в потоке текста.
     * Не несёт текста и привязки к странице; рендерится тонкой линией.
     */
    public data object Divider : ReflowBlock

    /**
     * Блок кода: контактные строки одного шрифта, преимущественно моноширинного.
     * Отличие от [Paragraph]: переносы строк значимы (`\n` в [text]), пробелы
     * не сворачиваются, рендер использует моноширинный шрифт и/или фон.
     *
     * Детектируется в [ru.kyamshanov.notepen.reflow.ReflowAssembler]: подряд
     * идущие строки, где ≥CODE_MONOSPACE_FRAC глифов помечены `monospace=true`,
     * группируются в один Code-блок. Inline-monospace внутри обычного абзаца
     * остаётся `SourceSpan(monospace = true)` — UI рендерит инлайном.
     *
     * @property text текст кода с сохранёнными переводами строк
     * @property source провенанс по строкам/символам — как у [Paragraph]
     */
    public data class Code(
        public val text: String,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock

    /**
     * Сноска: small-font блок, обычно в нижней части страницы PDF (под
     * горизонтальной линией-разделителем). В reflow рендерится отдельно от
     * основного потока — обычно более мелким шрифтом или со специальным
     * indent'ом. Если в основном тексте присутствует marker сноски (`¹`, `*`,
     * etc.), в идеале сноска привязана к нему — пока [marker] noop.
     *
     * @property text текст сноски, переносы строк свёрнуты в пробелы как в Paragraph
     * @property marker маркер ссылки в основном тексте (`null` если не распознан)
     * @property source провенанс
     */
    public data class Footnote(
        public val text: String,
        public val marker: String? = null,
        public val source: List<SourceSpan> = emptyList(),
    ) : ReflowBlock
}
