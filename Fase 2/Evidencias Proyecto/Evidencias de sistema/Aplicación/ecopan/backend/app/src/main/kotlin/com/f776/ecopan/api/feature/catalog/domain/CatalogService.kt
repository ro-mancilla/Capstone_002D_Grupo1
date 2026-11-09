package com.f776.ecopan.api.feature.catalog.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.catalog.controller.dto.CreateProductRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.ImageUploadGrantDto
import com.f776.ecopan.api.feature.catalog.controller.dto.FormulaDto
import com.f776.ecopan.api.feature.catalog.controller.dto.ProductDto
import com.f776.ecopan.api.feature.catalog.controller.dto.PutFormulaRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.UpdateProductRequest
import kotlin.uuid.Uuid

interface CatalogService {
    suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<ProductDto>
    /**
     * An Almacén product must come with [CreateProductRequest.openingStock] (0 allowed): it is stored as
     * the product's first stock movement, in the request's branch or [principalBranchId].
     */
    suspend fun createProduct(
        tenantId: Uuid,
        request: CreateProductRequest,
        createdBy: Uuid? = null,
        principalBranchId: Uuid? = null
    ): ServiceOutcome<ProductDto>
    suspend fun updateProduct(tenantId: Uuid, productId: Uuid, request: UpdateProductRequest): ServiceOutcome<ProductDto>

    /** Soft delete: `active=false` (02 §3 C4). */
    suspend fun deleteProduct(tenantId: Uuid, productId: Uuid): Boolean

    /**
     * Photo upload, step 1: a short-lived, one-blob, write-only URL the client uploads to
     * directly. Only `image/jpeg`, `image/png` and `image/webp` are granted. Throws
     * `StorageUnavailableException` when the store cannot sign.
     */
    suspend fun grantProductImageUpload(tenantId: Uuid, productId: Uuid, contentType: String): ServiceOutcome<ImageUploadGrantDto>

    /** Step 2: the client reports the key it uploaded; the API checks the blob landed, points the
     * product at its public URL and drops the previous photo. */
    suspend fun confirmProductImage(tenantId: Uuid, productId: Uuid, key: String): ServiceOutcome<ProductDto>

    /** Revoke: delete the blob and clear `imageUrl`. */
    suspend fun deleteProductImage(tenantId: Uuid, productId: Uuid): ServiceOutcome<ProductDto>

    suspend fun listFormulas(tenantId: Uuid): List<FormulaDto>
    suspend fun putFormula(tenantId: Uuid, productId: Uuid, request: PutFormulaRequest): ServiceOutcome<FormulaDto>
}
