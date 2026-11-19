package com.f776.ecopan.data.domain

import com.f776.ecopan.data.database.entity.ProductOrigin
import java.text.Normalizer

/**
 * The comparison key of a product type name: case-, accent- and spacing-insensitive, and `_` / `-`
 * read as a space, so "Pan", "PAN", " pan " and "Pastelería" / "PASTELERIA" / "pasteleria" each
 * collapse to one key, and a code-like seed value ("SLICED_BREAD") equals its readable spelling.
 * Uniqueness per tenant, analytics series and forecast series are all decided on this key, never on
 * the display name.
 */
object ProductTypeKey {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[\\s_\\-]+")

    fun of(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(separators, " ")
            .trim()
}

/**
 * The product's one classification: a fixed, code-owned list (no tenant-created types). [label] is
 * the canonical display name stored in `products.product_type` and carried on the sync wire.
 * [origin] is derived from it, never chosen separately: Almacén is bought-in stock tracked on the
 * product; Pan and Pastelería are baked (formula, batches).
 */
enum class ProductKind(val label: String, val origin: ProductOrigin) {
    PAN("Pan", ProductOrigin.PANADERIA),
    PASTELERIA("Pastelería", ProductOrigin.PANADERIA),
    ALMACEN("Almacén", ProductOrigin.ALMACEN);

    companion object {
        /** The kind whose label [raw] spells (case-, accent- and spacing-insensitive), or null. */
        fun fromName(raw: String): ProductKind? {
            val key = ProductTypeKey.of(raw)
            return entries.firstOrNull { ProductTypeKey.of(it.label) == key }
        }
    }
}
