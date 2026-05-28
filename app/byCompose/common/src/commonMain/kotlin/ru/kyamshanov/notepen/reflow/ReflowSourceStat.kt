package ru.kyamshanov.notepen.reflow

/**
 * Stat исходного PDF (или URI), нужный для валидации дискового кэша:
 * если размер или mtime изменились с момента записи кэша, кэш считается
 * устаревшим и переэкстракт обязателен.
 *
 * Возвращает `null`, если stat недоступен (например, файла нет либо SAF-провайдер
 * не отдаёт OpenableColumns.SIZE) — в этом случае кэш-слой пропускает чтение/запись.
 */
internal data class SourceStat(
    val size: Long,
    val mtime: Long,
)

internal expect fun statReflowSource(path: String): SourceStat?

internal expect fun reflowCacheDir(): String
