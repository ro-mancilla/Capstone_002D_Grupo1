package com.f776.ecopan.mobile.e2e

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The seeded reference tenant and the accounts the journeys use (scripts/demo-seed.py, the reference seed). */
internal object Seed {
    const val TENANT = "reference"
    val manager = Account("gerente", "gerente123")
    val baker = Account("panadero", "panadero123")
}

internal class Account(val username: String, val password: String)

/** The throwaway stack's access tokens live one minute (scripts/e2e/compose.yml); a cached one is reused for less than that. */
private const val TOKEN_REUSE_MS = 40_000L

/** Polls [probe] every second until it returns non-null or [timeoutMs] passes; [what] names the wait in the failure. */
internal suspend fun <T : Any> awaitUntil(what: String, timeoutMs: Long = 90_000, probe: suspend () -> T?): T {
    val deadline = currentMillis() + timeoutMs
    while (true) {
        probe()?.let { return it }
        check(currentMillis() < deadline) { "timed out waiting for $what" }
        delay(1_000)
    }
}

/** The backend as the dashboard sees it (the other side of a baker journey), over the throwaway stack only. */
internal object BakerBackend {
    private val http = HttpClient()
    private var bakerBranchId: String? = null
    private val tokens = mutableMapOf<String, Pair<Long, String>>()

    /** Signs in; a sign-in the throttle refuses (another journey is draining its bucket) is retried until it lets one through. */
    private suspend fun login(account: Account): String {
        val deadline = currentMillis() + THROTTLE_WINDOW_MS * 2
        while (true) {
            val response = http.post("$e2eApi/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("tenantSlug", Seed.TENANT); put("username", account.username); put("password", account.password)
                    put("deviceId", "e2e-baker-check-${account.username}"); put("deviceName", "e2e")
                }.toString())
            }
            if (response.status.isSuccess()) return response.bodyAsText()
            check(response.status.value == 429 && currentMillis() < deadline) { "${account.username} login -> ${response.status}" }
            delay(5_000)
        }
    }

    private fun String.field(name: String) = Regex(""""$name"\s*:\s*"([^"]+)"""").find(this)!!.groupValues[1]

    /** A bearer token for [account], reused while fresh so polling does not log in once a second. */
    private suspend fun token(account: Account): String {
        val cached = tokens[account.username]
        if (cached != null && currentMillis() - cached.first < TOKEN_REUSE_MS) return cached.second
        return login(account).field("accessToken").also { tokens[account.username] = currentMillis() to it }
    }

    private suspend fun managerToken() = token(Seed.manager)

    /** Production batches the backend holds, i.e. what phones have synced. */
    suspend fun batchCount(): Int {
        val response = http.get("$e2eApi/sync/pull?since=0&limit=500&entityTypes=PRODUCTION_BATCH") {
            header("Authorization", "Bearer ${managerToken()}")
        }
        return Regex(""""entityType"\s*:\s*"PRODUCTION_BATCH"""").findAll(response.bodyAsText()).count()
    }

    suspend fun awaitBatches(atLeast: Int, timeoutMs: Long = 90_000): Int =
        awaitUntil("$atLeast batch(es) on the backend", timeoutMs) { batchCount().takeIf { it >= atLeast } }

    /** The named ingredient's balance in grams, as the dashboard's ingredient stock page reads it. */
    suspend fun stockGrams(ingredient: String): Long {
        // The baker's own branch (the stock endpoint is per branch), read with the manager's token (the baker's is refused).
        val token = managerToken()
        val branchId = bakerBranchId ?: login(Seed.baker).field("branchId").also { bakerBranchId = it }
        val body = http.get("$e2eApi/inventory/stock?branchId=$branchId") { header("Authorization", "Bearer $token") }.bodyAsText()
        val entry = Regex("""\{[^{}]*"name"\s*:\s*"$ingredient"[^{}]*\}""").find(body)?.value
            ?: error("no stock level for $ingredient in $body")
        return Regex(""""currentGrams"\s*:\s*(-?\d+)""").find(entry)!!.groupValues[1].toLong()
    }

    /** Waits until [ingredient]'s balance has dropped by at least [byAtLeast] grams from [before] (the worker books asynchronously). */
    suspend fun awaitStockBelow(ingredient: String, before: Long, byAtLeast: Long, timeoutMs: Long = 90_000): Long =
        awaitUntil("$ingredient to drop by $byAtLeast g from $before g", timeoutMs) { stockGrams(ingredient).takeIf { before - it >= byAtLeast } }

    // ---- faults: the runner's control server (scripts/e2e/control.py), on the throwaway stack only ----------------

    private suspend fun control(path: String, body: String = ""): String {
        val response = http.post("$e2eControl$path") { setBody(body) }
        check(response.status.isSuccess()) { "control $path failed: ${response.bodyAsText()}" }
        return response.bodyAsText()
    }

    suspend fun stopApi() { control("/stop") }
    suspend fun startApi() { control("/start") }
    suspend fun sql(statement: String): String = control("/sql", statement)

    /** How many token refreshes the backend has refused as throttled so far (its request log, `POST /auth/refresh -> 429`). */
    suspend fun refreshesThrottled(): Int = Regex("POST /auth/refresh -> 429").findAll(control("/logs")).count()

    /** A failed sign-in, to drain the sign-in throttle's bucket; the status code (401, then 429 once throttled). */
    suspend fun wrongLogin(): Int = http.post("$e2eApi/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject { put("tenantSlug", Seed.TENANT); put("username", "nadie"); put("password", "mal") }.toString())
    }.status.value

    /** Re-files a product (the API forbids changing its origin, so the database is edited). */
    suspend fun setOrigin(productName: String, origin: String) {
        sql("update products set origin = '$origin' where name = '$productName'")
    }
}
