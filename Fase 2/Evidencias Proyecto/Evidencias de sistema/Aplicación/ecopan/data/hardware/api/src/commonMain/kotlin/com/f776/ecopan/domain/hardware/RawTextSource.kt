package com.f776.ecopan.domain.hardware

import kotlinx.coroutines.flow.StateFlow

/**
 * Optional capability of a [Scale] that talks text (discovered with `scale as? RawTextSource`, like
 * [ManualWeightEntry]): the last line it received, verbatim, so the live test can show a person
 * what their scale actually sends ("ST,GS, 0.842 kg"). `null` until something arrives.
 */
interface RawTextSource {
    val lastRawText: StateFlow<String?>
}
