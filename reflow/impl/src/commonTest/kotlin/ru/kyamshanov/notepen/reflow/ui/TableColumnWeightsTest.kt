package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Покрывает [tableColumnWeights] — общий базис пропорциональных ширин колонок,
 * которым пользуются и [TableView] (рендер), и [BlockHeightCalculator.measureTable]
 * (обмер). Defect M: при равной ширине колонок узкие столбцы рвали слова по
 * символам, поэтому вес колонки масштабируется длиной её крупнейшей ячейки.
 */
class TableColumnWeightsTest {
    private fun table(rows: List<List<String>>): ReflowBlock.Table =
        ReflowBlock.Table(
            rows = rows.map { cells -> ReflowBlock.TableRow(cells = cells.map { ReflowBlock.TableCell(it) }) },
        )

    @Test
    fun emptyTable_yieldsNoWeights() {
        assertEquals(emptyList(), tableColumnWeights(ReflowBlock.Table(rows = emptyList())))
    }

    @Test
    fun weightTracksLongestCellInColumn() {
        // Колонка 0 максимум 4 ("play"/"swim"), колонка 1 максимум 5 ("piano").
        val weights =
            tableColumnWeights(
                table(
                    listOf(
                        listOf("play", "piano"),
                        listOf("swim", "fast"),
                    ),
                ),
            )
        assertEquals(2, weights.size)
        assertEquals(4f, weights[0])
        assertEquals(5f, weights[1])
        // Содержательная колонка получает БОЛЬШЕ ширины, чем была бы при равном делении.
        assertTrue(weights[1] > weights[0])
    }

    @Test
    fun shortColumnsDoNotCollapseBelowMinimum() {
        // Колонка из односимвольных ячеек не схлопывается в нечитаемую полоску:
        // её вес поднимается до минимума, тогда как длинная колонка остаётся выше.
        val weights =
            tableColumnWeights(
                table(
                    listOf(
                        listOf("x", "a long descriptive header"),
                        listOf("y", "another wide value here"),
                    ),
                ),
            )
        assertEquals(2, weights.size)
        assertTrue(weights[0] >= 1f, "short column kept a usable minimum width, was ${weights[0]}")
        assertTrue(weights[1] > weights[0])
    }

    @Test
    fun raggedRows_weightsSpanWidestRow() {
        // Один ряд шире прочих — число колонок = максимуму по всем рядам.
        val weights =
            tableColumnWeights(
                table(
                    listOf(
                        listOf("a", "bb"),
                        listOf("c", "dd", "eeee"),
                    ),
                ),
            )
        assertEquals(3, weights.size)
        assertEquals(4f, weights[2])
    }
}
