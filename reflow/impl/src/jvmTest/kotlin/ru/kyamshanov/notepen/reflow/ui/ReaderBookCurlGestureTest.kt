package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.reflow.api.PageTransition
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReaderSettings
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.StoredReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// v2 runDesktopComposeUiTest uses StandardTestDispatcher (queued coroutines);
// the explicit clock stepping below mirrors existing reader snapshot tests.
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class ReaderBookCurlGestureTest {
    @Test
    fun bookCurlDragFrameDoesNotGloballyDimReader() =
        runDesktopComposeUiTest(width = READER_WIDTH, height = READER_HEIGHT) {
            mainClock.autoAdvance = false
            var stored by mutableStateOf(
                StoredReaderSettings(
                    current =
                        ReaderSettings(
                            paged = true,
                            pageTransition = PageTransition.BOOK,
                        ),
                ),
            )
            setContent {
                Box(Modifier.size(READER_WIDTH.dp, READER_HEIGHT.dp)) {
                    ReflowReader(
                        document = gestureDoc(),
                        stored = stored,
                        onStoredChange = { stored = it },
                        barVisible = false,
                        onBarVisibleChange = {},
                        newPresetIdProvider = { "preset" },
                    )
                }
            }
            settleReader()

            onRoot().performTouchInput {
                down(Offset(x = width - EDGE_DRAG_INSET_PX, y = height * GRIP_Y_FRACTION))
                moveBy(Offset(x = -width * DRAG_DISTANCE_FRACTION, y = 0f))
                advanceEventTime(64)
            }
            waitForIdle()
            mainClock.advanceTimeBy(80)
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val topLeft = frame.averageLuminance(xRange = 8 until 96, yRange = 8 until 96)
            val bottomLeft = frame.averageLuminance(xRange = 8 until 96, yRange = (READER_HEIGHT - 96) until (READER_HEIGHT - 8))

            onRoot().performTouchInput { up() }

            assertTrue(topLeft > MIN_READER_CORNER_LUMINANCE, "top-left reader corner must not be globally dimmed: $topLeft")
            assertTrue(
                bottomLeft > MIN_READER_CORNER_LUMINANCE,
                "bottom-left reader corner must not be globally dimmed: $bottomLeft",
            )
        }

    @Test
    fun underTurnedBookCurlReturnsWithoutChangingPage() =
        runDesktopComposeUiTest(width = READER_WIDTH, height = READER_HEIGHT) {
            mainClock.autoAdvance = false
            var firstBlock = -1
            setBookReader(onFirstBlockChange = { firstBlock = it })
            settleReader()
            val initialBlock = firstBlock

            onRoot().performTouchInput {
                down(Offset(x = width - EDGE_DRAG_INSET_PX, y = height * GRIP_Y_FRACTION))
                repeat(SLOW_DRAG_STEPS) {
                    moveBy(Offset(x = -width * CANCEL_DRAG_DISTANCE_FRACTION / SLOW_DRAG_STEPS, y = 0f))
                    advanceEventTime(SLOW_DRAG_STEP_MS)
                }
                up()
            }
            waitForIdle()
            mainClock.advanceTimeBy(PAGE_CURL_TEST_SETTLE_MS)
            waitForIdle()

            assertEquals(initialBlock, firstBlock, "under-turned curl must spring back to the current page")
        }

    @Test
    fun completedBookCurlCommitsAfterSettle() =
        runDesktopComposeUiTest(width = READER_WIDTH, height = READER_HEIGHT) {
            mainClock.autoAdvance = false
            var firstBlock = -1
            setBookReader(onFirstBlockChange = { firstBlock = it })
            settleReader()
            val initialBlock = firstBlock

            onRoot().performTouchInput {
                down(Offset(x = width - EDGE_DRAG_INSET_PX, y = height * GRIP_Y_FRACTION))
                repeat(COMMIT_DRAG_STEPS) {
                    moveBy(Offset(x = -width * COMMIT_DRAG_DISTANCE_FRACTION / COMMIT_DRAG_STEPS, y = 0f))
                    advanceEventTime(COMMIT_DRAG_STEP_MS)
                }
                up()
            }
            waitForIdle()
            mainClock.advanceTimeBy(PAGE_CURL_TEST_SETTLE_MS)
            waitForIdle()

            assertTrue(
                firstBlock > initialBlock,
                "completed curl must commit to the next page after settle: initial=$initialBlock current=$firstBlock",
            )
        }

    @Test
    fun flingBookCurlCommitsBelowProgressThreshold() =
        runDesktopComposeUiTest(width = READER_WIDTH, height = READER_HEIGHT) {
            mainClock.autoAdvance = false
            var firstBlock = -1
            setBookReader(onFirstBlockChange = { firstBlock = it })
            settleReader()
            val initialBlock = firstBlock

            onRoot().performTouchInput {
                down(Offset(x = width - EDGE_DRAG_INSET_PX, y = height * GRIP_Y_FRACTION))
                moveBy(Offset(x = -width * FLING_DRAG_DISTANCE_FRACTION, y = 0f))
                advanceEventTime(FLING_DRAG_STEP_MS)
                up()
            }
            waitForIdle()
            mainClock.advanceTimeBy(PAGE_CURL_TEST_SETTLE_MS)
            waitForIdle()

            assertTrue(
                firstBlock > initialBlock,
                "fling curl must commit even when progress is below threshold: initial=$initialBlock current=$firstBlock",
            )
        }

    @Test
    fun completedBookCurlCommitsTwoPageSpread() =
        runDesktopComposeUiTest(width = READER_WIDTH, height = READER_HEIGHT) {
            mainClock.autoAdvance = false
            var firstBlock = -1
            setBookReader(
                settings =
                    ReaderSettings(
                        paged = true,
                        pageTransition = PageTransition.BOOK,
                        twoPageSpread = true,
                    ),
                onFirstBlockChange = { firstBlock = it },
            )
            settleReader()
            val initialBlock = firstBlock

            onRoot().performTouchInput {
                down(Offset(x = width - EDGE_DRAG_INSET_PX, y = height * GRIP_Y_FRACTION))
                repeat(COMMIT_DRAG_STEPS) {
                    moveBy(Offset(x = -width * COMMIT_DRAG_DISTANCE_FRACTION / COMMIT_DRAG_STEPS, y = 0f))
                    advanceEventTime(COMMIT_DRAG_STEP_MS)
                }
                up()
            }
            waitForIdle()
            mainClock.advanceTimeBy(PAGE_CURL_TEST_SETTLE_MS)
            waitForIdle()

            assertTrue(
                firstBlock > initialBlock,
                "completed curl must commit to the next spread: initial=$initialBlock current=$firstBlock",
            )
        }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.setBookReader(
    settings: ReaderSettings =
        ReaderSettings(
            paged = true,
            pageTransition = PageTransition.BOOK,
        ),
    onFirstBlockChange: (Int) -> Unit,
) {
    var stored by mutableStateOf(
        StoredReaderSettings(
            current = settings,
        ),
    )
    setContent {
        Box(Modifier.size(READER_WIDTH.dp, READER_HEIGHT.dp)) {
            ReflowReader(
                document = gestureDoc(),
                stored = stored,
                onStoredChange = { stored = it },
                barVisible = false,
                onBarVisibleChange = {},
                newPresetIdProvider = { "preset" },
                onFirstBlockChange = onFirstBlockChange,
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.settleReader() {
    waitForIdle()
    mainClock.advanceTimeBy(600)
    waitForIdle()
}

private fun androidx.compose.ui.graphics.PixelMap.averageLuminance(
    xRange: IntRange,
    yRange: IntRange,
): Float {
    var sum = 0f
    var count = 0
    for (y in yRange) {
        for (x in xRange) {
            val color = this[x, y]
            sum += color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            count += 1
        }
    }
    return sum / count.coerceAtLeast(1)
}

private fun gestureDoc(): ReflowDocument =
    ReflowDocument(
        kind = PdfContentKind.TEXT_BASED,
        blocks =
            buildList {
                add(ReflowBlock.Heading("Проверка книжного перелистывания", level = 1))
                repeat(22) { index ->
                    add(ReflowBlock.Paragraph("Абзац ${index + 1}. $GESTURE_PARAGRAPH"))
                }
            },
    )

private const val GESTURE_PARAGRAPH =
    "Страница содержит достаточно текста для нескольких экранов reader mode; это нужно, чтобы жест перелистывания " +
        "имел соседнюю страницу, frozen texture и реальный путь BookCurlOverlay поверх pager."

private const val READER_WIDTH = 560
private const val READER_HEIGHT = 760
private const val EDGE_DRAG_INSET_PX = 8f
private const val GRIP_Y_FRACTION = 0.62f
private const val DRAG_DISTANCE_FRACTION = 0.26f
private const val MIN_READER_CORNER_LUMINANCE = 0.58f
private const val CANCEL_DRAG_DISTANCE_FRACTION = 0.18f
private const val COMMIT_DRAG_DISTANCE_FRACTION = 0.46f
private const val FLING_DRAG_DISTANCE_FRACTION = 0.12f
private const val SLOW_DRAG_STEPS = 8
private const val COMMIT_DRAG_STEPS = 10
private const val SLOW_DRAG_STEP_MS = 20L
private const val COMMIT_DRAG_STEP_MS = 16L
private const val FLING_DRAG_STEP_MS = 8L
private const val PAGE_CURL_TEST_SETTLE_MS = 520L
