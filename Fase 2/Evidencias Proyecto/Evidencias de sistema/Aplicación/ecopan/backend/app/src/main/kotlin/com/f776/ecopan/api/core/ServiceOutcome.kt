package com.f776.ecopan.api.core

/**
 * Shared 3-way result for services fronting a mutating route: a 400-shaped validation failure is
 * distinct from "not found" (404) and from success. Used across `feature/tenant`, `feature/catalog`,
 * `feature/inventory` so routers have one uniform way to map a service call to a response.
 */
sealed interface ServiceOutcome<out T> {
    data class Success<T>(val value: T) : ServiceOutcome<T>
    data class Invalid(val code: String, val message: String) : ServiceOutcome<Nothing>
    data object NotFound : ServiceOutcome<Nothing>
}
