package com.f776.ecopan.api.feature.tenant.di

import com.f776.ecopan.api.feature.tenant.domain.TenantService
import com.f776.ecopan.api.feature.tenant.domain.TenantServiceImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val TenantModule = module {
    singleOf(::TenantServiceImpl).bind<TenantService>()
}
