package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.domain.catalog.Product

/**
 * Which warning an Almacén tile carries, from the stock the till last heard about (net of its own
 * unsynced sales, see [Product.stockUnits]).
 *
 * - negative: stock below zero (a stale count, or stock sold before it was entered)
 * - out: exactly zero
 * - low: above zero but at or under the reorder level
 * - none: in stock, no reorder level set, no figure from the server yet, or not an Almacén product
 *
 * Purely informational. Nothing here is consulted when adding a line or charging a sale.
 */
internal fun Product.stockBadge(stale: Boolean): StockBadgeUi? {
    if (origin != ProductOrigin.ALMACEN) return null
    val stock = stockUnits ?: return null
    val threshold = lowStockThresholdUnits
    val level = when {
        stock < 0 -> StockLevel.NEGATIVE
        stock == 0L -> StockLevel.OUT
        threshold != null && stock <= threshold -> StockLevel.LOW
        else -> return null
    }
    return StockBadgeUi(level, stock, stale)
}
