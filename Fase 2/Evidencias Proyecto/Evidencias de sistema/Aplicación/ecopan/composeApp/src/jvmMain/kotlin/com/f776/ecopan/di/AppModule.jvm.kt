package com.f776.ecopan.di

import com.f776.ecopan.feature.pos.di.PosModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The till's own graph. `PosModule` lives here rather than in `AppModule` because the POS feature
 * is desktop-only (SPECS.md Client 1) and `:jvm:feature:pos:impl` is not on the mobile compile
 * classpath — see `navigation/TopLevelDestinations.kt`.
 */
actual val PlatformModule: Module = module {
    includes(PosModule)
}