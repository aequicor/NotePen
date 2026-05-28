package ru.kyamshanov.notepen.mainscreen.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Описывает книгу, физически лежащую в общей библиотечной папке хоста.
 *
 * Эти элементы видны другим устройствам через [LibraryManifestProvider] —
 * раздел отдельный от обычных папок и от истории недавних файлов.
 *
 * @property id Стабильный идентификатор элемента (например, относительный путь
 *   внутри библиотечного корня). Используется для click/open.
 * @property uri Абсолютный URI файла, по которому его можно открыть в редакторе.
 * @property displayName Человекочитаемое имя (имя файла).
 * @property sizeBytes Размер файла в байтах, либо `null` если неизвестен.
 * @property modifiedAt Время последней модификации файла (epoch millis).
 */
data class LibraryFolderItem(
    val id: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val modifiedAt: Long,
)

/**
 * Порт для секции «Библиотека» на главном экране — общей папки, содержимое
 * которой автоматически расшаривается подключённым пирам через `LibraryManifestProvider`.
 *
 * Реализация на Desktop работает поверх той же директории, что и
 * `FileSystemLibraryManifestProvider` (по умолчанию `~/NotePen Library`).
 * На платформах, где общая библиотека не поддерживается, порт может быть `null` —
 * UI в этом случае секцию скрывает.
 */
interface LibraryFolder {
    /** Реактивный список книг, лежащих сейчас в библиотеке. */
    val items: StateFlow<List<LibraryFolderItem>>

    /**
     * Копирует файл по [sourceUri] в библиотечную папку.
     *
     * Имя при коллизии разрешается через суффикс `" (2)"`, `" (3)"` и т. д.
     * После успешного копирования [items] обновится автоматически, и пиры
     * получат уведомление об изменении каталога.
     *
     * @return [Result.success] с новым [LibraryFolderItem] или [Result.failure],
     *   если расширение не поддерживается, файл-источник недоступен либо
     *   произошла I/O-ошибка.
     */
    suspend fun addCopy(sourceUri: String): Result<LibraryFolderItem>

    /** Принудительно пересканировать библиотечный каталог (drop-in для тестов и явного refresh). */
    suspend fun refresh()
}
