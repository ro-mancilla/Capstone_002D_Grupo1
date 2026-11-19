package com.f776.ecopan.api.seed

import at.favre.lib.crypto.bcrypt.BCrypt
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.SaleRepository
import com.f776.ecopan.data.database.repository.SeedRevisionRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.ProductKind
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import com.f776.ecopan.data.domain.Role
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Boot-time-only seed loader (01 §7, 02 §9 item 8): `SEED_PROFILE` is a comma-separated list of
 * profile names, each read from `seed/<name>.json` on the classpath and upserted **idempotently by
 * id** -- safe to run on every boot (demo reset = restart, per 01 §7). No admin seed endpoint, no
 * CLI path exists; this is the only entry point.
 *
 * Idempotency here is create-if-absent: a row already present (by client-minted id) is left alone
 * rather than re-written, so a second load of the same profile is a no-op (verified in
 * `SeedLoaderTest`). That is deliberate — a reference tenant that rewrote its own prices on every
 * boot would be useless to demo against, because nothing a reviewer changed would survive a
 * restart.
 *
 * The cost of that rule is that an *edit to the committed profile* reached new installs only.
 * Every developer who had already run `docker compose up` kept the catalog they were first seeded
 * with, and the only way to pick up a change was to drop the volume and lose their demo state with
 * it. [SeedProfile.revision] is the escape hatch: bump it in the JSON and the next boot re-applies
 * that profile's configuration rows over whatever is there, once. Sales history is never touched.
 * */
class SeedLoader(
    private val tenantRepository: TenantRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
    private val ingredientRepository: IngredientRepository,
    private val catalogRepository: CatalogRepository,
    private val saleRepository: SaleRepository,
    private val productStockRepository: ProductStockRepository,
    private val seedRevisionRepository: SeedRevisionRepository,
    /** Same zone the analytics and forecast paths bucket by (02 §3) -- see `Settings.reportingTz`. */
    private val reportingTz: String,
    /**
     * Where the post-seed forecast refresh is requested (issue #73). The worker's own daily
     * trigger fires when *it* starts, which on a fresh stack can precede this loader's sales
     * insert and store a forecast of zeros; asking again once the history is really there is
     * order-independent, unlike any sleep or start-order guarantee.
     */
    private val forecastRefreshBroker: ForecastRefreshBroker
) {
    private val logger = LoggerFactory.getLogger(SeedLoader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /** Loads and applies every named profile, in order. Missing resources are logged and skipped. */
    suspend fun loadAll(profileNames: List<String>) {
        profileNames.forEach { name -> loadOne(name) }
    }

    suspend fun loadOne(name: String) {
        val resource = javaClass.classLoader.getResourceAsStream("seed/$name.json")
        if (resource == null) {
            logger.warn("SEED_PROFILE '$name' requested but seed/$name.json was not found on the classpath")
            return
        }
        val profile = json.decodeFromString<SeedProfile>(resource.bufferedReader().readText())

        val recorded = seedRevisionRepository.appliedRevision(name)
        // No marker = a fresh database: the creates below apply the profile in full, nothing to refresh.
        val applied = recorded ?: profile.revision
        val refreshExisting = applied < profile.revision

        apply(profile, refreshExisting)
        seedRevisionRepository.recordApplied(name, profile.revision)
        if (refreshExisting) {
            logger.info("Seed profile '$name' refreshed from revision $applied to ${profile.revision}")
        } else {
            logger.info("Seed profile '$name' applied (idempotent, revision ${profile.revision})")
        }
        applySalesHistory(name, profile)
    }

    /**
     * [refreshExisting] decides what happens to a row that is already there: leave it (the normal
     * boot, so hand-made demo edits survive a restart) or overwrite it from the profile (a revision
     * bump). Formulas are upserts by `(tenant, product)` in the repository itself, so they are
     * re-put either way -- writing the same values back is free.
     */
    private suspend fun apply(profile: SeedProfile, refreshExisting: Boolean) {
        val tenantId = Uuid.parse(profile.tenant.id)
        val enabledModules = profile.tenant.enabledModules
            .mapNotNull { id ->
                runCatching { Module.valueOf(id) }.getOrNull()
                    .also { if (it == null) logger.warn("Seed profile '${profile.tenant.slug}' lists unknown module '$id'; skipped") }
            }
            .toSet()
        if (tenantRepository.findById(tenantId) == null) {
            tenantRepository.create(
                id = tenantId,
                slug = profile.tenant.slug,
                name = profile.tenant.name,
                currencyCode = profile.tenant.currencyCode,
                enabledModules = enabledModules
            )
        } else if (refreshExisting) {
            // Modules only: the slug is what devices enrolled against and what users type to log
            // in, so re-pointing it underneath a running demo would lock everyone out for the sake
            // of a cosmetic rename.
            tenantRepository.updateEnabledModules(tenantId, enabledModules)
        }

        profile.branches.forEach { branch ->
            val id = Uuid.parse(branch.id)
            if (branchRepository.findById(id) == null) {
                branchRepository.create(id, tenantId, branch.name)
            } else if (refreshExisting) {
                branchRepository.update(id, name = branch.name)
            }
        }

        profile.users.forEach { user ->
            val id = Uuid.parse(user.id)
            if (userRepository.findById(id) == null) {
                userRepository.create(
                    id = id,
                    tenantId = tenantId,
                    branchId = user.branchId?.let { Uuid.parse(it) },
                    username = user.username,
                    passwordHash = hash(user.password),
                    pinHash = user.pin?.let { hash(it) },
                    displayName = user.displayName,
                    role = Role.valueOf(user.role)
                )
            } else if (refreshExisting) {
                // Credentials included: the seed file is the documented source of the demo
                // passwords (README §"Cuentas"), so a profile that changes one has to actually
                // change it, or the README and the running system disagree.
                userRepository.update(
                    id = id,
                    passwordHash = hash(user.password),
                    pinHash = user.pin?.let { hash(it) },
                    displayName = user.displayName,
                    role = Role.valueOf(user.role),
                    branchId = user.branchId?.let { Uuid.parse(it) },
                    clearBranchId = user.branchId == null
                )
            }
        }

        profile.ingredients.forEach { ingredient ->
            val id = Uuid.parse(ingredient.id)
            if (ingredientRepository.findById(id) == null) {
                ingredientRepository.create(id, tenantId, ingredient.name, ingredient.lowStockThresholdGrams)
            } else if (refreshExisting) {
                ingredientRepository.update(
                    id = id,
                    name = ingredient.name,
                    lowStockThresholdGrams = ingredient.lowStockThresholdGrams
                )
            }
        }

        profile.products.forEach { product ->
            val id = Uuid.parse(product.id)
            val kind = ProductKind.fromName(product.productType)
                ?: error("Seed product '${product.name}' has productType '${product.productType}'; expected Pan, Pastelería or Almacén")
            val existing = catalogRepository.findProductById(id)
            if (existing == null) {
                catalogRepository.createProduct(
                    id = id,
                    tenantId = tenantId,
                    name = product.name,
                    productType = kind.label,
                    soldBy = SoldBy.valueOf(product.soldBy),
                    pricePerKgMinor = product.pricePerKgMinor,
                    priceMinor = product.priceMinor,
                    barcode = product.barcode,
                    imageUrl = product.imageUrl,
                    origin = kind.origin
                )
            } else if (refreshExisting) {
                // `updateProduct` reads null as "leave alone", so a field the profile omits keeps
                // whatever the row has. That is the right reading for a partial edit and the wrong
                // one for *clearing* a price when a product changes how it is sold -- rare enough
                // in a demo profile to leave to a volume reset rather than widen the repository API.
                catalogRepository.updateProduct(
                    id = id,
                    name = product.name,
                    productType = kind.label,
                    soldBy = SoldBy.valueOf(product.soldBy),
                    pricePerKgMinor = product.pricePerKgMinor,
                    priceMinor = product.priceMinor,
                    barcode = product.barcode,
                    imageUrl = product.imageUrl,
                    origin = kind.origin
                )
            }
            // The reorder level has its own writer (it never travels on the sync wire). Applied only
            // when the row was just created or is being refreshed, so a hand-edited level survives.
            if (existing == null || refreshExisting) {
                product.lowStockThresholdUnits?.let { catalogRepository.setProductLowStockThreshold(id, it) }
            }
        }

        // Almacén stock needs an opening count to be tracked at all. The id is derived from
        // (product, branch), so appending is idempotent and a restart never counts the shelf twice.
        profile.products.forEach { product ->
            product.openingCounts.forEach { count ->
                val movementId = Uuid.parse(product.id).toLongs { msb, lsb ->
                    Uuid.fromLongs(msb xor Uuid.parse(count.branchId).toLongs { b, _ -> b }, lsb)
                }
                productStockRepository.append(
                    id = movementId,
                    tenantId = tenantId,
                    branchId = Uuid.parse(count.branchId),
                    productId = Uuid.parse(product.id),
                    deltaUnits = count.units,
                    kind = ProductStockMovementKind.OPENING_COUNT,
                    note = null,
                    createdBy = null,
                    occurredAt = Clock.System.now()
                )
            }
        }

        profile.formulas.forEach { formula ->
            val productId = Uuid.parse(formula.productId)
            val exists = catalogRepository.findFormulaByProduct(tenantId, productId) != null
            if (!exists || refreshExisting) {
                catalogRepository.putFormula(
                    id = Uuid.random(),
                    tenantId = tenantId,
                    productId = productId,
                    lines = formula.lines.map { FormulaLine(Uuid.random(), Uuid.parse(it.ingredientId), it.basisPoints) }
                )
            }
        }

    }

    /**
     * Load `seed/<name>-sales.json` if present and expand it into sales (plan 11 SEED-2).
     *
     * Optional by design: a profile without a history file seeds configuration only, exactly as
     * before. The file is separate from the profile because it is machine-generated and ~1200 rows
     * -- keeping it out of `<name>.json` is what lets that file stay hand-editable.
     */
    private suspend fun applySalesHistory(name: String, profile: SeedProfile) {
        val resource = javaClass.classLoader.getResourceAsStream("seed/$name-sales.json") ?: return
        val document = json.decodeFromString<SeedSalesDocument>(resource.bufferedReader().readText())
        if (document.rows.isEmpty()) return

        val tenantId = Uuid.parse(profile.tenant.id)
        val productsById = profile.products
            .mapNotNull { seed -> catalogRepository.findProductById(Uuid.parse(seed.id)) }
            .associateBy { it.id }

        // A sale needs a cashier FK. Prefer one scoped to the branch; fall back to a tenant-wide
        // user, because the reference profile only staffs one of its two branches and the other
        // would otherwise silently seed nothing.
        val tenantWide = profile.users.firstOrNull { it.branchId == null }?.id?.let { Uuid.parse(it) }
        val cashierByBranch = profile.branches.mapNotNull { branch ->
            val branchId = Uuid.parse(branch.id)
            val scoped = profile.users
                .firstOrNull { it.branchId == branch.id && it.role == Role.CASHIER.name }
                ?.id?.let { Uuid.parse(it) }
            (scoped ?: tenantWide)?.let { branchId to it }
        }.toMap()

        val zone = ZoneId.of(reportingTz)
        val today = java.time.LocalDate.now(zone).toKotlinLocalDate()
        val sales = SeedSalesExpander.expand(document, tenantId, productsById, cashierByBranch, today, zone)
        if (sales.isEmpty()) return

        val inserted = saleRepository.createAllWithLines(sales)
        logger.info(
            "Seed profile '{}': sales history {} of {} sales inserted ({} already present)",
            name, inserted, sales.size, sales.size - inserted
        )
        // Only when rows actually landed: a restart of a seeded stack inserts 0 and so requests nothing.
        if (inserted > 0) requestForecastRefresh(name, profile)
    }

    /**
     * One request per branch of a forecasting tenant, published strictly *after* the sales insert
     * committed, so the consumer cannot read an incomplete history. Best effort: a broker that is
     * down must not stop the API booting; the daily run or *Actualizar pronóstico* still covers it.
     */
    private suspend fun requestForecastRefresh(name: String, profile: SeedProfile) {
        // The stored modules, not the profile's: an operator may have enabled forecasting on the tenant since.
        val forecasting = tenantRepository.findById(Uuid.parse(profile.tenant.id))?.enabledModules.orEmpty()
        if (Module.DEMAND_FORECASTING !in forecasting) return
        profile.branches.forEach { branch ->
            try {
                forecastRefreshBroker.publish(ForecastRefreshRequested(tenantId = profile.tenant.id, branchId = branch.id))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Seed profile '{}': could not request forecast refresh for branch {}", name, branch.id, e)
            }
        }
    }

    private fun hash(plaintext: String): String = BCrypt.withDefaults().hashToString(12, plaintext.toCharArray())
}
