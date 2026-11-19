package com.f776.ecopan.api.feature.catalog.controller.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val productType: String,
    /** Derived from [productType] (ALMACEN iff Almacén, else PANADERIA); read-only. */
    val origin: String,
    val soldBy: String,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    val imageUrl: String?,
    val active: Boolean,
    val serverSeq: Long
)

@Serializable
data class CreateProductRequest(
    val id: String,
    val name: String,
    /** Pan, Pastelería or Almacén: the single classification; the origin is derived from it. */
    val productType: String,
    val soldBy: String,
    val pricePerKgMinor: Long? = null,
    val priceMinor: Long? = null,
    val barcode: String? = null,
    /** Almacén only, and required there (0 allowed): units on the shelf now, recorded as the branch's opening count. */
    val openingStock: Long? = null,
    /** Branch the opening count belongs to; defaults to the caller's own branch, required for an all-branch caller. */
    val openingBranchId: String? = null
)

/** `imageUrl` is absent on purpose: a photo is set through the upload-grant flow
 * (`POST .../{id}/image/upload-url` -> client PUTs to storage -> `PUT .../{id}/image`) and
 * cleared by `DELETE .../{id}/image`, never by pasting a URL -- the API owns where pictures live. */
@Serializable
data class UpdateProductRequest(
    val name: String,
    /** Pan, Pastelería or Almacén: the single classification; the origin is derived from it. */
    val productType: String,
    val soldBy: String,
    val pricePerKgMinor: Long? = null,
    val priceMinor: Long? = null,
    val barcode: String? = null
)

@Serializable
data class FormulaLineDto(val id: String? = null, val ingredientId: String, val basisPoints: Int)

@Serializable
data class FormulaDto(val id: String, val productId: String, val lines: List<FormulaLineDto>)

@Serializable
data class PutFormulaRequest(val id: String? = null, val lines: List<FormulaLineDto>)

@Serializable
data class ImageUploadGrantRequest(val contentType: String)

/** Client contract: `PUT uploadUrl` with the file as the body, headers `x-ms-blob-type: BlockBlob`
 * and `Content-Type: <contentType>`, before `expiresAt`; then `PUT .../image` with [key]. */
@Serializable
data class ImageUploadGrantDto(val key: String, val uploadUrl: String, val expiresAt: Long, val contentType: String)

@Serializable
data class ConfirmImageRequest(val key: String)
