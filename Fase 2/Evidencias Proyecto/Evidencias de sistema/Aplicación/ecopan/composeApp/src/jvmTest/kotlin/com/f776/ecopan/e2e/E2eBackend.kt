package com.f776.ecopan.e2e

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The other side of a till journey (#145): the backend, asked over HTTP the way the dashboard asks it.
 * Points at the throwaway stack only; `scripts/e2e/run.sh till` sets the address.
 */
internal object E2eBackend {
    val api: String = System.getenv("ECOPAN_E2E_API") ?: "http://localhost:18080"
    private val http: HttpClient = HttpClient.newHttpClient()

    private fun request(method: String, path: String, token: String? = null, body: String? = null): String {
        val builder = HttpRequest.newBuilder(URI.create(api + path)).header("Content-Type", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        builder.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "$method $path -> ${response.statusCode()}: ${response.body()}" }
        return response.body()
    }

    /** A manager's access token. Each call is its own device so it never shares a refresh family with the app under test. */
    fun managerToken(): String {
        val body = request(
            "POST", "/auth/login",
            body = """{"tenantSlug":"reference","username":"gerente","password":"gerente123","deviceId":"e2e-till-check","deviceName":"e2e"}""",
        )
        return Regex(""""accessToken"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    /** How many sales the backend holds for one payment method, across all time (CASH, CARD, ...). */
    fun salesCount(paymentMethod: String, token: String = managerToken()): Long {
        val to = System.currentTimeMillis() + 86_400_000
        val body = request("GET", "/analytics/payment-methods?from=0&to=$to", token)
        val entry = Regex("""\{[^{}]*"paymentMethod"\s*:\s*"$paymentMethod"[^{}]*}""").find(body) ?: return 0
        return Regex(""""salesCount"\s*:\s*(\d+)""").find(entry.value)!!.groupValues[1].toLong()
    }

    class SaleLine(val productId: String, val soldBy: String, val weightGrams: Long?, val quantity: Int?, val unitPriceMinor: Long, val lineTotalMinor: Long)
    class Sale(val totalMinor: Long, val paymentMethod: String, val lines: List<SaleLine>)

    /** Where the sales ledger stands now: pass it to [salesSince] to see only what happens after. */
    fun salesCursor(token: String = managerToken()): Long {
        var cursor = 0L
        while (true) {
            val page = Json.parseToJsonElement(request("GET", "/sync/pull?since=$cursor&limit=500&entityTypes=SALE", token)).jsonObject
            cursor = page["nextCursor"]?.jsonPrimitive?.long ?: cursor
            if (page["hasMore"]?.jsonPrimitive?.content != "true") return cursor
        }
    }

    /** Every sale the backend recorded after [cursor], as the till pushed it. */
    fun salesSince(cursor: Long, token: String = managerToken()): List<Sale> {
        val page = Json.parseToJsonElement(request("GET", "/sync/pull?since=$cursor&limit=500&entityTypes=SALE", token)).jsonObject
        return page["changes"]!!.jsonArray.map { change ->
            val p = change.jsonObject["payload"]!!.jsonObject
            Sale(
                totalMinor = p["totalMinor"]!!.jsonPrimitive.long,
                paymentMethod = p["paymentMethod"]!!.jsonPrimitive.content,
                lines = p["lines"]!!.jsonArray.map { l ->
                    val o = l.jsonObject
                    SaleLine(
                        productId = o["productId"]!!.jsonPrimitive.content,
                        soldBy = o["soldBy"]!!.jsonPrimitive.content,
                        weightGrams = o["weightGrams"]?.jsonPrimitive?.longOrNull,
                        quantity = o["quantity"]?.jsonPrimitive?.longOrNull?.toInt(),
                        unitPriceMinor = o["unitPriceMinor"]!!.jsonPrimitive.long,
                        lineTotalMinor = o["lineTotalMinor"]!!.jsonPrimitive.long,
                    )
                },
            )
        }
    }

    /** Waits until the backend holds [count] sales after [cursor] (the till syncs on its own schedule). */
    fun awaitSales(cursor: Long, count: Int, timeoutMs: Long = 90_000): List<Sale> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var sales = salesSince(cursor)
        while (sales.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000)
            sales = salesSince(cursor)
        }
        check(sales.size >= count) { "expected $count sale(s) on the backend, found ${sales.size}" }
        return sales
    }

    /** A wrong-password sign-in; returns the HTTP status. Used to fill the auth rate limit's bucket for this machine. */
    fun wrongLogin(): Int {
        val request = HttpRequest.newBuilder(URI.create("$api/auth/login")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"tenantSlug":"reference","username":"nadie","password":"mal"}""")).build()
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun login(user: String, password: String): String {
        val body = request(
            "POST", "/auth/login",
            body = """{"tenantSlug":"reference","username":"$user","password":"$password","deviceId":"e2e-$user-${java.util.UUID.randomUUID()}","deviceName":"e2e"}""",
        )
        return body
    }

    private fun field(json: String, name: String): String = Regex(""""$name"\s*:\s*"([^"]+)"""").find(json)!!.groupValues[1]

    /** A unit product of the test's own (so no test depends on another's data); returns its id. */
    fun createUnitProduct(name: String, priceMinor: Long, token: String = managerToken()): String {
        val id = java.util.UUID.randomUUID().toString()
        request("POST", "/catalog/products", token,
            """{"id":"$id","name":"$name","productType":"Pastelería","soldBy":"UNIT","priceMinor":$priceMinor}""")
        return id
    }

    fun changeUnitPrice(id: String, name: String, priceMinor: Long, token: String = managerToken()) {
        request("PUT", "/catalog/products/$id", token,
            """{"name":"$name","productType":"Pastelería","soldBy":"UNIT","priceMinor":$priceMinor}""")
    }

    /** The baker logs a batch of the seeded product named [productName] on their phone; returns once the backend accepted it. */
    fun pushBatch(productName: String, mixWeightGrams: Long = 12_000): String {
        val manager = managerToken()
        val catalog = request("GET", "/catalog/products", manager)
        val productId = Regex("""\{[^{}]*"id"\s*:\s*"([^"]+)"[^{}]*"name"\s*:\s*"$productName"""").find(catalog)?.groupValues?.get(1)
            ?: Regex("""\{[^{}]*"name"\s*:\s*"$productName"[^{}]*"id"\s*:\s*"([^"]+)"""").find(catalog)!!.groupValues[1]
        val session = login("panadero", "panadero123")
        val token = field(session, "accessToken")
        val userId = Regex(""""user"\s*:\s*\{[^}]*"id"\s*:\s*"([^"]+)"""").find(session)!!.groupValues[1]
        val branchId = Regex(""""user"\s*:\s*\{[^}]*"branchId"\s*:\s*"([^"]+)"""").find(session)!!.groupValues[1]
        val batchId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val response = request("POST", "/sync/push", token, """{"deviceId":"e2e-baker-${java.util.UUID.randomUUID()}","mutations":[{"mutationId":"${java.util.UUID.randomUUID()}","entityType":"PRODUCTION_BATCH","opType":"CREATE","clientTimestamp":$now,"payload":{"id":"$batchId","branchId":"$branchId","bakerId":"$userId","productId":"$productId","mixWeightGrams":$mixWeightGrams,"batchAt":$now,"movementsMinted":false}}]}""")
        check("APPLIED" in response) { "the batch was not applied: $response" }
        return batchId
    }
}
