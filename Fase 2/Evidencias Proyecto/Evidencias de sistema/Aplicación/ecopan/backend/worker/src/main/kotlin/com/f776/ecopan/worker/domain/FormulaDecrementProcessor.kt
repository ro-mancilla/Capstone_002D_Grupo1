package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage

interface FormulaDecrementProcessor {
    suspend fun processMessage(message: ProductionBatchMessage)
}
