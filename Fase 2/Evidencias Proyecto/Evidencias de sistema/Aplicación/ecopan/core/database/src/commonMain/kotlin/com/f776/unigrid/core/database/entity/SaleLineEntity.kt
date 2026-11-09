package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.f776.ecopan.core.domain.catalog.SoldBy
import kotlin.uuid.Uuid

/**
 * One line of a [SaleEntity], snapshotted at add-time (05 §2) — [productTypeSnapshot], [soldBy]
 * and [unitPriceMinorSnapshot] never change even if the catalog does.
 * Exactly one of [weightGrams]/[quantity] is set, matching [soldBy].
 */
@Entity(
    tableName = "sale_lines",
    indices = [Index(value = ["saleId"])],
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SaleLineEntity(
    @PrimaryKey val id: Uuid,
    val saleId: Uuid,
    val productId: Uuid,
    val productTypeSnapshot: String,
    val soldBy: SoldBy,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinorSnapshot: Long,
    val lineTotalMinor: Long,
)
