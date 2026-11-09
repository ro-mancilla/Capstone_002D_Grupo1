package com.f776.ecopan.api.core.storage

import com.azure.core.util.Context
import com.azure.identity.ManagedIdentityCredentialBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobCorsRule
import com.azure.storage.blob.models.BlobRange
import com.azure.storage.blob.models.BlobStorageException
import java.io.ByteArrayOutputStream
import com.azure.storage.blob.models.PublicAccessType
import com.azure.storage.blob.options.BlobContainerCreateOptions
import com.azure.storage.blob.sas.BlobSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import com.f776.ecopan.api.core.StorageSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * [ObjectStorage] over one Azure Blob container.
 *
 * Two ways in, chosen by [StorageSettings]:
 *  - `accountUrl` set -> the app's **managed identity** (`ManagedIdentityCredential`). The App
 *    Service deployment works this way: the role assignments in `infra/apps.bicep` are the whole
 *    credential, shared-key access is disabled on the account, and upload grants are *user
 *    delegation* SAS -- signed with a key Azure hands the identity, valid at most a few minutes.
 *  - otherwise `connectionString` -> Azurite under docker-compose (or a local `:backend:app:run`
 *    against the published port), using the emulator's public, well-known dev account; grants are
 *    ordinary service SAS signed with that key.
 *
 * The SDK's synchronous client runs on `Dispatchers.IO`. Nothing here moves file bytes: the calls
 * are sign-a-URL, does-it-exist and delete, all small and quick.
 *
 * References:
 *  - https://learn.microsoft.com/azure/storage/blobs/storage-blob-user-delegation-sas-create-java
 *  - https://learn.microsoft.com/azure/storage/blobs/storage-quickstart-blobs-java
 */
internal class AzureBlobObjectStorage(private val settings: StorageSettings) : ObjectStorage {

    private val log = LoggerFactory.getLogger(AzureBlobObjectStorage::class.java)

    private val managedIdentity = !settings.accountUrl.isNullOrBlank()

    private val service: BlobServiceClient by lazy {
        val builder = BlobServiceClientBuilder()
        if (managedIdentity) {
            builder.endpoint(settings.accountUrl).credential(ManagedIdentityCredentialBuilder().build())
            log.info("Object storage: Azure Blob at {} via managed identity", settings.accountUrl)
        } else {
            builder.connectionString(settings.connectionString)
            log.info("Object storage: Azure Blob via connection string (container '{}')", settings.container)
        }
        builder.buildClient()
    }

    private val container: BlobContainerClient by lazy { service.getBlobContainerClient(settings.container) }

    override fun publicUrl(key: String): String = "${settings.publicBlobBaseUrl}/${settings.container}/$key"

    override suspend fun grantUpload(key: String, contentType: String, ttl: Duration): UploadGrant = storageCall("grant $key") {
        val now = OffsetDateTime.now()
        val expiry = now.plus(ttl.toJavaDuration())
        val permission = BlobSasPermission().setCreatePermission(true).setWritePermission(true)
        val values = BlobServiceSasSignatureValues(expiry, permission)
            // Clock skew between this host and the storage service; a grant is minutes long anyway.
            .setStartTime(now.minusMinutes(5))
            .setContentType(contentType)
        val blob = container.getBlobClient(key)
        val sas = if (managedIdentity) {
            blob.generateUserDelegationSas(values, service.getUserDelegationKey(now.minusMinutes(5), expiry))
        } else {
            blob.generateSas(values)
        }
        // The signature covers account, container and blob name, not the host -- so the URL can
        // carry the *public* host (localhost:10000 for Azurite) while the app itself signed it
        // through the compose-internal one.
        UploadGrant(uploadUrl = "${publicUrl(key)}?$sas", expiresAt = Clock.System.now() + ttl)
    }

    override suspend fun inspect(key: String, headBytes: Int): StoredBlob? = storageCall("inspect $key") {
        val blob = container.getBlobClient(key)
        if (!blob.exists()) {
            null
        } else {
            val size = blob.properties.blobSize
            val head = ByteArrayOutputStream()
            // Only the first bytes cross the wire: the photo itself never passes through the API.
            if (size > 0) {
                blob.downloadStreamWithResponse(head, BlobRange(0, minOf(size, headBytes.toLong())), null, null, false, null, Context.NONE)
            }
            StoredBlob(size, head.toByteArray())
        }
    }

    override suspend fun delete(key: String) = storageCall("delete $key") {
        container.getBlobClient(key).deleteIfExists()
        Unit
    }

    override suspend fun provisionForDevelopment(corsAllowedOrigins: List<String>) {
        if (managedIdentity) return
        storageCall("provision") {
            container.createIfNotExistsWithResponse(
                BlobContainerCreateOptions().setPublicAccessType(PublicAccessType.BLOB), null, null
            )
            val properties = service.properties
            properties.cors = listOf(
                BlobCorsRule()
                    .setAllowedOrigins(corsAllowedOrigins.joinToString(","))
                    .setAllowedMethods("GET,HEAD,PUT,OPTIONS")
                    .setAllowedHeaders("*")
                    .setExposedHeaders("*")
                    .setMaxAgeInSeconds(3600)
            )
            service.setProperties(properties)
            log.info("Object storage: dev container '{}' public-read, CORS for {}", settings.container, corsAllowedOrigins)
        }
    }

    /** Anything the SDK throws -- connection refused, a 403 from a missing role, a container the
     * identity cannot see -- is the store being unavailable; the caller says so as a 503. */
    private suspend fun <T> storageCall(what: String, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: BlobStorageException) {
            log.warn("Object storage {} failed: {} {}", what, e.statusCode, e.errorCode)
            throw StorageUnavailableException("blob storage rejected $what (${e.statusCode} ${e.errorCode})", e)
        } catch (e: RuntimeException) {
            log.warn("Object storage {} failed: {}", what, e.toString())
            throw StorageUnavailableException("blob storage unreachable for $what", e)
        }
    }
}
