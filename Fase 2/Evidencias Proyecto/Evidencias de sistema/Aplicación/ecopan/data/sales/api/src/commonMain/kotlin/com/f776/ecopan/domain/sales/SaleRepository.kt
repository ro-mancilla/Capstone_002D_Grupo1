package com.f776.ecopan.domain.sales

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface SaleRepository {
    /**
     * Atomic: sale + lines + an outbox row for sync commit as one local transaction (03 §"one
     * Room transaction" rule). POS never blocks on network here. Returns the till's sequential
     * sale number assigned in that same transaction (1, 2, 3 ... per local database), which the
     * receipt prints as "Venta N.º 0001" instead of the UUID.
     */
    suspend fun recordSale(sale: Sale): Int

    /** Today's sales for [branchId], local-first — drives the till view / running total. */
    fun observeTodaySales(branchId: Uuid): Flow<List<Sale>>
}
