package ru.kyamshanov.notepen.background

import androidx.compose.ui.graphics.Color

/**
 * Каталог стилей фона документа («текстурированная бумага»).
 *
 * Persisted-значение — это [Style.id] (плоская строка в
 * [ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState.backgroundStyle]).
 * Сам каталог живёт в app-слое: `:rendering:impl` / `:reflow:impl` не зависят от
 * `:app:byCompose:common` и получают уже готовую плиточную кисть параметром (см.
 * [rememberPaperBrush]). Неизвестный id мягко откатывается к [PLAIN].
 *
 * **Текстуры сейчас процедурные** (мелкое зерно поверх базового тона) — это bootstrap,
 * чтобы фича работала и тестировалась до появления реальной арт-графики. Когда заказчик
 * пришлёт бесшовные PNG-плитки, добавляем `composeResources/drawable/paper_<id>.png`
 * (+ `_dark`), даём [Style] ссылки на `DrawableResource` и переключаем загрузку в
 * [rememberPaperBrush] на `imageResource(...)`. Сам контракт (id ↔ стиль) при этом
 * не меняется.
 */
public object PaperBackgrounds {
    /** Сентинел «обычный фон» — кисть не строится, поведение как до фичи. */
    public const val PLAIN: String = "plain"

    /**
     * Описание одного бумажного стиля. [baseLight]/[baseDark] — базовый тон под
     * светлую/тёмную тему; [grain] + [grainAlpha] задают цвет и силу зерна
     * процедурной плитки (до подмены на реальные PNG).
     */
    public data class Style(
        val id: String,
        val displayName: String,
        val baseLight: Color,
        val baseDark: Color,
        val grain: Color,
        val grainAlpha: Float,
    )

    /** Доступные стили (без [PLAIN]). Порядок = порядок в пикере. */
    public val styles: List<Style> =
        listOf(
            Style(
                id = "cream",
                displayName = "Кремовая",
                baseLight = Color(0xFFFBF6EC),
                baseDark = Color(0xFF211F1A),
                grain = Color(0xFF8C7A55),
                grainAlpha = 0.07f,
            ),
            Style(
                id = "linen",
                displayName = "Лён",
                baseLight = Color(0xFFF2EEE4),
                baseDark = Color(0xFF1E1D1A),
                grain = Color(0xFF847C66),
                grainAlpha = 0.08f,
            ),
            Style(
                id = "gray",
                displayName = "Серая",
                baseLight = Color(0xFFEDECEA),
                baseDark = Color(0xFF1F1F1F),
                grain = Color(0xFF80807C),
                grainAlpha = 0.06f,
            ),
        )

    /** Стиль по id или `null`, если id неизвестен / равен [PLAIN]. */
    public fun byId(id: String): Style? = styles.firstOrNull { it.id == id }

    /** Есть ли у документа непустой (нарисованный) фон. */
    public fun isTextured(id: String): Boolean = id != PLAIN && byId(id) != null
}
