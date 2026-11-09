package com.f776.ecopan.feature.auth

/** PIN unlock needs bcrypt (core:crypto/PinVerifier); iOS has no implementation yet (01 §4). */
internal expect val PinUnlockSupported: Boolean
