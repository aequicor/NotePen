package ru.kyamshanov.notepen.uikitsandbox.presentation.utils

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Преобразует [StateFlow] в Decompose [Value].
 *
 * Adapter нужен на границе MVIKotlin Store и Decompose component API.
 *
 * @param scope scope жизненного цикла компонента, в котором собирается flow.
 * @param mapper преобразователь state-flow значения в публичную модель.
 * @return [Value], синхронизированный с исходным [StateFlow].
 */
internal fun <T : Any, R : Any> StateFlow<T>.asValue(
    scope: CoroutineScope,
    mapper: (T) -> R,
): Value<R> {
    val value = MutableValue(mapper(this.value))
    scope.launch {
        collect { state -> value.value = mapper(state) }
    }
    return value
}
