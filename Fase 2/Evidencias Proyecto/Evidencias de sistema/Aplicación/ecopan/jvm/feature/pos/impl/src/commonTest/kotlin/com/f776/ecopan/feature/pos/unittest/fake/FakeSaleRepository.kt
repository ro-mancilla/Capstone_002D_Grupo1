package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeSaleRepository : SaleRepository {
    val recordedSales = mutableListOf<Sale>()
    private val todaySales = MutableStateFlow<List<Sale>>(emptyList())

    override suspend fun recordSale(sale: Sale): Int {
        val number = recordedSales.size + 1
        recordedSales += sale.copy(receiptNumber = number)
        todaySales.value = todaySales.value + sale.copy(receiptNumber = number)
        return number
    }

    override fun observeTodaySales(branchId: Uuid) = todaySales
}
