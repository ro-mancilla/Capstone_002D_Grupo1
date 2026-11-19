package com.f776.ecopan.core.domain.catalog

/**
 * How a product comes to be sold (spec #22). [PANADERIA] is baked by the bakery: it has a formula and
 * production batches. [ALMACEN] is bought finished and resold: never a formula, never a batch.
 * Mirrors the backend's `ProductOrigin` wire values.
 */
enum class ProductOrigin { PANADERIA, ALMACEN }
