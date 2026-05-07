---
genre: how-to
title: "Stage 04: UI components + MainContent (common)"
topic: main-screen
module: common
stage: 04
feature: main_screen_redesign
---

# Stage 04: UI-компоненты и MainContent (`:common`)

**Модуль:** `:app:byCompose:common`  
**Sourceset:** `commonMain`  
**Статус:** TODO  
**Зависит от:** Stage 03

---

## Цель

Реализовать все Compose-компоненты главного экрана. Использовать исключительно Material 3 (импорт из `androidx.compose.material3.*`). Не смешивать с Material 2.

**ACT-NOW R5:** во всех файлах этого этапа — только `import androidx.compose.material3.*`.

---

## Пакетная структура

```
common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/
  component/
    RecentFileCard.kt
    FolderCard.kt
    EmptyState.kt
    ThumbnailView.kt
    StatusBadge.kt
  screen/
    MainContent.kt
    MainScreenComponent.kt    ← Decompose-компонент, хостирует ViewModel
  dialog/
    CreateFolderDialog.kt
    DeleteFolderDialog.kt
    SafMergeDialog.kt
```

---

## Шаг 1: Цветовые токены

В `MainContent.kt` определить локальные константы (Material 3 ColorScheme задаётся в `:theme`):

```kotlin
// Используется ComposableAppTheme из :theme — все цвета берём из MaterialTheme.colorScheme
```

---

## Шаг 2: `ThumbnailView`

```kotlin
@Composable
fun ThumbnailView(
    state: ThumbnailState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.aspectRatio(0.707f).clip(MaterialTheme.shapes.medium)) {
        when (state) {
            is ThumbnailState.Loading -> {
                // Shimmer-плейсхолдер (AC-8): серый прямоугольник с анимацией пульсации
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            is ThumbnailState.Ready -> {
                // Миниатюра из ByteArray через BitmapPainter (expect/actual в Stage 05/06)
                Image(
                    painter = rememberPdfThumbnailPainter(state.imageData),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is ThumbnailState.Error -> {
                // Иконка ошибки (AC-9d)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer)) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
            }
        }
    }
}
```

> `rememberPdfThumbnailPainter` — expect/actual: Android → `BitmapFactory.decodeByteArray`, Desktop → `ImageIO.read`.

---

## Шаг 3: `StatusBadge`

7 визуальных состояний по дизайну @Designer:

```kotlin
@Composable
fun StatusBadge(status: AvailabilityStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        AvailabilityStatus.AVAILABLE -> return   // нет бейджа для доступных
        AvailabilityStatus.UNKNOWN -> return      // нет бейджа в UNKNOWN
        AvailabilityStatus.NOT_FOUND -> "Не найден" to MaterialTheme.colorScheme.error
        AvailabilityStatus.FILE_ERROR -> "Ошибка" to MaterialTheme.colorScheme.error
        AvailabilityStatus.ARCHIVED_UNAVAILABLE -> "Архив / Недоступен" to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
```

---

## Шаг 4: `RecentFileCard`

```kotlin
@Composable
fun RecentFileCard(
    model: RecentFileUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            ThumbnailView(
                state = model.thumbnailState,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(status = model.availabilityStatus, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
```

---

## Шаг 5: `FolderCard`

```kotlin
@Composable
fun FolderCard(
    model: FolderUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${model.fileCount} файлов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.MoreVert, contentDescription = "Меню папки")
            }
        }
    }
}
```

---

## Шаг 6: `EmptyState`

```kotlin
@Composable
fun EmptyState(onOpenFile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Нет недавних файлов",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Откройте PDF-файл, чтобы начать работу",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(24.dp))
        // OQ-6: CTA-кнопка в пустом состоянии (AC-1)
        OutlinedButton(onClick = onOpenFile) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Открыть файл")
        }
    }
}
```

---

## Шаг 7: `MainContent`

Адаптивная вёрстка: `< 600 dp` — LazyColumn (1 col), `≥ 600 dp` — LazyVerticalGrid.

```kotlin
@Composable
fun MainContent(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val isWide = with(LocalDensity.current) { windowWidth.toDp() >= 600.dp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotePen") },
                actions = {
                    // Desktop: кнопка «Открыть» в топбаре (AC-12)
                    if (isWide) {
                        TextButton(onClick = { onIntent(MainScreenIntent.OpenFilePicker) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Открыть")
                        }
                    }
                    IconButton(onClick = { onIntent(MainScreenIntent.CreateFolder.let { MainScreenIntent.DismissCreateFolderDialog.let {
                        // вызов openCreateFolderDialog через ViewModel
                        MainScreenIntent.OpenFilePicker // placeholder — заменить в @CodeWriter
                    } } }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Новая папка")
                    }
                },
            )
        },
        floatingActionButton = {
            // Android: FAB «Открыть файл» (AC-12)
            if (!isWide) {
                FloatingActionButton(onClick = { onIntent(MainScreenIntent.OpenFilePicker) }) {
                    Icon(Icons.Default.Add, contentDescription = "Открыть файл")
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.recentFiles.isEmpty() && state.folders.isEmpty() ->
                    EmptyState(
                        onOpenFile = { onIntent(MainScreenIntent.OpenFilePicker) },
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> RecentFilesAndFoldersList(state, onIntent, isWide)
            }
        }
    }

    // Диалоги
    state.createFolderDialog?.let { dialog ->
        CreateFolderDialog(
            state = dialog,
            onNameChange = { onIntent(MainScreenIntent.FolderDialogNameChanged(it)) },
            onConfirm = { onIntent(MainScreenIntent.CreateFolder(dialog.currentName)) },
            onDismiss = { onIntent(MainScreenIntent.DismissCreateFolderDialog) },
        )
    }
    state.deleteFolderDialog?.let { dialog ->
        DeleteFolderDialog(
            folderName = dialog.folderName,
            onConfirm = { onIntent(MainScreenIntent.DeleteFolder(dialog.folderId)) },
            onDismiss = { onIntent(MainScreenIntent.DismissDeleteFolderDialog) },
        )
    }
    state.safMergeDialog?.let { dialog ->
        SafMergeDialog(
            existing = dialog.existingRecord,
            newUri = dialog.newUri,
            onMerge = { onIntent(MainScreenIntent.MergeSafRecords(dialog.existingRecord.id, "", dialog.newUri)) },
            onReject = { onIntent(MainScreenIntent.RejectSafMerge(dialog.existingRecord.id, dialog.newUri)) },
        )
    }

    // Snackbar/toast для errorEvent
    state.errorEvent?.let { event ->
        // Реализация через SnackbarHostState в Scaffold — детали в @CodeWriter
    }
}

@Composable
private fun RecentFilesAndFoldersList(
    state: MainScreenUiState,
    onIntent: (MainScreenIntent) -> Unit,
    isWide: Boolean,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        // Секция: Недавние файлы
        item { SectionHeader("Недавние файлы") }

        if (isWide) {
            // Adaptive grid внутри LazyColumn через subcompose
            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.recentFiles, key = { it.id }) { file ->
                        RecentFileCard(
                            model = file,
                            onClick = { onIntent(MainScreenIntent.OpenRecentFile(file.id)) },
                        )
                    }
                }
            }
        } else {
            items(state.recentFiles, key = { it.id }) { file ->
                RecentFileCard(
                    model = file,
                    onClick = { onIntent(MainScreenIntent.OpenRecentFile(file.id)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }

        // Секция: Папки (AC-40: скрыта если нет папок)
        if (state.folders.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)) }
            item { SectionHeader("Папки") }
            items(state.folders, key = { "folder_${it.id}" }) { folder ->
                FolderCard(
                    model = folder,
                    onClick = { /* открытие папки — будущая фича */ },
                    onDelete = { onIntent(MainScreenIntent.RequestDeleteFolder(folder.id)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
```

---

## Шаг 8: `MainScreenComponent` (Decompose)

```kotlin
class MainScreenComponent(
    componentContext: ComponentContext,
    private val historyRepository: FileHistoryRepository,
    private val folderRepository: FolderRepository,
    private val addToHistory: AddToHistoryUseCase,
    private val checkAvailability: CheckAvailabilityUseCase,
    private val openRecentFileUseCase: OpenRecentFileUseCase,
    private val thumbnailRepository: ThumbnailRepository,
    private val thumbnailGenerator: PdfThumbnailGenerator,
    val onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    val onOpenFilePicker: () -> Unit,
) : ComponentContext by componentContext {

    val viewModel = MainScreenViewModel(
        lifecycle = lifecycle,
        historyRepository = historyRepository,
        folderRepository = folderRepository,
        addToHistory = addToHistory,
        checkAvailability = checkAvailability,
        openRecentFile = openRecentFileUseCase,
        thumbnailRepository = thumbnailRepository,
        thumbnailGenerator = thumbnailGenerator,
    )
}
```

---

## Тесты Stage 04

Ввиду отсутствия Compose Preview Tests на всех платформах — тесты UI в данном этапе ограничены:

| Тест | Тип |
|------|-----|
| `EmptyState` рендерится без краша в commonTest (snapshot тест если доступен) | Визуальный |
| Адаптивность: при ширине < 600dp показывается LazyColumn, при ≥ 600dp — Grid | Интеграционный |

Основные функциональные проверки UI покрыты тестами ViewModel (Stage 03).

---

## Acceptance Criteria, закрываемые этим этапом

AC-1 (EmptyState), AC-7 (превью), AC-8 (Loading state), AC-9c/d (статус-иконки), AC-12 (кнопка открытия), AC-15 (адаптивная сетка), AC-16 (адаптивная колонка), AC-24 (секция папок), AC-39 (диалог удаления), AC-40 (папки скрыты если пусто).

---

## Контрольные точки Stage 04

- [ ] `./gradlew :app:byCompose:common:compileKotlin` — зелёный
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
- [ ] **ACT-NOW R5:** grep по изменённым файлам на `import androidx.compose.material.` — пустой результат
