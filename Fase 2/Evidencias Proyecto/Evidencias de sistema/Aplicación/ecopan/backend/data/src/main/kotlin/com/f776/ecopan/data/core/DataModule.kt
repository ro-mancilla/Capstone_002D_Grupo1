package com.f776.ecopan.data.core

import com.f776.ecopan.data.database.di.DatabaseModule
import com.f776.ecopan.data.messagebroker.di.MessageBrokerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val DataModule = module {
    single(named("defaultScope")) { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
    includes(DatabaseModule, MessageBrokerModule)
}