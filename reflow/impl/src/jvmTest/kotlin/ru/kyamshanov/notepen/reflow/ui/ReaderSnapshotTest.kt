package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import ru.kyamshanov.notepen.reflow.api.BuiltinReaderPresets
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import kotlin.test.Test

/**
 * JVM/desktop-снапшоты ридера (Roborazzi, без эмулятора). Гонятся в `build/` своего
 * worktree, поэтому параллельным сессиям не мешают (см. скил snapshot-testing).
 *
 * `mainClock.autoAdvance = false` — у ридера есть вечный тикер сессии
 * (`LaunchedEffect { while(true){ delay() } }`); при автопрокрутке часов он крутил бы
 * рекомпозицию до бесконечности и `waitForIdle()` не вернулся бы.
 */
// v2 runDesktopComposeUiTest uses StandardTestDispatcher (queued coroutines);
// migrating would re-time the mainClock/settle() logic these snapshots depend on.
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class ReaderSnapshotTest {
    /**
     * Скролл-режим: текст не доходит до самого низа — снизу постоянное поле под airbar
     * (его показ/скрытие не двигает текст). Кадр с раскрытой панелью здесь не снимаем:
     * колесо пресетов airbar в headless-тесте не приходит к idle и подвешивает
     * `waitForIdle()` — это ограничение тестовой среды, а не поведение приложения;
     * перекрытие панелью проверяем живьём.
     */
    @Test
    fun scrollModeReservesSpaceForBar() =
        runDesktopComposeUiTest {
            mainClock.autoAdvance = false
            val doc = sampleDoc()
            setContent {
                ReflowReader(
                    document = doc,
                    stored = StoredReaderSettings(current = ReaderSettings(paged = false)),
                    onStoredChange = {},
                    barVisible = false,
                    onBarVisibleChange = {},
                    newPresetIdProvider = { "preset" },
                )
            }
            settle()
            onRoot().captureRoboImage("snapshots/scroll_bar_hidden.png")
        }

    /**
     * Страничный режим: при смене типографики (компактный → долгое чтение, крупнее) высоты
     * блоков пересчитываются, поэтому низ страницы не обрезается. Без переснятия высот текст
     * пропадал бы между страницами (см. PagedReflowContent.measureKey).
     */
    @Test
    fun pagedRemeasuresWhenTypographyChanges() =
        runDesktopComposeUiTest {
            mainClock.autoAdvance = false
            val doc = sampleDoc()
            var stored by mutableStateOf(StoredReaderSettings(current = BuiltinReaderPresets.compact.settings))
            setContent {
                ReflowReader(
                    document = doc,
                    stored = stored,
                    onStoredChange = { stored = it },
                    barVisible = false,
                    onBarVisibleChange = {},
                    newPresetIdProvider = { "preset" },
                )
            }
            settle()
            onRoot().captureRoboImage("snapshots/paged_compact.png")
            stored = StoredReaderSettings(current = BuiltinReaderPresets.longReading.settings)
            settle()
            onRoot().captureRoboImage("snapshots/paged_enlarged.png")
        }
}

/** Прогоняет рекомпозицию/верстку (включая невидимый проход обмера высот) и короткую анимацию. */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.settle() {
    waitForIdle()
    mainClock.advanceTimeBy(400)
    waitForIdle()
}

/** Документ из заголовка и десятка абзацев — хватает на несколько страниц при любом пресете. */
private fun sampleDoc(): ReflowDocument =
    ReflowDocument(
        kind = PdfContentKind.TEXT_BASED,
        blocks =
            buildList {
                add(ReflowBlock.Heading("Холодный и горячий поток", level = 1))
                repeat(16) { i ->
                    add(ReflowBlock.Paragraph("Абзац ${i + 1}. $PARAGRAPH"))
                }
            },
    )

private const val PARAGRAPH: String =
    "StateFlow всегда хранит одно текущее значение, и любой новый подписчик сразу получает его; " +
        "SharedFlow же эмитит события без удержания состояния. Этот абзац набран достаточно длинным, " +
        "чтобы переноситься на несколько строк при разной ширине колонки и кегле."
