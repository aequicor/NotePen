# Промпт: автономное UI/UX тестирование ридера NotePen с авто-фиксом

> **Как использовать:** скопировать всё ниже (от строки `# ЗАДАНИЕ`) в новую сессию Claude Code, запущенную в `/Users/kruz18/IdeaProjects/NotePen`. Сессия должна быть в режиме без подтверждений (`--dangerously-skip-permissions` или auto-approve), потому что задача автономная и предполагает многочасовой цикл правок и сборок.

---

# ЗАДАНИЕ

Ты — автономный QA + dev агент. Твоя цель — довести reflow-ридер NotePen (Kotlin Multiplatform, Compose) до состояния «комфортного чтения PDF/FB2/EPUB» на трёх классах устройств (Desktop, Android tablet с пером, Android phone эмулятор), в **обеих** ориентациях (landscape + portrait). Бюджет токенов и времени **не ограничен**. Останавливаться можно только когда выполнен приёмочный критерий ниже.

## Приёмочный критерий (acceptance)

Ридер считается принятым, **когда одновременно**:

1. На каждом из четырёх профилей (Desktop / Huawei TGR-W09 landscape / Huawei TGR-W09 portrait / Android emulator API 36 phone portrait) можно открыть **любую** из фикстур ниже, переключиться в reading mode, перелистать ≥ 30 страниц и подтвердить:
   - пробелы/переносы/знаки препинания корректные (нет «р а з о р в а н н ы х» слов, нет потерянных дефисов на границе страниц, нет двойных пробелов);
   - перечисления (нумерованные и маркированные) выглядят как списки, а не как параграфы со случайными цифрами;
   - таблицы — это таблицы (с границами, с заголовком из `isHeader`, цельные после cross-page merge), а **не** колонки из 1-символьных ячеек;
   - графики/figures показаны как изображения, не как плейсхолдеры (если PDF позволяет) и без скачков высоты при ре-композиции;
   - выделения (highlights) рендерятся вместе с текстом и не «соскакивают» при ре-пагинации;
   - типографика соответствует современным e-readers (см. эталон ниже);
2. Тёмная тема (Ночь) **читабельна**: контраст основного текста ≥ 4.5:1, заголовков ≥ 3:1 (WCAG AA), italic/blockquote/code/footnote — отдельно проверены;
3. Нет видимых лагов при перелистывании (subjective: ≤ 250 мс кадра первой страницы, ≤ 100 мс на смену) и нет зависаний > 1 с при смене типографики, пресета или темы;
4. При смене ориентации, темы, пресета, размера шрифта — **место чтения сохраняется** (визуально подтверждено: тот же абзац виден после ре-пагинации);
5. `./gradlew check` зелёный (включая существующие snapshot-тесты), и **добавленные тобой** скриншот-тесты на новые сценарии (минимум один на каждый класс фикстур и каждый профиль ориентации) — тоже зелёные.

Принимать ридер «частично» нельзя — все 5 пунктов одновременно.

## Эталон современной типографики

Сверяйся с: Apple Books, Google Play Books, Kindle Paperwhite, KOReader, Calibre. Конкретно:
- Тело текста: serif или книжный sans, 14–18 sp на телефоне / 16–22 sp на планшете, line-height 1.4–1.6;
- Заголовки: SemiBold/Bold, размер 1.2× / 1.5× / 2.0× базы, отступы сверху больше чем снизу (orphan control);
- Перечисления: висячий маркер, 1.5–2 em отступ от левого края;
- Цитата (blockquote): тонкая левая граница, italic, чуть меньше базы;
- Footnote: ~0.85× базы, alpha ~0.75, мелкий superscript-маркер в основном тексте;
- Code: моноширинный шрифт, без переноса слов, лёгкий фон;
- Hyphenation: включена для оправданного текста, выключена для левого выравнивания;
- Justify не должен давать «реки» (визуальный gap > 1.5 em).

## Surface (что именно проверять)

### Куски кода, которые рендерят ридер

- `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt` — главный composable.
- `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssembler.kt` — сборка блоков из текста + табличный детектор.
- `reflow/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/reflow/PdfReflowExtractor.kt` (если есть) — извлечение текста через PDFBox.
- `reflow/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/reflow/TaggedPdfHeadings.kt` — повышение заголовков.
- `reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/BlockHeightCalculator.kt` — измерение блоков.
- `reflow/api/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/api/ReflowBlock.kt` — модель блоков, включая `TableRow.isHeader`, `Code`, `Footnote`.
- `app/byCompose/common/src/.../book/`, `epub/` — конвертация ebook → PDF (для FB2/EPUB).
- Android-специфика — `app/byCompose/android/` и платформенные actuals в `*/src/androidMain/`.

### Фикстуры

| Путь | Что покрывает |
|---|---|
| `reflow/impl/src/jvmTest/resources/fixtures/thesis-mixed-content.pdf` | TEXT_BASED, ~136K символов, чистые таблицы и списки |
| `reflow/impl/src/jvmTest/resources/fixtures/article-org-risks.pdf` | TEXT_BASED smoke |
| `/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf` | **HYBRID/OCR**, русский, 383 стр., грамматический справочник с таблицами — самый сложный случай |
| `/Users/kruz18/Documents/english/Student's Book_202509160920_22009.pdf` | IMAGE_ONLY/сканы |
| `/Users/kruz18/Downloads/Telegram Desktop/Isiguro_Klara-i-Solnce.aQ1xpw.619657.fb2` | **FB2 → PDF** конверсия, чистая художественная проза |

Если каких-то фикстур нет — отметить как coverage gap, не падать. Минимум: thesis + Baranovskaya + Isiguro.

Дополнительно: возьми **2 EPUB** из публичных Project Gutenberg (англ + рус) и положи в `~/Downloads/test-fixtures/` чтобы перекрыть EPUB-путь.

## Известные дефекты (baseline)

Уже найдены и зафиксированы в `.claude/ux-reports/20260529-124456/REPORT.md`. Перечитай его в начале сессии, чтобы не дублировать работу. Кратко:

1. **F-1 КРИТИКА** — assembler делает «таблицу» из обычной прозы в OCR-PDF (`Барановская`): каждое 1-2 символьное «ячейка». Чинить в `ReflowAssembler.kt` (порог отказа от таблицы: средняя длина ячейки ≤ 3, колонок > 8, либо словарная проверка ломаных слов).
2. **F-2 РЕГРЕССИЯ** — `TableView` в [ReflowReader.kt:1383](reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/ReflowReader.kt:1383) использует `rowIndex == 0` вместо `row.isHeader`. Wave-3 поставил флаг, рендер не обновлён. Однострочный фикс.
3. **F-3 РЕГРЕССИЯ** — заголовки промотируются из OCR-битого текста (`Лр тиклъ` вместо `Артикль`). Добавить фильтр «слишком много одиночных букв в кандидате» в `TaggedPdfHeadings.kt`.
4. **F-4 ПОЛИШ** — TOC FB2 показывает безымянные `<section>` как `...` × ~20 раз. Либо скрывать, либо подставлять первый параграф.
5. **F-5 ПОЛИШ** — точка входа в peer catalog не находится из библиотечного top bar.
6. **F-6 ПОЛИШ** — стрелка `Right` не листает в reflow mode (надо подключить keyboard handler к `onPageDeltaReady` на desktop entrypoint).

Эти 6 — твоя стартовая точка. По мере прохода найдёшь больше.

## Цикл работы (auto-fix loop)

Работай итеративно. Каждая итерация:

1. **Триаж** — выбери максимум 3 дефекта приоритета (severity: critical > regression > polish, и blast-radius: чем больше профилей затрагивает, тем выше). Старт — F-1, F-2, F-3.
2. **Фикс** — внеси изменение. Маленькими коммитами в **отдельную ветку** `qa/auto-fix-<date>`. Не пуш на master. На каждый коммит — одна логическая правка.
3. **Локальная регрессия**:
   - `./gradlew detekt ktlintCheck` (+ при необходимости `:<module>:detektBaseline`);
   - `./gradlew :reflow:impl:jvmTest :app:byCompose:common:jvmTest`;
   - все snapshot-тесты в `reflow/impl/snapshots/` — если изменения визуальные и **ожидаемые**, пересними golden через `-Proborazzi.test.record=true` и зафиксируй в коммите. Любое НЕ-ожидаемое изменение golden — стоп, разбираться.
4. **UI-регрессия** — заново прогон полного скрипта тестирования (см. ниже) на всех 4 профилях для затронутых сценариев.
5. **Если регрессия зелёная** — продолжаем со следующей пачкой дефектов; если красная — откат фикса (`git revert`) и думаем заново.
6. **Если найдены новые дефекты** — добавляем в очередь.

Повторяем, пока очередь не пуста И все 5 приёмочных пунктов выполнены. Финальный коммит — обновлённый отчёт.

## Профили тестирования

### Profile 1: Desktop (macOS, JBR 21)

- **Запуск:** `./gradlew :app:byCompose:desktop:createDistributable && open app/byCompose/desktop/build/compose/binaries/main/app/NotePen.app`. **НЕ** через `runDesktop` — гredle-spawned JVM не имеет `.app` bundle, и macOS computer-use MCP его прячет из скриншотов (см. F-7 ниже).
- **Драйвер UI:** `mcp__computer-use__*` (потребует `request_access` для `NotePen`).
- **Гетча 1 (screenshot persist):** в этом харнесе `save_to_disk:true` у screenshot не отдаёт путь. Если нужно сохранить — используй `Read` от inline-байтов и `Write` в файл, либо запиши screenshot из теста через Roborazzi. Сторонне `screencapture` блокируется песочницей.
- **Гетча 2 (тапы по краю):** правый/левый tap-zone листают; центральный — переключает airbar. Right/Left arrow клавиши **сейчас не листают** — пока F-6 не починен, для тестового перехода между страницами используй tap-зоны или `pagerState.scrollToPage` через тестовый hook.

### Profile 2: Huawei TGR-W09 (Android 12, real stylus)

- **Подготовка SDK:** уже в PATH из `~/.zshrc` (`ANDROID_HOME=~/Library/Android/sdk`). Серийный номер: `44RUN24B09G03494`.
- **Бэкап перед каждым reinstall** (у пользователя там реальные разметки!):
  ```bash
  adb -s 44RUN24B09G03494 exec-out run-as ru.kyamshanov.notepen tar -cf - files databases shared_prefs \
    > .claude/ux-reports/<run-id>/huawei-backup/notepen-data.tar
  ```
- **Гетча: формат tar** — macOS tar пишет pax-заголовки, Android toybox-tar валится с `bad symlink` / `Read-only file system`. Пересобирай в ustar перед обратным push:
  ```bash
  mkdir -p ~/tmp/np-extract && tar -xf <backup>.tar -C ~/tmp/np-extract
  (cd ~/tmp/np-extract && tar --format=ustar -cf <backup>-ustar.tar files databases shared_prefs)
  ```
- **Гетча: восстановление из /sdcard run-as не может** — пихай tar через stdin:
  ```bash
  cat <backup>-ustar.tar | adb exec-in run-as ru.kyamshanov.notepen tar -xf - -C /data/data/ru.kyamshanov.notepen/
  ```
- **Гетча: URI после reinstall ломаются** — у пользователя файлы в библиотеке начинают показывать «Ошибка доступа к файлу», даже если PDF физически на диске. Это F-EXTRA-1, занеси в очередь: либо ре-резолвить URI по имени файла, либо чистить непригодные history-записи. На время тестов пользуйся `+ Открыть` и пушни нужный PDF на `/sdcard/Download/` через `adb push`.
- **Гетча: applicationId конфликт** — debug-вариант имеет тот же `applicationId = "ru.kyamshanov.notepen"`. **Перед installDebug** добавь в [app/byCompose/android/build.gradle.kts](app/byCompose/android/build.gradle.kts) debug `applicationIdSuffix = ".debug"`. Тогда дебаг-сборка живёт рядом с боевой, и сносить боевую не надо. Это не должно остаться в master — отдельный коммит, в финале revert или вынеси в `buildTypes { debug { ... } }` так, чтобы CI был чист.
- **Драйвер UI:** `adb shell input tap/swipe`, `adb exec-out screencap`.
- **Ориентации:** `adb shell settings put system user_rotation 0` (portrait) / `1` (landscape) + `adb shell content insert --uri content://settings/system --bind name:s:accelerometer_rotation --bind value:i:0`.
- **Стилус:** Huawei M-Pencil. Реальную силу нажатия в скрипте имитировать нельзя — для проверки stylus path нужен ручной тык (либо синтетический MotionEvent через `monkeyrunner`, но это медленно). Покрывай минимум: стрелка строкой, ластик, переключение цвета.

### Profile 3: Android Emulator API 36 (phone form-factor portrait)

- **AVD:** `Medium_Phone_API_36.1` уже создан, лежит в `~/.android/avd/`.
- **Запуск:** `nohup emulator -avd Medium_Phone_API_36.1 -no-snapshot-save -netdelay none -netspeed full >/tmp/emu.log 2>&1 &`. Ждать `boot_completed=1`.
- **Установка:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:byCompose:android:installDebug`.
- **Гетча: разрешение и скриншоты** — Read tool не принимает картинки > 2000 px по любой стороне. Перед `Read` пропускай через `sips -Z 1800 in.png --out out.png`.
- **Ориентации:** см. Profile 2.

### Profile 4: Android Emulator landscape tablet (опционально)

Если нужен явный тест tablet-landscape без Huawei — сделай AVD `Pixel_Tablet_API_36` через `avdmanager create avd -n Pixel_Tablet_API_36 -k 'system-images;android-36;google_apis;arm64-v8a' -d "pixel_tablet"`. Не критично, если Huawei в обеих ориентациях покрывает планшетный сценарий.

## Тестовый сценарий (на каждом профиле, в каждой ориентации)

1. Запуск приложения, скриншот библиотеки.
2. Открыть Baranovskaya → reading mode → перелистать через tap-zone до страницы ~30 → скриншоты каждой 5-й страницы → визуальная проверка:
   - первые 5 страниц (титул, колофон, оглавление) — не должны быть «таблицей-кашей»;
   - заголовки разделов (`Артикль`, `Существительное`...) — не битые;
   - таблицы упражнений — с границами, корректным заголовком;
   - hyphenation на границах — нет потерянных слов «-это» в начале строки.
3. Открыть Isiguro FB2 → reading mode → 10 страниц → проверить:
   - запуск ≤ 8 секунд (cold), ≤ 1 секунда (warm с layout-cache);
   - заголовки `Часть первая` / `Часть вторая` с правильным интервалом;
   - дашесы диалога (`–`) сохраняются и не превращаются в дефисы;
   - якорь чтения сохраняется при смене пресета (`Компактно` ↔ `Долгое чтение`) и темы.
4. Тёмная тема (Ночь): открыть на странице с заголовком + body + цитатой → измерить контраст (можно скриптом через `sips`/Python `colorsys`).
5. Список книг → крутить вверх-вниз, проверить отсутствие jank.
6. Перелистывание 50 страниц подряд через tap → лог время кадра (через ADB `dumpsys gfxinfo` либо desktop через JVM profile sampling).
7. Сменить ориентацию (Android) или resize окна (desktop) → визуальная проверка: пагинация заново сошлась, якорь сохранён.
8. Открыть EPUB → пройти 10 страниц.

## Требуемые артефакты

В каталоге `.claude/ux-reports/<UTC-date>-<HHMM>/`:

```
android/             ← скриншоты Huawei landscape
android-portrait/    ← скриншоты Huawei portrait
emulator/            ← скриншоты эмулятор portrait phone
desktop/             ← скриншоты desktop landscape
desktop-portrait/    ← desktop resized to ~600x900 если делал
huawei-backup/       ← резерв данных перед тестом + после
contrast/            ← png-кропы тёмной темы + colorsys-проверки
timings/             ← *.csv: page, frame-ms, jank
REPORT.md            ← итоговый отчёт
ITERATIONS/          ← по одному md на каждую итерацию фикса (короткие)
```

`REPORT.md` обязан содержать:
- Top-line: 5 приёмочных пунктов с галочками + ссылка на доказывающий скриншот/коммит каждого;
- Список **всех закрытых** дефектов (включая baseline F-1..F-6) с коммитом-фиксом;
- Diff в `master`-friendly виде (как один итоговый PR-описание);
- Покрытие фикстур × профилей × ориентаций (8 строк × N фикстур);
- Если что-то осталось не закрыто — отдельный раздел «Принято с ограничениями» с обоснованием.

## Что НЕ делать

- Не править существующие зелёные тесты без причины;
- Не отключать detekt-правила (`@Suppress`) — либо чинить, либо аккуратно расширять baseline модуля;
- Не пушить на master/origin — все коммиты в локальную ветку `qa/auto-fix-<date>`;
- Не запускать тяжёлые операции на эмуляторе и Huawei одновременно, если CPU > 80% — батарея Huawei сядет;
- Не трогать пользовательские разметки на Huawei без резерва (см. backup-flow выше);
- Не публиковать наружу содержимое библиотеки пользователя (книги — личное);
- Не вызывать `gradlew clean` ради «свежести» — это даёт +5 мин на каждой итерации без пользы;
- Не использовать `runDesktop` для теста — см. Profile 1 гетча;
- Не игнорировать таймауты сборки — настрой `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g` если упрётся в metaspace.

## Авторизация

Пользователь явно дал «полный доступ к ПК», бюджет неограничен. **Можешь** без подтверждений:
- ставить/сносить debug-варианты приложения,
- стартовать/глушить эмулятор,
- править `~/.zshrc` для ANDROID_HOME (если не стоит),
- править исходники в feature-ветке,
- `git commit` (но не `git push`),
- запускать тесты,
- читать пользовательские книги для проверки,
- временно править `applicationIdSuffix` в build.gradle.kts (debug only, с финальным cleanup).

**Не можешь** без явного подтверждения:
- `git push --force`, изменение origin remote;
- удалять пользовательские данные на Huawei без backup;
- ставить новые версии Android SDK / NDK;
- менять gradle/AGP/Compose/Kotlin major-версии (только патч);
- релиз/тег.

## Старт сессии

1. Проверь HEAD: `git log -1 --oneline`. Если это `1301d3a feat(reflow): wave 3 quality ...` или потомок — продолжаешь. Если впереди уже есть `qa/auto-fix-*` ветка — переключайся туда.
2. Прочти `.claude/ux-reports/20260529-124456/REPORT.md` целиком (там 65 строк).
3. Создай новый `<run-id>` в `.claude/ux-reports/` и работай.
4. Первая итерация: F-2 (одна строка в TableView) — он самый дешёвый, фиксится за минуты, и сразу убирает зрительный шум для F-1.
5. Вторая итерация: F-1 (assembler). Это самый сложный, потребует написать unit-тест на «не превращай мусор в таблицу» в `:reflow:impl:jvmTest`.
6. Потом F-3, F-6, далее — что найдёшь.

Удачи. Готового приёмочного отчёта — не существует пока сам не сделаешь.
