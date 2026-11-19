package com.f776.ecopan.core.crypto.di

import com.f776.ecopan.core.crypto.PinVerifier
import com.f776.ecopan.core.crypto.PinVerifierImpl
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val CryptoModule = module {
    factoryOf(::PinVerifierImpl).bind<PinVerifier>()
}
