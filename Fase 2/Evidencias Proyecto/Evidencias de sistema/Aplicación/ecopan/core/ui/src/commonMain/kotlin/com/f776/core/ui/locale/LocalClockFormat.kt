package com.f776.core.ui.locale

import androidx.compose.runtime.staticCompositionLocalOf
import com.f776.locale.ClockFormat
import com.f776.locale.LocaleClockFormat

/**
 * The [ClockFormat] in force for the composition.
 *
 * Unlike [com.f776.core.ui.appstate.LocalSnackbarAppState] this has a working default rather than
 * erroring: times are rendered by leaf composables all over the app, and requiring the DI graph to
 * resolve one would break every `@Preview` and every screen test that renders a card. The default
 * is the locale's own convention, which is right everywhere except Android with the system-wide
 * 24-hour override on -- and the app root provides the injected instance that knows about it.
 */
val LocalClockFormat = staticCompositionLocalOf<ClockFormat> { LocaleClockFormat() }
