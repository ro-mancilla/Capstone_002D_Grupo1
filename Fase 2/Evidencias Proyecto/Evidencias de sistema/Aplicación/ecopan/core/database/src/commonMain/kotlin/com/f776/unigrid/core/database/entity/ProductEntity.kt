package com.f776.unigrid.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Client-side cache of a tenant's catalog (02 §1 `products`). Exactly one of
 * [pricePerKgMinor]/[priceMinor] is set, matching [soldBy] — enforced server-side; the client
 * cache trusts the pulled row rather than re-validating it. [mixToLoafRatioMilli]/[minMixGrams]/
 * [maxMixGrams] are baker-mobile formula-derivation config (06 §1, §9) — null means "no estimate"
 * / "use the tenant-default soft bounds", not zero.
 */
@Entity(tableName = "products", indices = [Index(value = ["tenantId"]), Index(value = ["barcode"])])
data class ProductEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val productType: String,
    val soldBy: SoldBy,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    /** Absolute URL of a product photo, or null. A picture is not ledger data: a tile
     * without its photo is still a tile, so this never blocks an offline sale. */
    val imageUrl: String?,
    val active: Boolean,
    val serverSeq: Long,
    val cachedAt: Instant,
    val mixToLoafRatioMilli: Int?,
    val minMixGrams: Long?,
    val maxMixGrams: Long?,
    /** Panadería (baked, has a formula and batches) or Almacén (bought finished). The column
     * default is what an auto-migrated pre-origin row gets: every legacy product reads as Panadería
     * until its next pull overwrites it with the server's classification. */
    @ColumnInfo(defaultValue = "PANADERIA") val origin: ProductOrigin = ProductOrigin.PANADERIA,
    /** Almacén only: the reorder level (units). Null = none set, and always null for Panadería. */
    val lowStockThresholdUnits: Long? = null,
    /** Almacén only: this branch's stock as of the last pull that carried the product (negative
     * allowed). Null = the server never told this device (never synced, or an all-branch login), which
     * the till renders as no badge. Sales still waiting to sync are *not* subtracted here; the
     * repository does that on read so a re-pull cannot double-count them. */
    val stockUnits: Long? = null,
)
