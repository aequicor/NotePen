package ru.kyamshanov.notepen.uikitsandbox.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Набор dispatcher-ов sandbox-фичи.
 */
internal interface SandboxDispatchers {
    /**
     * Dispatcher для IO-like работы data-слоя.
     */
    val io: CoroutineDispatcher
}

/**
 * Dispatcher-ы по умолчанию для standalone sandbox.
 */
internal object DefaultSandboxDispatchers : SandboxDispatchers {
    /**
     * Default используется вместо Main/IO, чтобы common-код оставался KMP-совместимым.
     */
    override val io: CoroutineDispatcher = Dispatchers.Default
}

/**
 * Adapter dispatcher-а, переданного через публичные dependencies.
 *
 * @property io dispatcher для data-операций.
 */
internal class DependencySandboxDispatchers(
    override val io: CoroutineDispatcher,
) : SandboxDispatchers
