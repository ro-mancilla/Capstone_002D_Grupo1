@file:OptIn(ExperimentalTestApi::class)

package com.f776.ecopan.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Stories 63-69: the till and the backend, with the backend failing. Faults are real: containers stop and start, the
 * database loses a row, a session's refresh token is revoked. After each fault the sales are counted on the backend.
 */
class SyncE2eTest {

    @Test
    fun salesMadeOfflineQueueAndSyncOnceTheBackendReturns() = runComposeUiTest(testTimeout = 400.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("sync-offline") {
            enroll()
            waitForText("Sincronizado")
            E2eStack.stop("web")
            try {
                sellInCash("Berlín", "\$1.000")
                sellInCash("Berlín", "\$1.000")
                waitForText("Sin conexión", substring = true)
                waitForText("2 pendientes", substring = true)
            } finally {
                E2eStack.start("web")
            }
            waitForText("Sincronizado", timeoutMs = 120_000)
            // Both sales arrived, once each.
            val sales = E2eBackend.awaitSales(cursor, 2)
            assertEquals(2, sales.size)
            assertEquals(setOf(900L), sales.map { it.totalMinor }.toSet())
        }
    }

    @Test
    fun aBatchAndAPriceChangeMadeElsewhereReachTheTill() = runComposeUiTest(testTimeout = 400.seconds) {
        tillJourney("sync-cross-client") {
            enroll()
            val name = "e2e precio ${System.currentTimeMillis()}"
            // The till syncs after each sale (and every five minutes); ringing one up is "the next sync".
            val id = E2eBackend.createUnitProduct(name, 1_111)
            sellInCash("Berlín", "\$1.000")
            // The grid only composes what fits the window, so find the new product through the search box.
            typeIntoTag("pos_search", name)
            waitForText(name, timeoutMs = 120_000)
            waitForText("\$1.111", timeoutMs = 10_000) // only this product is left in the grid
            typeIntoTag("pos_search", "")

            E2eBackend.changeUnitPrice(id, name, 1_222)
            sellInCash("Berlín", "\$1.000")
            typeIntoTag("pos_search", name)
            waitForText("\$1.222", timeoutMs = 120_000)
            typeIntoTag("pos_search", "")

            // A baker's batch, logged on a phone, shows up on the till as the batch-ready notice.
            E2eBackend.pushBatch("Marraqueta")
            waitForText("Lote de Marraqueta listo", substring = true, timeoutMs = 60_000)
        }
    }

    @Test
    fun aFailingOrThrottledBackendNeverAsksForAttention() = runComposeUiTest(testTimeout = 400.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("sync-recovers") {
            enroll()
            // The backend goes away for a while: offline, not "Requiere atención".
            E2eStack.stop("web")
            try {
                sellInCash("Berlín", "\$1.000")
                waitForText("Sin conexión", substring = true)
                Thread.sleep(15_000)
                assertNoText("Requiere atención")
            } finally {
                E2eStack.start("web")
            }
            waitForText("Sincronizado", timeoutMs = 120_000)
            E2eBackend.awaitSales(cursor, 1)

            // Sign-in throttled while the one-minute access token expires: the refresh is refused with 429, which
            // says nothing about the session. The till keeps it and carries on.
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                E2eBackend.wrongLogin()
                assertNoText("Requiere atención")
                Thread.sleep(1_000)
            }
            sellInCash("Berlín", "\$1.000")
            // Free the bucket, then the sale goes through on its own.
            val bucketDeadline = System.currentTimeMillis() + 90_000
            while (E2eBackend.wrongLogin() == 429 && System.currentTimeMillis() < bucketDeadline) Thread.sleep(2_000)
            waitForText("Sincronizado", timeoutMs = 120_000)
            assertEquals(2, E2eBackend.awaitSales(cursor, 2).size)
        }
    }

    @Test
    fun aRevokedDeviceSessionAsksForAReconnectAndKeepsTheQueuedSale() = runComposeUiTest(testTimeout = 500.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("sync-revoked") {
            val device = "Caja revocada ${System.currentTimeMillis()}"
            enroll(deviceName = device)
            waitForText("Sincronizado")
            E2eStack.sql("update refresh_tokens set revoked_at = now() where device_name = '$device' and revoked_at is null")

            // The access token lives a minute; the sale that follows finds the session dead on its next sync.
            Thread.sleep(65_000)
            sellInCash("Berlín", "\$1.000")
            waitForText("Requiere atención", timeoutMs = 120_000)
            // Tapping the indicator opens the reconnect form, which says how many sales are waiting.
            click("Requiere atención")
            waitForText("Reconectar esta caja")
            waitForText("esperando enviarse", substring = true)

            // A manager reconnects the till; the queued sale is sent.
            type("Usuario", "jefelocal")
            type("Contraseña", "jefelocal123")
            click("Reconectar")
            waitForText("Sincronizado", timeoutMs = 120_000)
            assertEquals(1, E2eBackend.awaitSales(cursor, 1).size)
        }
    }

    @Test
    fun rejectedOperationsAreListedInSettingsAndCanBeRetriedOrDiscarded() = runComposeUiTest(testTimeout = 400.seconds) {
        val cursor = E2eBackend.salesCursor()
        tillJourney("sync-rejected") {
            enroll()
            val name = "e2e rechazo ${System.currentTimeMillis()}"
            E2eBackend.createUnitProduct(name, 1_500)
            sellInCash("Berlín", "\$1.000") // the next sync brings the new product to the till
            typeIntoTag("pos_search", name)
            waitForText(name, timeoutMs = 120_000)
            typeIntoTag("pos_search", "")

            // The product vanishes from the backend (a hard delete the till never hears of); the sale of it is refused.
            E2eStack.sql("delete from products where name = '$name'")
            sellInCash(name, "\$5.000")

            click("Ajustes")
            waitForText("Operaciones rechazadas", timeoutMs = 90_000)
            assertText("Venta")
            assertText("Motivo", substring = true)

            // Retrying sends it again; the cause is still there, so it is refused again and stays listed.
            click("Reintentar")
            waitForText("Operaciones rechazadas", timeoutMs = 60_000)

            // Discarding asks first, then removes it from the list.
            click("Descartar")
            click("Descartar operación")
            assertNoText("Operaciones rechazadas")

            // The refused sale never became a backend sale; the one before it did.
            assertEquals(1, E2eBackend.awaitSales(cursor, 1).size)
        }
    }
}
