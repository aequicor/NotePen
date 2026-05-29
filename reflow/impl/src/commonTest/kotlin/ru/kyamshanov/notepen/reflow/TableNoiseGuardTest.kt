package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit-тесты [TableNoiseGuard] на представительных строках, снятых с реальных
 * данных (Барановская — фантомные таблицы; thesis-фикстура — легитимные). Пороги
 * откалиброваны так, чтобы первые отсекались, вторые — нет (F-1 / F-8).
 */
class TableNoiseGuardTest {
    private fun row(vararg cells: String) = ReflowBlock.TableRow(cells.map { ReflowBlock.TableCell(it) })

    @Test
    fun `pure single-char grid is noise (F-1 avg floor)`() {
        // 7 колонок × 2 ряда по 1 символу (как фантомный Lattice-грид F-7).
        val rows = List(2) { row("А", "р", "т", "и", "к", "л", "ь") }
        assertTrue(TableNoiseGuard.isOcrNoiseTable(rows))
    }

    @Test
    fun `spaced-letter prose is noise (F-8) — '(О с-нова-на-в-1997-году)'`() {
        // Барановская стр.2: «(Основана в 1997 году)» нарезано по глифам.
        val rows =
            listOf(
                row("", "«В а с", "ж д", "ет", "у сп е", "х»"),
                row("(О с", "нова", "на", "в", "1997", "году)"),
            )
        assertTrue(TableNoiseGuard.isOcrNoiseTable(rows), "spaced-letter OCR prose must be rejected")
    }

    @Test
    fun `wide shredded copyright grid is noise (F-8)`() {
        // 3 ряда × много колонок мелких фрагментов (copyright-абзац, разрезанный по глифам).
        val rows =
            listOf(
                row("", "", "Ба", "р", "анов", "с", "кая", "Т", ".В", ".", "Грамм", "а", "тика", "а", "н"),
                row("глийс", "ког", "о языка.", "Сборн", "ик", "упражнений", ":", "У", "чеб,", "п", "ос", "о", "бие.", "И", "здани"),
                row("е", "второ", "е,", "исправле", "нное", "и до", "полненное.", "-", "Язык", "а", "нг", "л", ".", "русс", "кий"),
            )
        assertTrue(TableNoiseGuard.isOcrNoiseTable(rows), "wide shredded prose grid must be rejected")
    }

    @Test
    fun `legit short text table stays (Name-Age)`() {
        val rows =
            listOf(
                row("Name", "Age"),
                row("Anna", "30"),
                row("Bob", "25"),
            )
        assertFalse(TableNoiseGuard.isOcrNoiseTable(rows), "real short-cell table must survive")
    }

    @Test
    fun `legit wide thesis-like table stays (high letter density)`() {
        // Аналог thesis [2]: 21 колонка, но содержательные ячейки (avg букв высок).
        val cells = (1..16).map { "Indicator$it" }.toTypedArray()
        val rows = listOf(row(*cells), row(*Array(16) { "value-${it * 100}" }))
        assertFalse(TableNoiseGuard.isOcrNoiseTable(rows), "wide table with real content must survive")
    }

    @Test
    fun `legit collocation list stays (to have breakfast)`() {
        // Барановская стр.5 [81]: реальный 2-словный список — НЕ задеваем.
        val rows =
            listOf(
                row("to have", "/", "breakfast"),
                row("to have", "/", "lunch"),
                row("to have", "/", "dinner"),
            )
        assertFalse(TableNoiseGuard.isOcrNoiseTable(rows), "real collocation list must survive")
    }

    @Test
    fun `empty table is not noise`() {
        assertFalse(TableNoiseGuard.isOcrNoiseTable(listOf(row("", ""), row("", ""))))
    }
}
