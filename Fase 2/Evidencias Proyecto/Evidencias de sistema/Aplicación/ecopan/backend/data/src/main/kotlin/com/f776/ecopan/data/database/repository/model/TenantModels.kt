package com.f776.ecopan.data.database.repository.model

import com.f776.ecopan.data.domain.Module
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class Tenant(
    val id: Uuid,
    val slug: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: Set<Module>,
    val createdAt: Instant
)

data class Branch(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val active: Boolean,
    val serverSeq: Long,
    val createdAt: Instant
)
