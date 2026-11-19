package com.f776.unigrid.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A recorded sale (05 §2). Append-only: no update/delete DAO methods exist for this table — a
 * crash between this row, its [SaleLineEntity] rows, and the paired outbox row must be impossible,
 * so all three are written by [com.f776.unigrid.core.database.dao.SaleDao.recordSale] in one
 * `@Transaction`.
 */
@Entity(tableName = "sales", indices = [Index(value = ["branchId"]), Index(value = ["timestamp"])])
data class SaleEntity(
    @PrimaryKey val id: Uuid,
    val branchId: Uuid,
    val cashierId: Uuid,
    val timestamp: Instant,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
    val cashTenderedMinor: Long?,
    val changeMinor: Long?,
    /**
     * The till's own sequential sale number ("Venta N.º 0001") -- what a customer reads on the
     * receipt instead of the UUID. Assigned inside [com.f776.unigrid.core.database.dao.SaleDao.recordSale]'s
     * transaction as `MAX + 1`, so it is gapless per local database (per till) and never reused.
     * Local only: it is not part of the sync payload, because the server has the UUID.
     */
    @ColumnInfo(defaultValue = "0") val receiptNumber: Int = 0,
)
