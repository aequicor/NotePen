package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.Composable

/**
 * `true`, если на платформе включён системный режим «уменьшить движение»
 * (accessibility «Удалить анимации»). Reflow-ридер в этом случае форсит
 * [ru.kyamshanov.notepen.reflow.api.PageTransition.NONE] независимо от выбранного
 * стиля перехода. Платформенно (`expect/actual`), без новых зависимостей — как
 * [currentLocalHour].
 *
 * На десктопе (JVM) единого системного сигнала reduced motion нет — всегда `false`;
 * там пользователь выбирает «Нет» вручную.
 */
@Composable
internal expect fun isReducedMotionEnabled(): Boolean
