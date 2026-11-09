package com.f776.ecopan.domain.tenant

import kotlin.uuid.Uuid

data class Branch(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val active: Boolean,
)
