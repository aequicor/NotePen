# Desktop hyphenation promotion

КД: десктопный reflow не может полагаться только на `Hyphens.Auto`: Skiko принимает `U+00AD` как точку разрыва, но не рисует дефис-глиф и не учитывает его ширину при `JUSTIFY`. Поэтому JVM-путь сам расставляет русские точки переноса по Кнуту-Лиэнгу и затем промоутит только реально разорванные soft-hyphen слоты в обычный `-`, который меряет и рисует сам текстовый движок.

Источник истины: `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReaderHyphenation.kt` (`ReaderHyphenation`, `readerHyphenation`), `ReaderHyphenPromotion.kt` (`promoteHyphens`, `resolveHyphenation`), `LiangHyphenator.kt`, JVM actual `reflow/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReaderHyphenation.jvm.kt`, Android actual `ReaderHyphenation.android.kt`, и рендер/обмер в `ReflowReader.kt` + `BlockHeightCalculator.kt`.

Инварианты: публичные `TextAnchor`/selection/source span смещения остаются в PLAIN-пространстве; `TextLayoutResult` работает в HYPH-пространстве и всегда конвертируется через `ReaderHyphenation`. `BlockHeightCalculator.measure` и render path обязаны идти через `resolveHyphenation`, иначе пагинация и отрисовка разойдутся. Кэш layout поднят до `ReflowLayoutBinaryFormat.VERSION = 5`, потому что промоутнутый дефис занимает ширину и меняет line breaks.

Проверка: `./gradlew :reflow:impl:jvmTest :app:byCompose:common:jvmTest`. Связанные тесты: `ReaderHyphenationTest`, `LiangHyphenatorTest`, `ReaderHyphenPromotionTest`, `RuHyphenationTest`, `JustifyShyBreakDiagnosticTest`, `WordParityHyphenRenderTest`, `FileSystemReflowLayoutCacheTest`.
