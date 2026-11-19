package com.f776.ecopan.data.sales.unittest

import com.f776.ecopan.data.sales.wire.toPushPayload
import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleLine
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Pins the *wire shape* of a pushed sale against the backend's `SalePayload`
 * (`backend/app/.../feature/sync/controller/dto/SyncDtos.kt`), which declares `soldAt: Long`.
 *
 * This client sent `soldAt` as an ISO-8601 string instead, so the server rejected every sale it
 * ever received and the client marked the row FAILED — permanently, since a rejection is terminal.
 * No test caught it because both sides were only ever exercised against their own fakes:
 * `FakeServer` echoes whatever payload it is handed without validating it, so end-to-end sync tests
 * passed while no real sale could cross the wire.
 *
 * Asserting the JSON primitive types is the closest a client-side test can get to the real contract
 * without depending on the backend module, which the client deliberately cannot see.
 */
class SalePushPayloadContractTest {

    private fun sale(soldAt: Instant) = Sale(
        id = Uuid.random(),
        branchId = Uuid.random(),
        cashierId = Uuid.random(),
        timestamp = soldAt,
        paymentMethod = PaymentMethod.CASH,
        totalMinor = 6900,
        cashTenderedMinor = 10000,
        changeMinor = 3100,
        lines = listOf(
            SaleLine(
                id = Uuid.random(),
                productId = Uuid.random(),
                productTypeSnapshot = "Pastelería",
                soldBy = SoldBy.UNIT,
                weightGrams = null,
                quantity = 1,
                unitPriceMinorSnapshot = 6900,
                lineTotalMinor = 6900,
            ),
        ),
    )

    @Test
    fun `soldAt is serialized as epoch millis not an ISO-8601 string`() {
        val soldAt = Instant.fromEpochMilliseconds(1_784_952_320_138)

        val json = Json.encodeToJsonElement(
            com.f776.ecopan.data.sales.wire.SalePushPayload.serializer(),
            sale(soldAt).toPushPayload(),
        ).jsonObject

        val encoded = json.getValue("soldAt").jsonPrimitive
        assertTrue(encoded.isString.not(), "soldAt must be a JSON number — the server parses it as a Long")
        assertEquals(1_784_952_320_138L, encoded.content.toLong())
    }
}
