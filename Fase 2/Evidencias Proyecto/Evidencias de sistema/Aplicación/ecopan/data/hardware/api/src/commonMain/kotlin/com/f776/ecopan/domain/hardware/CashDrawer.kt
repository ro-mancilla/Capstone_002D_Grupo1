package com.f776.ecopan.domain.hardware

import kotlinx.coroutines.flow.StateFlow

interface CashDrawer {
    suspend fun open()
    val status: StateFlow<DeviceStatus>
}
