package com.f776.ecopan.api.seed

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.SaleInput
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import kotlinx.datetime.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Turns the generator's demand curve into sales and lines (plan 11 SEED-2).
 *
 * Three things this has to get right:
 *
 * **Freshness without breaking the weekday pattern.** The curve carries absolute, weekday-correct
 * dates, so a file committed months ago would otherwise show a dashboard whose "last 7 days" is
 * empty. [weekShiftDays] slides the whole series forward by a whole number of *weeks*, which moves
 * it to the present while leaving every row on the same day of the week — and weekday seasonality
 * (Saturday ≈ 1.45× Monday) is the pattern the demo is meant to show.
 *
 * **Idempotency.** Every id is derived from its content, so re-seeding the same profile on the same
 * day mints the same ids and `createAllWithLines` skips them all. Because the ids include the
 * *resolved* date, seeding again tomorrow adds exactly one new day rather than rewriting history —
 * the window rolls forward on its own.
 *
 * **Determinism.** All randomness comes from [Random] seeded by the row's own key, so basket sizes
 * and loaf weights never depend on iteration order or on how many profiles were loaded first.
 */
internal object SeedSalesExpander {

    /** A weigh-and-sell loaf. Spread so tickets are not all identical. */
    private const val LOAF_GRAMS_MEAN = 420
    private const val LOAF_GRAMS_JITTER = 150

    private const val BASKET_MIN = 1
    private const val BASKET_MAX = 4

    private const val OPEN_HOUR = 8
    private const val CLOSE_HOUR = 20

    /** Weighted so cash dominates, which is the reality this POS is built for. */
    private val PAYMENTS = listOf(
        PaymentMethod.CASH, PaymentMethod.CASH, PaymentMethod.CASH,
        PaymentMethod.CARD, PaymentMethod.CARD, PaymentMethod.TRANSFER
    )

    /**
     * Whole weeks to add so [lastDay] lands on or just before [today].
     *
     * Zero when the curve already ends in the future (a freshly generated file), so regenerating and
     * seeding on the same day is not shifted backwards.
     */
    fun weekShiftDays(lastDay: LocalDate, today: LocalDate): Long {
        val gapDays = today.toEpochDays() - lastDay.toEpochDays()
        if (gapDays <= 0) return 0
        return (gapDays / 7) * 7
    }

    /**
     * Expand [document] against the tenant's catalogue.
     *
     * Rows whose product or branch is unknown are skipped rather than failing the boot: a seed file
     * generated against an older profile should degrade, not take the service down.
     */
    fun expand(
        document: SeedSalesDocument,
        tenantId: Uuid,
        productsById: Map<Uuid, Product>,
        cashierByBranch: Map<Uuid, Uuid>,
        today: LocalDate,
        zone: ZoneId
    ): List<SaleInput> {
        if (document.rows.isEmpty()) return emptyList()

        val shift = weekShiftDays(LocalDate.parse(document.lastDay), today)

        // Group first: a ticket mixes product types, so baskets are built per (branch, day) over the
        // whole day's items rather than per product row.
        val byBranchDay = document.rows.groupBy { it.branchId to it.day }
        val sales = mutableListOf<SaleInput>()

        for ((key, rows) in byBranchDay) {
            val (branchIdRaw, dayRaw) = key
            val branchId = runCatching { Uuid.parse(branchIdRaw) }.getOrNull() ?: continue
            val cashierId = cashierByBranch[branchId] ?: continue

            val day = LocalDate.parse(dayRaw).let { LocalDate.fromEpochDays(it.toEpochDays() + shift) }
            // A shifted series can overshoot into the future on the tail; those days have not
            // "happened" yet and would show as impossible sales.
            if (day > today) continue

            val pool = buildList {
                for (row in rows) {
                    val productId = runCatching { Uuid.parse(row.productId) }.getOrNull() ?: continue
                    val product = productsById[productId] ?: continue
                    repeat(row.items) { add(product) }
                }
            }
            if (pool.isEmpty()) continue

            val random = Random(seedFor(branchIdRaw, day.toString()))
            val shuffled = pool.shuffled(random)

            var cursor = 0
            var ticket = 0
            while (cursor < shuffled.size) {
                val size = random.nextInt(BASKET_MIN, BASKET_MAX + 1).coerceAtMost(shuffled.size - cursor)
                val items = shuffled.subList(cursor, cursor + size)
                cursor += size

                val saleId = stableUuid("sale", branchIdRaw, day.toString(), ticket.toString())
                val lines = items.mapIndexed { index, product ->
                    lineFor(product, random, saleId, index)
                }

                sales += SaleInput(
                    id = saleId,
                    tenantId = tenantId,
                    branchId = branchId,
                    cashierId = cashierId,
                    soldAt = soldAt(day, random, zone),
                    paymentMethod = PAYMENTS[random.nextInt(PAYMENTS.size)],
                    totalMinor = lines.sumOf { it.lineTotalMinor },
                    deviceId = "seed-till-${branchIdRaw.take(8)}",
                    lines = lines
                )
                ticket++
            }
        }
        return sales
    }

    private fun lineFor(product: Product, random: Random, saleId: Uuid, index: Int): SaleLineInput {
        val lineId = stableUuid("line", saleId.toString(), index.toString())
        return if (product.soldBy == SoldBy.WEIGHT) {
            val grams = (LOAF_GRAMS_MEAN + random.nextInt(-LOAF_GRAMS_JITTER, LOAF_GRAMS_JITTER + 1))
                .coerceIn(120, 1600).toLong()
            val perKg = product.pricePerKgMinor ?: 0L
            SaleLineInput(
                id = lineId,
                productId = product.id,
                soldBy = SoldBy.WEIGHT,
                weightGrams = grams,
                quantity = null,
                unitPriceMinor = perKg,
                // Round once, here. Money stays integer minor units end to end (01 §5).
                lineTotalMinor = (perKg.toDouble() * grams / 1000.0).roundToLong()
            )
        } else {
            val quantity = random.nextInt(1, 4)
            val unitPrice = product.priceMinor ?: 0L
            SaleLineInput(
                id = lineId,
                productId = product.id,
                soldBy = SoldBy.UNIT,
                weightGrams = null,
                quantity = quantity,
                unitPriceMinor = unitPrice,
                lineTotalMinor = unitPrice * quantity
            )
        }
    }

    /** A trading-hours instant, biased early: the bread rush is in the morning. */
    private fun soldAt(day: LocalDate, random: Random, zone: ZoneId): Instant {
        val span = (CLOSE_HOUR - OPEN_HOUR) * 60
        // Min of two draws ⇒ a rising-then-falling shape weighted toward the open, without needing
        // a real beta sampler.
        val minutes = minOf(random.nextInt(span), random.nextInt(span))
        val local = java.time.LocalDate.of(day.year, day.monthNumber, day.day)
            .atTime(OPEN_HOUR, 0)
            .plusMinutes(minutes.toLong())
        return Instant.fromEpochMilliseconds(local.atZone(zone).toInstant().toEpochMilli())
    }

    private fun seedFor(vararg parts: String): Long =
        parts.joinToString("|").hashCode().toLong() * 2_862_933_555_777_941_757L + 3_037_000_493L

    /**
     * A UUID derived from its parts, so the same logical row always gets the same id.
     *
     * `Uuid.parse` needs a well-formed string, so the SHA-256 digest is trimmed to 32 hex chars and
     * stamped with version 5 / RFC-4122 variant bits rather than being used raw.
     */
    private fun stableUuid(vararg parts: String): Uuid {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("|").toByteArray())
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return Uuid.parse(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        )
    }
}
