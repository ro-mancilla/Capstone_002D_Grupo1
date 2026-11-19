package com.f776.ecopan.data.example.di

import com.f776.ecopan.data.example.NoteRepositoryImpl
import com.f776.ecopan.domain.example.NoteRepository
import com.f776.unigrid.core.database.di.DatabaseModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val ExampleDataModule = module {
    includes(DatabaseModule)

    factoryOf(::NoteRepositoryImpl).bind<NoteRepository>()
}
