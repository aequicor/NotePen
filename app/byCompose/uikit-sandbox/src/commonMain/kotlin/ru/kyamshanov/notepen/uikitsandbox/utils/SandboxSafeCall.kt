package ru.kyamshanov.notepen.uikitsandbox.utils

import kotlinx.coroutines.CancellationException
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFailure

/**
 * Выполняет suspend-блок и переводит recoverable-ошибки в [SandboxOutcome].
 *
 * CancellationException пробрасывается наружу, чтобы не ломать кооперативную отмену
 * корутин MVIKotlin executor-а.
 *
 * @param failure ошибка, которую нужно вернуть при не-cancellation исключении.
 * @param block выполняемый suspend-блок.
 * @return [SandboxOutcome.Success] с результатом блока или [SandboxOutcome.Failure].
 */
internal suspend inline fun <T> sandboxSafeCall(
    failure: SandboxWidgetFailure,
    crossinline block: suspend () -> T,
): SandboxOutcome<T> =
    try {
        SandboxOutcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        SandboxOutcome.Failure(failure)
    }
