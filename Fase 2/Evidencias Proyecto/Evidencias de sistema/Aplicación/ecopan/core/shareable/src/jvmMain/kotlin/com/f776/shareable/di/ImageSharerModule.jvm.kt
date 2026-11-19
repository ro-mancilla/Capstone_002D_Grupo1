package com.f776.shareable.di

import com.f776.shareable.ImageSharerFactory
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val PlatformModule: Module = module {
    factory {
        ImageSharerFactory(ioDispatcher = get<CoroutineDispatcher>(named("ioDispatcher"))).getImageSharer()
    }
}