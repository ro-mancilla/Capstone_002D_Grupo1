package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Client-side cache of the single tenant this device is enrolled to (02 §1 `tenants`, fetched via
 * `GET /tenant`). One row per install — refreshed wholesale on every successful fetch/pull.
 */
@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey val id: Uuid,
    val slug: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: Set<EntitlementModule>,
    val cachedAt: Instant,
)
