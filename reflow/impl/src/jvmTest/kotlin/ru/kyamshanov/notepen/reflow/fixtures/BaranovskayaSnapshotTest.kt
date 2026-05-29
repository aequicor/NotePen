package ru.kyamshanov.notepen.reflow.fixtures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import ru.kyamshanov.notepen.reflow.ui.ReflowReader
import java.io.File
import kotlin.test.Test

/**
 * Roborazzi golden snapshots на реальном PDF (Барановская, грамматика английского).
 * Документ извлекается один раз и кэшируется в companion'е; в каждом тесте делаем
 * sub-document из среза блоков и рендерим через [ReflowReader].
 *
 * Тесты gracefully skip'ают, если фикстура отсутствует. Для записи golden'ов:
 *   `./gradlew :reflow:impl:jvmTest -Proborazzi.test.record=true`
 * Без флага — verify против записанного golden'а.
 *
 * Срезы выбраны под характерные кейсы:
 *  - **intro**: первые ~30 блоков, обычно заголовок-серия + intro-параграфы.
 *  - **section**: блоки с heading + текст + listitem.
 *  - **mid_book**: что-то из середины (упражнения), показывает Russian list-marker
 *    detection + heading ensemble в реальных условиях.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class BaranovskayaSnapshotTest {
    @Test
    fun introBlocks() {
        val doc = loadDoc() ?: return
        snapshot(name = "baranovskaya_intro", slice = doc.subDocument(0..30))
    }

    @Test
    fun midBookExercises() {
        val doc = loadDoc() ?: return
        val total = doc.blocks.size
        val start = (total * 0.4).toInt()
        snapshot(name = "baranovskaya_exercises", slice = doc.subDocument(start..(start + 30)))
    }

    @Test
    fun introWithLongReadingPreset() {
        val doc = loadDoc() ?: return
        snapshot(
            name = "baranovskaya_intro_long",
            slice = doc.subDocument(0..30),
            stored = StoredReaderSettings(current = BuiltinReaderPresets.longReading.settings),
        )
    }

    /**
     * Окно вокруг первой «крупной» таблицы (≥3 ряда и ≥3 колонки): включает
     * предшествующий heading + table. Защищает рендер таблиц от регрессий в
     * Stream/Lattice detector'е и UI [ReflowReader]'е.
     */
    @Test
    fun grammarTable() {
        val doc = loadDoc() ?: return
        val tableIdx =
            doc.blocks.indexOfFirst { block ->
                block is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Table &&
                    block.rows.size >= MIN_SNAPSHOT_TABLE_ROWS &&
                    (block.rows.firstOrNull()?.cells?.size ?: 0) >= MIN_SNAPSHOT_TABLE_COLS
            }
        if (tableIdx < 0) {
            println("[snapshot] no suitable table found — skipping grammarTable")
            return
        }
        val start = (tableIdx - SLICE_PRELUDE).coerceAtLeast(0)
        val end = (tableIdx + SLICE_AFTER).coerceAtMost(doc.blocks.size - 1)
        snapshot(name = "baranovskaya_table", slice = doc.subDocument(start..end))
    }

    /**
     * Окно с кластером из 4+ ListItem подряд (выровненный пер-уровень indent
     * в UI должен быть виден): покрывает Russian list-marker detection
     * («Упр./Задание/Пример N.») + visual hierarchy.
     */
    @Test
    fun listExercises() {
        val doc = loadDoc() ?: return
        val clusterStart = findListCluster(doc, minClusterSize = MIN_SNAPSHOT_LIST_CLUSTER)
        if (clusterStart < 0) {
            println("[snapshot] no list cluster found — skipping listExercises")
            return
        }
        val start = (clusterStart - SLICE_PRELUDE).coerceAtLeast(0)
        val end = (clusterStart + SLICE_AFTER).coerceAtMost(doc.blocks.size - 1)
        snapshot(name = "baranovskaya_list", slice = doc.subDocument(start..end))
    }

    /**
     * Окно с heading-иерархией L1→L2→L3 в пределах [SLICE_HEADING_WINDOW] блоков.
     * Защищает heading-ensemble + heading-render styling (font scale per level).
     */
    @Test
    fun headingHierarchy() {
        val doc = loadDoc() ?: return
        val start = findHeadingHierarchyStart(doc)
        if (start < 0) {
            println("[snapshot] no L1+L2+L3 hierarchy window found — skipping headingHierarchy")
            return
        }
        val end = (start + SLICE_HEADING_WINDOW).coerceAtMost(doc.blocks.size - 1)
        snapshot(name = "baranovskaya_heading_hierarchy", slice = doc.subDocument(start..end))
    }

    /** Индекс начала первой группы из [minClusterSize] подряд идущих ListItem'ов. */
    private fun findListCluster(
        doc: ReflowDocument,
        minClusterSize: Int,
    ): Int {
        var run = 0
        var runStart = -1
        for ((i, block) in doc.blocks.withIndex()) {
            if (block is ru.kyamshanov.notepen.reflow.api.ReflowBlock.ListItem) {
                if (run == 0) runStart = i
                run++
                if (run >= minClusterSize) return runStart
            } else if (block !is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Paragraph &&
                block !is ru.kyamshanov.notepen.reflow.api.ReflowBlock.Figure
            ) {
                // Допускаем абзацы (объяснения) и Figure (иллюстрации к упражнениям)
                // между list-item'ами; heading / table / divider сбрасывают run.
                run = 0
                runStart = -1
            }
        }
        return -1
    }

    /**
     * Ищет первое окно длиной [SLICE_HEADING_WINDOW], содержащее заголовки уровней
     * 1, 2 и 3 одновременно. Возвращает индекс окна или -1.
     */
    private fun findHeadingHierarchyStart(doc: ReflowDocument): Int {
        val blocks = doc.blocks
        for (start in 0 until blocks.size - SLICE_HEADING_WINDOW) {
            val window = blocks.subList(start, start + SLICE_HEADING_WINDOW)
            val levels =
                window
                    .filterIsInstance<ru.kyamshanov.notepen.reflow.api.ReflowBlock.Heading>()
                    .map { it.level }
                    .toSet()
            if (1 in levels && 2 in levels && 3 in levels) return start
        }
        return -1
    }

    private fun snapshot(
        name: String,
        slice: ReflowDocument,
        stored: StoredReaderSettings = StoredReaderSettings(current = ReaderSettings(paged = true)),
    ) {
        if (slice.blocks.isEmpty()) {
            println("[snapshot $name] empty slice — skipping")
            return
        }
        runDesktopComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                ReflowReader(
                    document = slice,
                    stored = stored,
                    onStoredChange = {},
                    barVisible = false,
                    onBarVisibleChange = {},
                    newPresetIdProvider = { "preset" },
                )
            }
            settle()
            onRoot().captureRoboImage("snapshots/$name.png")
        }
    }

    private fun ReflowDocument.subDocument(range: IntRange): ReflowDocument {
        val safeStart = range.first.coerceIn(0, blocks.size)
        val safeEnd = range.last.coerceIn(safeStart - 1, blocks.size - 1)
        return ReflowDocument(
            kind = kind,
            blocks = if (safeEnd >= safeStart) blocks.subList(safeStart, safeEnd + 1) else emptyList(),
        )
    }

    private fun loadDoc(): ReflowDocument? {
        val file = File(BARANOVSKAYA_PATH)
        if (!file.exists() || !file.canRead()) {
            println("[snapshot] Baranovskaya fixture not found — skipping snapshot tests")
            return null
        }
        cached?.let { return it }
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(BARANOVSKAYA_PATH) }
        cached = doc
        return doc
    }

    companion object {
        private const val BARANOVSKAYA_PATH =
            "/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf"

        /** Минимум рядов таблицы для snapshot — отсекает маленькие 2×3 grids. */
        private const val MIN_SNAPSHOT_TABLE_ROWS = 3

        /** Минимум колонок таблицы для snapshot — отсекает 2-колоночные key-value таблицы. */
        private const val MIN_SNAPSHOT_TABLE_COLS = 3

        /** Минимум подряд идущих ListItem'ов для «кластера». */
        private const val MIN_SNAPSHOT_LIST_CLUSTER = 4

        /** Сколько блоков ДО найденной точки интереса включить в slice (контекст). */
        private const val SLICE_PRELUDE = 2

        /** Сколько блоков ПОСЛЕ точки интереса включить в slice. */
        private const val SLICE_AFTER = 25

        /** Окно поиска L1+L2+L3 hierarchy в одной visual chunk'е. */
        private const val SLICE_HEADING_WINDOW = 40

        /**
         * Кеш извлечённого документа на время прогона класса: extract'a ~2s, делать его
         * 3 раза подряд — расточительно. Volatile + двойная проверка достаточны: тесты
         * Roborazzi запускаются последовательно, а `@Test` методы одного класса разделяют
         * companion'а.
         */
        @Volatile
        private var cached: ReflowDocument? = null
    }
}

/**
 * Прогоняет рекомпозицию/верстку (включая бэк-обмер высот через BlockHeightCalculator
 * на Dispatchers.Default) и завершает короткие анимации.
 *
 * `mainClock.advanceTimeBy` двигает только виртуальные часы Compose; bg-measure
 * крутится на реальном [kotlinx.coroutines.Dispatchers.Default] и `waitForIdle()`
 * его НЕ ждёт. Поэтому добавляем `Thread.sleep` чтобы дать CPU-работе finishнуть
 * перед снапшотом — иначе ловим loading-индикатор «Готовим читалку…».
 */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.settle() {
    waitForIdle()
    // Real-time wait для bg-measure (Dispatchers.Default).
    @Suppress("MagicNumber")
    Thread.sleep(500L)
    waitForIdle()
    mainClock.advanceTimeBy(2000)
    waitForIdle()
}
