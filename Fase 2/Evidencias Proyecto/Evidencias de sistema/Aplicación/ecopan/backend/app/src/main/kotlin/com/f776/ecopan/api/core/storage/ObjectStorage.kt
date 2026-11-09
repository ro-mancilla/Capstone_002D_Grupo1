package com.f776.ecopan.api.core.storage

import kotlin.time.Duration
import kotlin.time.Instant

/** A one-blob, write-only, short-lived upload URL: the client `PUT`s the file straight to it. */
class UploadGrant(val uploadUrl: String, val expiresAt: Instant)

/** What [ObjectStorage.inspect] read: the blob's full [size] and up to the first few bytes. */
class StoredBlob(val size: Long, val head: ByteArray)

/** Raised when the store is unreachable or refuses the call; the API turns it into a 503. */
class StorageUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The API's view of object storage, and deliberately a narrow one: **files never pass through the
 * backend.** Clients upload to and read from the store directly; this API only decides who may
 * write which key, for how long, and what the stored record points at.
 *
 * - Write access is granted per key with [grantUpload] -- a SAS scoped to one blob, create+write
 *   only, expiring in minutes. In Azure it is a *user delegation* SAS signed with the app's
 *   managed identity (no account key exists to leak); against Azurite it is signed with the
 *   emulator's published dev key.
 * - Read access is the container's public-read setting: a product photo is served to browsers'
 *   `<img>` tags and the tills' image loader with no token, from a URL that stays valid while
 *   a device is offline. Revoking a photo therefore means deleting the blob ([delete]).
 * - [inspect] lets the API confirm an upload actually landed, and is a photo of a sane size, before
 *   recording its URL: its size plus a ranged read of the first bytes, never the file itself.
 *
 * Today this holds product photos, keyed `{tenantId}/{productId}/{random}.{ext}`; one
 * implementation ([AzureBlobObjectStorage]) covers Azurite locally and Azure Storage in
 * production. Router tests use an in-memory fake.
 */
interface ObjectStorage {
    /** Where anyone can read [key] from, once uploaded. Stable; safe to persist and sync. */
    fun publicUrl(key: String): String

    suspend fun grantUpload(key: String, contentType: String, ttl: Duration): UploadGrant

    /** `null` if nothing is at [key]; otherwise its size and its first [headBytes] bytes (a ranged read). */
    suspend fun inspect(key: String, headBytes: Int): StoredBlob?

    suspend fun delete(key: String)

    /**
     * Dev-only bootstrap: against Azurite, create the container public-read and open CORS to
     * the dashboard origins so a browser can `PUT` to it. In Azure this is a no-op -- the
     * container, its access level and its CORS rules are declared in `infra/`, and the app's
     * identity holds data-plane roles only, never account-level ones.
     */
    suspend fun provisionForDevelopment(corsAllowedOrigins: List<String>)
}
