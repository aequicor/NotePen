# Двухстраничный reflow-разворот

Контекст: режим чтения должен уметь показывать две текстовые страницы рядом, как раскрытую книгу, но источник истины для пагинации остаётся прежним: один непрерывный поток блоков режется на `ReaderPagination.PageWindow`. Разворот не создаёт отдельную модель страниц, а группирует уже рассчитанные окна по два.

Источник истины: настройки живут в `reflow/api/.../ReaderSettings.kt` (`ReaderSettings.twoPageSpread`, `PageTransition.BOOK`, `pageTurnSound`). Отображение находится в `reflow/impl/.../ui/ReflowReader.kt`: `PagedReflowContent` считает ширину одной колонки, `PageWindowColumn` рендерит одно окно, а `spreadCount`/`windowPageToSpread` переводят окна в развороты. Звук перелистывания изолирован в expect/actual `PageTurnSound.kt`, `PageTurnSound.android.kt`, `PageTurnSound.jvm.kt`.

Инварианты и острые края: `ReaderPagination` не должен знать о разворотах, иначе сломается сохранение позиции через `TextAnchor`. В развороте один длинный блок может одновременно оказаться в левой и правой колонке, поэтому `ReflowSelectionState` хранит координаты по `(blockIndex, occurrenceKey)`, а не только по `blockIndex`; для страницы `occurrenceKey` равен индексу окна. Звук должен вызываться по фактической смене `HorizontalPager.currentPage`, чтобы работать для свайпа, тап-зон и аппаратных клавиш.

Проверка: `./gradlew :reflow:impl:compileKotlinJvm`, `./gradlew :reflow:impl:compileAndroidMain`.

