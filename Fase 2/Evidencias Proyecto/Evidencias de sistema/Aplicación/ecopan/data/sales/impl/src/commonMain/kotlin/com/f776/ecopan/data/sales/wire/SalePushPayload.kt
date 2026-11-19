package com.f776.ecopan.data.sales.wire

import kotlinx.serialization.Serializable

/**
 * The `payload` object for a `SALE` mutation in the sync push envelope (03 §3.2, reconciled with
 * 02's `sales`/`sale_lines` tables). Deliberately excludes two fields that exist on the server
 * table but never travel in this body: `tenantId` (comes from the JWT principal — 03 §3.2 says so
 * explicitly) and `deviceId` (a top-level field of the push *envelope* that the future sync
 * engine attaches once per batch, not per-mutation).
 *
 * [soldAt] is **epoch milliseconds**, matching the backend's `SalePayload.soldAt: Long`. It was an
 * ISO-8601 string, which the server could not parse, so every sale was rejected and marked FAILED
 * without ever reaching the database. The pull side already used epoch millis, so the two
 * directions disagreed with each other.
 */
@Serializable
internal data class SalePushPayload(
    val id: String,
    val branchId: String,
    val cashierId: String,
    val soldAt: Long,
    val paymentMethod: String,
    val totalMinor: Long,
    val cashTenderedMinor: Long?,
    val changeMinor: Long?,
    val lines: List<SaleLinePushPayload>,
)

@Serializable
internal data class SaleLinePushPayload(
    val id: String,
    val productId: String,
    val soldBy: String,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
)
