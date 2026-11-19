package com.f776.ecopan.di

import com.f776.ecopan.feature.production.di.ProductionModule
import org.koin.dsl.module

/**
 * The baker client's own graph, included by both mobile `PlatformModule` actuals.
 *
 * Not in `AppModule`: `:mobile:feature:production:impl` has no JVM target, so the till could not
 * resolve these definitions even if it wanted to.
 */
internal val MobileModule = module {
    includes(ProductionModule)
}
