package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import ru.kyamshanov.notepen.appsettings.domain.model.ScreenOrientationMode

/**
 * Применяет [ScreenOrientationMode] к текущему экрану, пока этот эффект в
 * композиции. Привязывает ориентацию контента к настройке приложения, а не к
 * физическому повороту устройства — чтобы можно было читать/писать лёжа, не
 * переворачивая интерфейс.
 *
 * На Android выставляет `Activity.requestedOrientation` и возвращает прежнее
 * значение при выходе из композиции (выход из редактора/ридера снимает лок). На
 * десктопе — no-op (понятия ориентации окна нет).
 */
@Composable
expect fun ApplyScreenOrientation(mode: ScreenOrientationMode)
