package ru.kyamshanov.notepen.uikitsandbox.di

import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.kyamshanov.notepen.uikitsandbox.data.SandboxWidgetRepositoryImpl
import ru.kyamshanov.notepen.uikitsandbox.data.local.InMemorySandboxWidgetLocalDataSource
import ru.kyamshanov.notepen.uikitsandbox.data.remote.SandboxWidgetRemoteDataSource
import ru.kyamshanov.notepen.uikitsandbox.data.remote.StaticSandboxWidgetRemoteDataSource
import ru.kyamshanov.notepen.uikitsandbox.presentation.component.UikitSandboxComponentFactory
import ru.kyamshanov.notepen.uikitsandbox.utils.DefaultSandboxDispatchers
import ru.kyamshanov.notepen.uikitsandbox.utils.SandboxDispatchers

/**
 * Internal Koin-модуль, демонстрирующий wiring sandbox graph-а по новой архитектуре.
 *
 * Модуль не является публичной точкой входа. Он показывает, как можно собрать
 * [UikitSandboxComponentFactory] через DI, не смешивая DI с Compose rendering.
 */
internal val uikitSandboxModule: Module =
    module {
        single<StoreFactory> { DefaultStoreFactory() }
        single<SandboxDispatchers> { DefaultSandboxDispatchers }
        single<SandboxWidgetRemoteDataSource> { StaticSandboxWidgetRemoteDataSource() }

        factory {
            val remoteDataSource = get<SandboxWidgetRemoteDataSource>()
            val dispatchers = get<SandboxDispatchers>()
            UikitSandboxComponentFactory(
                storeFactory = get(),
                repositoryFactory = {
                    SandboxWidgetRepositoryImpl(
                        localDataSource = InMemorySandboxWidgetLocalDataSource(),
                        remoteDataSource = remoteDataSource,
                        dispatchers = dispatchers,
                    )
                },
            )
        }
    }
