package com.f776.ecopan.notifier.domain

import com.f776.ecopan.data.messagebroker.model.BatchProcessed

interface NotificationProcessor {
    suspend fun handle(event: BatchProcessed)
}
