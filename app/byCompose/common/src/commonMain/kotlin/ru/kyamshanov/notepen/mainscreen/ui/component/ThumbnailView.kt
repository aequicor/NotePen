package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.mainscreen.platform.rememberPdfThumbnailPainter
import ru.kyamshanov.notepen.mainscreen.ui.model.ThumbnailState

/**
 * Отображает миниатюру PDF-файла в одном из трёх состояний:
 * загрузка (shimmer-плейсхолдер), готово (изображение), ошибка (иконка предупреждения).
 *
 * @param state Текущее состояние миниатюры.
 * @param modifier Модификатор компонента.
 */
@Composable
fun ThumbnailView(
    state: ThumbnailState,
    modifier: Modifier = Modifier,
) {
    val bgColor =
        when (state) {
            is ThumbnailState.Error -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .aspectRatio(0.707f)
                .clip(MaterialTheme.shapes.medium)
                .background(bgColor),
    ) {
        when (state) {
            is ThumbnailState.Loading -> Unit
            is ThumbnailState.Ready -> {
                val painter = rememberPdfThumbnailPainter(state.imageData)
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is ThumbnailState.Error -> {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
