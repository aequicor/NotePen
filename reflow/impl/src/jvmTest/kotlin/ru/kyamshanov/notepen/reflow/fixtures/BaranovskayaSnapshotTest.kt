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
