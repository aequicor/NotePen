package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Суффикс файла-сайдкара аннотаций NotePen, который пишется рядом с документом
 * (`"$documentPath.notepen.json"`). Это внутренний артефакт, а не документ.
 */
const val ANNOTATION_SIDECAR_SUFFIX: String = ".notepen.json"

/**
 * `true`, если [uri] указывает на внутренний сайдкар аннотаций NotePen.
 *
 * Такие файлы не являются документами и не должны попадать в историю недавних
 * файлов: миниатюра для них не генерируется (это JSON, а не PDF/изображение).
 */
fun isAnnotationSidecarUri(uri: String): Boolean =
    uri.endsWith(ANNOTATION_SIDECAR_SUFFIX, ignoreCase = true)
