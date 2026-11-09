package com.f776.ecopan.core.crypto

/**
 * **Scoped out of v1 (DECIDED, 01 §4):** PIN unlock ships on Android + JVM only. jvmAndAndroidMain
 * uses `at.favre.lib:bcrypt` (pure JVM, works unmodified on Android), which is not usable from
 * Kotlin/Native — a real iOS implementation needs bcrypt via cinterop against vendored C source, a
 * pattern with no precedent elsewhere in this codebase and not something verifiable without a real
 * iOS simulator/device (this environment has neither). Rather than ship unverified cinterop crypto
 * code, iOS is deliberately left unimplemented and `:feature:auth`'s lock screen must gate PIN
 * unlock off on iOS (fall back to online-login-per-shift, or hide the feature) until this is
 * revisited with real device testing available.
 *
 * Left throwing rather than silently faking a result — a stub that always returns `true`/`false`
 * would be far worse than an obvious crash.
 */
internal actual class PinVerifierImpl actual constructor() : PinVerifier {
    actual override fun verify(pin: String, hash: String): Boolean {
        error(
            "PIN unlock is not implemented on iOS in v1 (scoped out — see this file's KDoc and " +
                "01 §4). :feature:auth must not present the PIN lock screen on this platform.",
        )
    }

    actual override fun hash(pin: String): String {
        error(
            "PIN hashing is not implemented on iOS in v1 (scoped out — see this file's KDoc and " +
                "01 §4). :feature:auth must not present PIN enrollment on this platform.",
        )
    }
}
