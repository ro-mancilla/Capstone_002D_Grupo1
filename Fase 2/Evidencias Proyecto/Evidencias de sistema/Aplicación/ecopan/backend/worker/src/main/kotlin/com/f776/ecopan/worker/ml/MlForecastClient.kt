package com.f776.ecopan.worker.ml

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface MlForecastClient {
    suspend fun forecast(request: MlForecastRequest): MlForecastResponse
}

/**
 * The one trusted caller of the ML service (08 §5): shared-key auth via `X-API-Key`
 * (`ML_API_KEY`, 02 §7). The header is omitted when the key is blank, matching the service's
 * dev-mode behaviour of skipping the check when its own `ML_API_KEY` is unset.
 */
class KtorMlForecastClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val httpClient: HttpClient,
) : MlForecastClient {

    override suspend fun forecast(request: MlForecastRequest): MlForecastResponse =
        httpClient.post("${baseUrl.trimEnd('/')}/forecast") {
            contentType(ContentType.Application.Json)
            if (apiKey.isNotBlank()) header("X-API-Key", apiKey)
            setBody(request)
        }.body()
}
