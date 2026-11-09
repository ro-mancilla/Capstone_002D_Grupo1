package com.f776.ecopan.feature.shell.di

import com.f776.ecopan.feature.shell.ShellContext
import com.f776.ecopan.feature.shell.ShellContextImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ShellModule = module {
    singleOf(::ShellContextImpl).bind<ShellContext>()
}
