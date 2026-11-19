package com.f776.ecopan.api.feature.auth.di

import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.feature.auth.domain.AuthService
import com.f776.ecopan.api.feature.auth.domain.AuthServiceImpl
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.RefreshTokenRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import org.koin.dsl.module

val AuthModule = module {
    single<AuthService> {
        val settings = get<Settings>()
        AuthServiceImpl(
            jwtSettings = settings.jwtSettings,
            deviceSessionTtlDays = settings.deviceSessionTtlDays,
            tenantRepository = get<TenantRepository>(),
            userRepository = get<UserRepository>(),
            branchRepository = get<BranchRepository>(),
            refreshTokenRepository = get<RefreshTokenRepository>()
        )
    }
}
