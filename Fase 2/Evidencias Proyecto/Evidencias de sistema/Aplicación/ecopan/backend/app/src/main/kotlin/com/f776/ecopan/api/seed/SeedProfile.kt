package com.f776.ecopan.api.seed

import kotlinx.serialization.Serializable

/**
 * On-disk shape of `seed/<name>.json` (02 §9 item 8). Plaintext `password`/`pin` fields are hashed
 * at load time with the same bcrypt path as `AuthService` -- **never** store a hash in this file,
 * it stays human-readable. `11-seeds-and-demo.md` owns authoring the full demo profile set; this
 * plan authors exactly one (`reference.json`) to prove the loader mechanism.
 */
@Serializable
data class SeedProfile(
    /**
     * Bump this whenever you change anything else in the file.
     *
     * A profile is otherwise applied create-if-absent, so an edit reaches only databases that have
     * never been seeded — every developer already running the stack keeps the catalog they started
     * with. Raising the revision tells `SeedLoader` to write this profile's configuration rows over
     * what is there, once, on the next boot. Sales history is untouched, and a database already at
     * this revision is left exactly alone, so hand-made demo edits survive ordinary restarts.
     */
    val revision: Int = 1,
    val tenant: SeedTenant,
    val branches: List<SeedBranch> = emptyList(),
    val users: List<SeedUser> = emptyList(),
    val ingredients: List<SeedIngredient> = emptyList(),
    val products: List<SeedProduct> = emptyList(),
    val formulas: List<SeedFormula> = emptyList()
)

@Serializable
data class SeedTenant(
    val id: String,
    val slug: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: List<String> = listOf("POS_SALES")
)

@Serializable
data class SeedBranch(val id: String, val name: String)

@Serializable
data class SeedUser(
    val id: String,
    val branchId: String? = null,
    val username: String,
    /** Plaintext -- hashed at load time (never stored as a hash in the seed file itself). */
    val password: String,
    /** Plaintext numeric PIN, optional -- hashed at load time. */
    val pin: String? = null,
    val displayName: String,
    val role: String
)

@Serializable
data class SeedIngredient(val id: String, val name: String, val lowStockThresholdGrams: Long? = null)

@Serializable
data class SeedProduct(
    val id: String,
    val name: String,
    /** Pan, Pastelería or Almacén; the origin is derived from it. */
    val productType: String,
    val soldBy: String,
    val pricePerKgMinor: Long? = null,
    val priceMinor: Long? = null,
    val barcode: String? = null,
    val imageUrl: String? = null,
    /** Reorder level in units (Almacén only): the till and the web flag stock at or under it. */
    val lowStockThresholdUnits: Long? = null,
    /** Starting shelf count per branch; set for every ALMACEN product, which is untracked without one. */
    val openingCounts: List<SeedOpeningCount> = emptyList()
)

@Serializable
data class SeedOpeningCount(val branchId: String, val units: Long)

@Serializable
data class SeedFormulaLine(val ingredientId: String, val basisPoints: Int)

@Serializable
data class SeedFormula(val productId: String, val lines: List<SeedFormulaLine>)
