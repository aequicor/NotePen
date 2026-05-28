package ru.kyamshanov.notepen.appsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.SettingsComponent
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository

/**
 * Decompose-компонент экрана глобальных настроек приложения. Чтение/запись
 * идут через [AppSettingsRepository]; UI наблюдает [settings] через
 * `collectAsState`.
 */
class SettingsComponentImpl(
    componentContext: ComponentContext,
    private val repository: AppSettingsRepository,
    private val onBackListener: () -> Unit,
) : SettingsComponent,
    ComponentContext by componentContext {
    private val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)

    val settings: StateFlow<AppSettings> = repository.settings

    fun setAlwaysOnDisplay(enabled: Boolean) {
        scope.launch { repository.save(settings.value.copy(alwaysOnDisplay = enabled)) }
    }

    override fun onBack() {
        onBackListener()
    }
}
