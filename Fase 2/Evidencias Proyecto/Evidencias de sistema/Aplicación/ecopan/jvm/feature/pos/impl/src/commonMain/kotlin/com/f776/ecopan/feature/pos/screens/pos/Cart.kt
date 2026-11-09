package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.money.lineTotalForUnits
import com.f776.ecopan.core.domain.money.lineTotalForWeight
import com.f776.ecopan.domain.catalog.Product
import kotlin.uuid.Uuid

/**
 * The draft sale (05 §5): lines plus the one rule that binds them to a total.
 *
 * Lines and total used to be two independent fields on [PosUiState], with
 * `totalMinor = lines.sumOf { … }` re-asserted by hand at every mutation and reset at two more.
 * A sixth cart action had to rediscover it, and the two could disagree in between. Here the total
 * is derived, so they cannot.
 *
 * Also the one home for the rules that went with it: merge-or-append for repeated unit taps, the
 * per-[SoldBy] choice of money rule, the line caption, and step-to-zero-removes.
 *
 * Nothing is persisted until payment — this is draft state, and voiding a line before paying is
 * a plain removal (05 §6).
 */
internal data class Cart(val lines: List<PosCartLineUi> = emptyList()) {

    val totalMinor: Long get() = lines.sumOf { it.totalMinor }

    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * Tapping the same unit product again increments its line rather than adding a second one —
     * a cashier ringing up four empanadas taps four times and expects one line of four.
     * A product with no unit price cannot be sold by unit, so the cart is unchanged.
     */
    fun addUnit(product: Product): Cart {
        val price = product.priceMinor ?: return this
        val index = lines.indexOfFirst { it.productId == product.id && it.soldBy == SoldBy.UNIT }

        if (index < 0) {
            return Cart(lines + unitLine(product, price, quantity = 1))
        }
        val existing = lines[index]
        val quantity = (existing.quantity ?: 0) + 1
        return Cart(lines.toMutableList().apply { this[index] = existing.withQuantity(quantity) })
    }

    /**
     * Weighed lines never merge: two bags of the same bread are two weighings, and combining them
     * would lose the weight the customer watched being taken.
     */
    fun addWeighed(product: Product, grams: Long, pricePerKgMinor: Long): Cart =
        Cart(
            lines + PosCartLineUi(
                lineId = Uuid.random(),
                productId = product.id,
                productTypeSnapshot = product.productType,
                soldBy = SoldBy.WEIGHT,
                name = product.name,
                metaLabel = "${formatGramsAsKg(grams)} × ${formatMinor(pricePerKgMinor)}/kg",
                weightGrams = grams,
                quantity = null,
                unitPriceMinorSnapshot = pricePerKgMinor,
                totalMinor = lineTotalForWeight(grams, pricePerKgMinor),
            )
        )

    /** Stepping a unit line to zero removes it, which is how the last one is taken off. */
    fun step(lineId: Uuid, delta: Int): Cart {
        val index = lines.indexOfFirst { it.lineId == lineId }
        if (index < 0) return this
        val existing = lines[index]
        val quantity = (existing.quantity ?: return this) + delta

        if (quantity <= 0) return remove(lineId)
        return Cart(lines.toMutableList().apply { this[index] = existing.withQuantity(quantity) })
    }

    fun remove(lineId: Uuid): Cart = Cart(lines.filterNot { it.lineId == lineId })

    private fun unitLine(product: Product, priceMinor: Long, quantity: Int) = PosCartLineUi(
        lineId = Uuid.random(),
        productId = product.id,
        productTypeSnapshot = product.productType,
        soldBy = SoldBy.UNIT,
        name = product.name,
        metaLabel = unitLabel(quantity, priceMinor),
        weightGrams = null,
        quantity = quantity,
        unitPriceMinorSnapshot = priceMinor,
        totalMinor = lineTotalForUnits(quantity.toLong(), priceMinor),
    )
}

/** Caption and total move together — a quantity change that updated one and not the other was
 * the shape of bug this consolidation removes. */
private fun PosCartLineUi.withQuantity(quantity: Int): PosCartLineUi = copy(
    quantity = quantity,
    metaLabel = unitLabel(quantity, unitPriceMinorSnapshot),
    totalMinor = lineTotalForUnits(quantity.toLong(), unitPriceMinorSnapshot),
)

private fun unitLabel(quantity: Int, priceMinor: Long) = "$quantity u. × ${formatMinor(priceMinor)}"
