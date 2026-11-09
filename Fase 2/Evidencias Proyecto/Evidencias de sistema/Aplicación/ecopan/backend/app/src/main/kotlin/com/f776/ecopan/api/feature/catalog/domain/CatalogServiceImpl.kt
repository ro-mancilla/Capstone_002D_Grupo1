package com.f776.ecopan.api.feature.catalog.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.core.storage.ObjectStorage
import com.f776.ecopan.api.feature.catalog.controller.dto.CreateProductRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.FormulaDto
import com.f776.ecopan.api.feature.catalog.controller.dto.FormulaLineDto
import com.f776.ecopan.api.feature.catalog.controller.dto.ImageUploadGrantDto
import com.f776.ecopan.api.feature.catalog.controller.dto.ProductDto
import com.f776.ecopan.api.feature.catalog.controller.dto.PutFormulaRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.UpdateProductRequest
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.domain.ProductKind
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.OpeningCount
import kotlin.time.Clock
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.Product
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private val IMAGE_EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")

/** Long enough for a phone on a bad connection to push a 5 MB photo, short enough to be useless later. */
private val UPLOAD_GRANT_TTL = 10.minutes

/** Largest product photo accepted at confirm (#138). */
private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024

/** Enough leading bytes to tell JPEG, PNG and WebP apart from anything else. */
private const val IMAGE_SNIFF_BYTES = 12

/** JPEG `FF D8 FF`, PNG's 8-byte signature, or WebP's `RIFF....WEBP`. */
private fun looksLikeImage(head: ByteArray): Boolean {
    fun at(offset: Int, vararg bytes: Int) =
        head.size >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
    return at(0, 0xFF, 0xD8, 0xFF) ||
        at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
        (at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50))
}

private const val OPENING_MOVEMENT_SALT = 0x0DE17ACC0DEL

private val UNKNOWN_TYPE = ServiceOutcome.Invalid(
    "UNKNOWN_PRODUCT_TYPE", "productType must be one of: Pan, Pastelería, Almacén"
)

internal class CatalogServiceImpl(
    private val catalogRepository: CatalogRepository,
    private val branchRepository: BranchRepository,
    private val objectStorage: ObjectStorage
) : CatalogService {

    override suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<ProductDto> =
        catalogRepository.listProducts(tenantId, includeInactive).map { it.toDto() }

    override suspend fun createProduct(
        tenantId: Uuid,
        request: CreateProductRequest,
        createdBy: Uuid?,
        principalBranchId: Uuid?
    ): ServiceOutcome<ProductDto> {
        val id = runCatching { Uuid.parse(request.id) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_ID", "id must be a valid uuid")
        val soldBy = request.soldBy.toSoldByOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_SOLD_BY", "soldBy must be WEIGHT or UNIT")
        // One field: Tipo. The origin is derived from it, never chosen (a stray `origin` is ignored).
        val kind = ProductKind.fromName(request.productType) ?: return UNKNOWN_TYPE
        val origin = kind.origin
        validateOrigin(origin, soldBy)?.let { return it }
        validatePrice(soldBy, request.pricePerKgMinor, request.priceMinor)?.let { return it }

        // An Almacén product is tracked from creation, so its opening count is validated *before*
        // the row exists: a refused count must not leave a product without stock behind.
        var openingBranchId: Uuid? = null
        if (origin == ProductOrigin.ALMACEN) {
            val opening = request.openingStock
                ?: return ServiceOutcome.Invalid("OPENING_STOCK_REQUIRED", "an ALMACEN product needs an openingStock (0 is allowed)")
            if (opening < 0) return ServiceOutcome.Invalid("INVALID_OPENING_STOCK", "openingStock must not be negative")
            val requested = request.openingBranchId?.let {
                runCatching { Uuid.parse(it) }.getOrNull()
                    ?: return ServiceOutcome.Invalid("INVALID_BRANCH", "openingBranchId must be a valid uuid")
            }
            val branchId = requested ?: principalBranchId
                ?: return ServiceOutcome.Invalid("OPENING_BRANCH_REQUIRED", "openingBranchId is required when the caller is not tied to a branch")
            if (principalBranchId != null && principalBranchId != branchId) {
                return ServiceOutcome.Invalid("BRANCH_FORBIDDEN", "principal is scoped to a different branch")
            }
            val branch = branchRepository.findById(branchId)
            if (branch == null || branch.tenantId != tenantId) {
                return ServiceOutcome.Invalid("INVALID_BRANCH", "openingBranchId is not a branch of this tenant")
            }
            openingBranchId = branchId
        }

        // One transaction in the repository: the product row and its opening count land together or
        // not at all. The movement id is derived from the product id, so a ledger row can never be
        // duplicated; a repeated create of an already-stored product id fails on the product's key.
        val opening = openingBranchId?.let {
            OpeningCount(
                movementId = id.toLongs { msb, lsb -> Uuid.fromLongs(msb xor OPENING_MOVEMENT_SALT, lsb) },
                branchId = it,
                units = request.openingStock!!,
                createdBy = createdBy,
                occurredAt = Clock.System.now()
            )
        }
        val product = catalogRepository.createProduct(
            id = id,
            tenantId = tenantId,
            name = request.name,
            productType = kind.label,
            soldBy = soldBy,
            pricePerKgMinor = request.pricePerKgMinor,
            priceMinor = request.priceMinor,
            barcode = request.barcode,
            imageUrl = null,
            origin = origin,
            opening = opening
        )
        return ServiceOutcome.Success(product.toDto())
    }

    override suspend fun updateProduct(tenantId: Uuid, productId: Uuid, request: UpdateProductRequest): ServiceOutcome<ProductDto> {
        val existing = catalogRepository.findProductById(productId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound

        val soldBy = request.soldBy.toSoldByOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_SOLD_BY", "soldBy must be WEIGHT or UNIT")
        val kind = ProductKind.fromName(request.productType) ?: return UNKNOWN_TYPE
        // Pan <-> Pastelería is a plain edit; crossing Almacén either way would orphan stock or a
        // formula, so the Tipo's side (baked vs bought-in) is fixed once the product exists.
        if (kind.origin != existing.origin) {
            return ServiceOutcome.Invalid(
                "ORIGIN_IMMUTABLE", "a product cannot change between Almacén and Pan / Pastelería after it is created"
            )
        }
        validateOrigin(existing.origin, soldBy)?.let { return it }
        validatePrice(soldBy, request.pricePerKgMinor, request.priceMinor)?.let { return it }

        val updated = catalogRepository.updateProduct(
            id = productId,
            name = request.name,
            productType = kind.label,
            soldBy = soldBy,
            pricePerKgMinor = request.pricePerKgMinor,
            priceMinor = request.priceMinor,
            barcode = request.barcode
        ) ?: return ServiceOutcome.NotFound
        return ServiceOutcome.Success(updated.toDto())
    }

    override suspend fun grantProductImageUpload(
        tenantId: Uuid,
        productId: Uuid,
        contentType: String
    ): ServiceOutcome<ImageUploadGrantDto> {
        val existing = catalogRepository.findProductById(productId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound
        val normalized = contentType.trim().lowercase()
        val extension = IMAGE_EXTENSIONS[normalized]
            ?: return ServiceOutcome.Invalid("UNSUPPORTED_IMAGE_TYPE", "image must be JPEG, PNG or WebP")

        // A fresh key per upload: the product's URL changes with every new photo, so every cache
        // between the store and a till's screen sees a replaced picture as a new one.
        val key = "${imagePrefix(tenantId, productId)}${Uuid.random()}.$extension"
        val grant = objectStorage.grantUpload(key, normalized, UPLOAD_GRANT_TTL)
        return ServiceOutcome.Success(
            ImageUploadGrantDto(key = key, uploadUrl = grant.uploadUrl, expiresAt = grant.expiresAt.toEpochMilliseconds(), contentType = normalized)
        )
    }

    override suspend fun confirmProductImage(tenantId: Uuid, productId: Uuid, key: String): ServiceOutcome<ProductDto> {
        val existing = catalogRepository.findProductById(productId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound
        // The key must be one this API could have granted for *this* product -- a client cannot
        // point a product at another tenant's blob, or at anything outside its own prefix.
        if (!key.startsWith(imagePrefix(tenantId, productId)) || key.contains("..")) {
            return ServiceOutcome.Invalid("INVALID_IMAGE_KEY", "key does not belong to this product")
        }
        val blob = objectStorage.inspect(key, IMAGE_SNIFF_BYTES)
            ?: return ServiceOutcome.Invalid("IMAGE_NOT_UPLOADED", "no blob at that key -- upload before confirming")
        // #138: the grant fixes the key and type, not what the client actually sent. Anything over
        // the limit, or that is not a JPEG/PNG/WebP by its leading bytes, is deleted, and the
        // product keeps whatever photo it had.
        val rejection = when {
            blob.size > MAX_IMAGE_BYTES -> ServiceOutcome.Invalid("IMAGE_TOO_LARGE", "a product photo is at most ${MAX_IMAGE_BYTES / (1024 * 1024)} MB")
            !looksLikeImage(blob.head) -> ServiceOutcome.Invalid("IMAGE_NOT_AN_IMAGE", "the upload is not a JPEG, PNG or WebP image")
            else -> null
        }
        if (rejection != null) {
            objectStorage.delete(key)
            return rejection
        }

        val previousKey = existing.imageUrl?.let { ownedKey(tenantId, productId, it) }
        val updated = catalogRepository.setProductImageUrl(productId, objectStorage.publicUrl(key)) ?: return ServiceOutcome.NotFound
        if (previousKey != null && previousKey != key) objectStorage.delete(previousKey)
        return ServiceOutcome.Success(updated.toDto())
    }

    override suspend fun deleteProductImage(tenantId: Uuid, productId: Uuid): ServiceOutcome<ProductDto> {
        val existing = catalogRepository.findProductById(productId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound
        // An external URL (the seeds point at Wikipedia) is not ours to delete; only clear it.
        existing.imageUrl?.let { ownedKey(tenantId, productId, it) }?.let { objectStorage.delete(it) }
        val updated = catalogRepository.setProductImageUrl(productId, null) ?: return ServiceOutcome.NotFound
        return ServiceOutcome.Success(updated.toDto())
    }

    private fun imagePrefix(tenantId: Uuid, productId: Uuid) = "$tenantId/$productId/"

    /** The blob key behind [url] if it is one of ours for this product, else null. */
    private fun ownedKey(tenantId: Uuid, productId: Uuid, url: String): String? {
        val prefix = imagePrefix(tenantId, productId)
        val base = objectStorage.publicUrl(prefix)
        return if (url.startsWith(base)) prefix + url.removePrefix(base) else null
    }

    override suspend fun deleteProduct(tenantId: Uuid, productId: Uuid): Boolean {
        val existing = catalogRepository.findProductById(productId)
        if (existing == null || existing.tenantId != tenantId) return false
        catalogRepository.updateProduct(id = productId, active = false)
        return true
    }

    override suspend fun listFormulas(tenantId: Uuid): List<FormulaDto> =
        catalogRepository.listFormulas(tenantId).map { it.toDto() }

    override suspend fun putFormula(tenantId: Uuid, productId: Uuid, request: PutFormulaRequest): ServiceOutcome<FormulaDto> {
        val product = catalogRepository.findProductById(productId)
        if (product == null || product.tenantId != tenantId) return ServiceOutcome.NotFound
        if (product.origin == ProductOrigin.ALMACEN) {
            return ServiceOutcome.Invalid("FORMULA_REQUIRES_PANADERIA", "an ALMACEN product cannot have a formula")
        }

        val lines = mutableListOf<FormulaLine>()
        for (line in request.lines) {
            val ingredientId = runCatching { Uuid.parse(line.ingredientId) }.getOrNull()
                ?: return ServiceOutcome.Invalid("INVALID_INGREDIENT", "ingredientId must be a valid uuid")
            if (line.basisPoints <= 0) {
                return ServiceOutcome.Invalid("INVALID_BASIS_POINTS", "basisPoints must be > 0")
            }
            val lineId = line.id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()
            lines += FormulaLine(id = lineId, ingredientId = ingredientId, basisPoints = line.basisPoints)
        }

        val formulaId = request.id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()
        val formula = catalogRepository.putFormula(id = formulaId, tenantId = tenantId, productId = productId, lines = lines)
        return ServiceOutcome.Success(formula.toDto())
    }

    private fun validatePrice(soldBy: SoldBy, pricePerKgMinor: Long?, priceMinor: Long?): ServiceOutcome.Invalid? = when (soldBy) {
        SoldBy.WEIGHT -> if (pricePerKgMinor == null) {
            ServiceOutcome.Invalid("PRICE_PER_KG_REQUIRED", "pricePerKgMinor is required when soldBy is WEIGHT")
        } else {
            null
        }
        SoldBy.UNIT -> if (priceMinor == null) {
            ServiceOutcome.Invalid("PRICE_REQUIRED", "priceMinor is required when soldBy is UNIT")
        } else {
            null
        }
    }

    private fun validateOrigin(origin: ProductOrigin, soldBy: SoldBy): ServiceOutcome.Invalid? =
        if (origin == ProductOrigin.ALMACEN && soldBy != SoldBy.UNIT) {
            ServiceOutcome.Invalid("ALMACEN_REQUIRES_UNIT", "an ALMACEN product can only be sold by UNIT")
        } else {
            null
        }

    private fun String.toSoldByOrNull(): SoldBy? = runCatching { SoldBy.valueOf(this) }.getOrNull()

    private fun Product.toDto() = ProductDto(
        id = id.toString(),
        name = name,
        productType = productType,
        origin = origin.name,
        soldBy = soldBy.name,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        barcode = barcode,
        imageUrl = imageUrl,
        active = active,
        serverSeq = serverSeq
    )

    private fun Formula.toDto() = FormulaDto(
        id = id.toString(),
        productId = productId.toString(),
        lines = lines.map { FormulaLineDto(it.id.toString(), it.ingredientId.toString(), it.basisPoints) }
    )
}
