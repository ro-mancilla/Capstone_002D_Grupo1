package com.f776.ecopan.api.seed

import at.favre.lib.crypto.bcrypt.BCrypt
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeProductStockRepository
import com.f776.ecopan.api.fakes.FakeSaleRepository
import com.f776.ecopan.api.fakes.FakeSeedRevisionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 02 §9 item 8 AC: booting with `SEED_PROFILE=reference` yields one login-able tenant; idempotent on reboot. */
class SeedLoaderTest {

    private fun newLoader(
        tenants: FakeTenantRepository = FakeTenantRepository(),
        branches: FakeBranchRepository = FakeBranchRepository(),
        users: FakeUserRepository = FakeUserRepository(),
        ingredients: FakeIngredientRepository = FakeIngredientRepository(),
        catalog: FakeCatalogRepository = FakeCatalogRepository(),
        sales: FakeSaleRepository = FakeSaleRepository(),
        revisions: FakeSeedRevisionRepository = FakeSeedRevisionRepository()
    ) = SeedLoader(tenants, branches, users, ingredients, catalog, sales, FakeProductStockRepository(), revisions, "America/Santiago", com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker())

    @Test
    fun `loading reference once yields a login-able tenant with one row per entity`() = runTest {
        val tenants = FakeTenantRepository()
        val branches = FakeBranchRepository()
        val users = FakeUserRepository()
        val ingredients = FakeIngredientRepository()
        val catalog = FakeCatalogRepository()
        val loader = newLoader(tenants, branches, users, ingredients, catalog)

        loader.loadOne("reference")

        assertEquals(1, tenants.tenants.size)
        val tenant = tenants.tenants.values.single()
        assertEquals("reference", tenant.slug)

        assertEquals(2, branches.branches.size)
        assertEquals(5, users.users.size, "one user per role")
        assertEquals(setOf(Role.CASHIER, Role.BAKER, Role.BRANCH_MANAGER, Role.GENERAL_MANAGER, Role.ADMIN), users.users.values.map { it.role }.toSet())
        assertEquals(6, ingredients.ingredients.size)
        assertEquals(8, catalog.products.size)
        assertEquals(5, catalog.formulas.size, "every Panadería product has a formula -- no 'sin fórmula' tiles in the demo")

        // Passwords/PINs are hashed at load time, never stored as plaintext.
        val cashier = users.users.values.single { it.username == "cajero" }
        assertTrue(BCrypt.verifyer().verify("cajero123".toCharArray(), cashier.passwordHash).verified)
        assertTrue(cashier.passwordHash != "cajero123")
        assertTrue(BCrypt.verifyer().verify("1111".toCharArray(), cashier.pinHash!!).verified)
    }

    @Test
    fun `loading reference twice is idempotent -- no duplicate rows, no error`() = runTest {
        val tenants = FakeTenantRepository()
        val branches = FakeBranchRepository()
        val users = FakeUserRepository()
        val ingredients = FakeIngredientRepository()
        val catalog = FakeCatalogRepository()
        val loader = newLoader(tenants, branches, users, ingredients, catalog)

        loader.loadOne("reference")
        loader.loadOne("reference")

        assertEquals(1, tenants.tenants.size)
        assertEquals(2, branches.branches.size)
        assertEquals(5, users.users.size)
        assertEquals(6, ingredients.ingredients.size)
        assertEquals(8, catalog.products.size)
        assertEquals(5, catalog.formulas.size)
    }

    /**
     * The point of the revision gate. An ordinary reboot must leave a hand-edited demo alone --
     * otherwise nothing a reviewer changes survives a restart -- so a price someone adjusted stays
     * adjusted as long as the committed profile has not itself moved on.
     */
    @Test
    fun `re-seeding at the same revision leaves a locally edited product alone`() = runTest {
        val catalog = FakeCatalogRepository()
        val revisions = FakeSeedRevisionRepository()
        val loader = newLoader(catalog = catalog, revisions = revisions)

        loader.loadOne("reference")
        val edited = catalog.products.values.first()
        catalog.updateProduct(id = edited.id, name = "Precio de la demo", priceMinor = 99_999)

        loader.loadOne("reference")

        assertEquals("Precio de la demo", catalog.products.getValue(edited.id).name)
        assertEquals(99_999, catalog.products.getValue(edited.id).priceMinor)
    }

    /**
     * The other half: an edit to the *committed* profile has to be able to reach a database that
     * was seeded before it. Without this, a change to `reference.json` shipped to new installs only
     * and every developer already running the stack kept the catalog they started with, with no way
     * to pick it up short of dropping the volume.
     */
    @Test
    fun `bumping the revision rewrites what is already there`() = runTest {
        val catalog = FakeCatalogRepository()
        val revisions = FakeSeedRevisionRepository()
        val loader = newLoader(catalog = catalog, revisions = revisions)

        loader.loadOne("reference")
        val seeded = catalog.products.values.first()
        catalog.updateProduct(id = seeded.id, name = "Se irá")

        // Stand in for a committed edit: the profile on disk now claims a revision this database
        // has not applied.
        revisions.applied["reference"] = 0
        loader.loadOne("reference")

        assertEquals(seeded.name, catalog.products.getValue(seeded.id).name)
        assertEquals(rawProfile("reference").revision, revisions.applied.getValue("reference"))
    }

    /**
     * The reference profile doubles as the run-it-yourself demo tenant (see README), and the app is
     * Spanish-only (`composeResources/values/strings.xml` has no other locale). Seeded *data* is
     * displayed verbatim -- branch names in the header, ingredient names in Stock Glance,
     * `productType` as the batch-history label -- so English here would surface as English in a
     * Spanish UI no matter how well the string resources are translated.
     */
    @Test
    fun `reference profile ships display data in Spanish`() = runTest {
        val branches = FakeBranchRepository()
        val ingredients = FakeIngredientRepository()
        val catalog = FakeCatalogRepository()
        newLoader(branches = branches, ingredients = ingredients, catalog = catalog).loadOne("reference")

        assertEquals(
            setOf("Sucursal Principal", "Sucursal Providencia"),
            branches.branches.values.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("Harina", "Agua", "Sal", "Levadura", "Manteca", "Azúcar"),
            ingredients.ingredients.values.map { it.name }.toSet(),
        )
        assertEquals(
            setOf("Pan", "Pastelería", "Almacén"),
            catalog.products.values.map { it.productType }.toSet(),
            "productType is shown verbatim as the batch-history label, so it must be Spanish too",
        )
    }

    @Test
    fun `unknown profile name is logged and skipped without throwing`() = runTest {
        val loader = newLoader()
        loader.loadOne("does-not-exist-${Uuid.random()}")
        // no assertion beyond "doesn't throw" -- the AC is graceful skip, not a crash.
    }

    @Test
    fun `unipan profile is unit-sold-only, three-branch, formulas and forecasting off`() = runTest {
        val tenants = FakeTenantRepository()
        val branches = FakeBranchRepository()
        val users = FakeUserRepository()
        val ingredients = FakeIngredientRepository()
        val catalog = FakeCatalogRepository()
        val stock = FakeProductStockRepository()
        val loader = SeedLoader(tenants, branches, users, ingredients, catalog, FakeSaleRepository(), stock, FakeSeedRevisionRepository(), "America/Santiago", com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker())

        loader.loadOne("unipan")

        val almacen = catalog.products.values.filter { it.origin == com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN }.map { it.id }.toSet()
        assertEquals(
            almacen.flatMap { id -> branches.branches.keys.map { id to it } }.toSet(),
            stock.movements.values.map { it.productId to it.branchId }.toSet(),
            "every Almacén product has an opening count in every branch"
        )

        val tenant = tenants.tenants.values.single()
        assertEquals("unipan", tenant.slug)
        assertTrue(com.f776.ecopan.data.domain.Module.INVENTORY_FORMULAS !in tenant.enabledModules)
        assertTrue(com.f776.ecopan.data.domain.Module.DEMAND_FORECASTING !in tenant.enabledModules)
        assertTrue(com.f776.ecopan.data.domain.Module.POS_SALES in tenant.enabledModules)
        assertTrue(com.f776.ecopan.data.domain.Module.MULTI_BRANCH_ROLES in tenant.enabledModules)
        assertTrue(com.f776.ecopan.data.domain.Module.ANALYTICS_DASHBOARD in tenant.enabledModules)

        assertEquals(3, branches.branches.size, "Norte/Centro/Sur -- exercises MULTI_BRANCH_ROLES for real")
        assertEquals(0, ingredients.ingredients.size, "no scale/formula flow -- no ingredients seeded at all")
        assertEquals(0, catalog.formulas.size)

        assertEquals(4, catalog.products.size)
        assertTrue(catalog.products.values.all { it.soldBy == com.f776.ecopan.data.database.entity.SoldBy.UNIT }, "every product is UNIT-sold -- no scale anywhere in this tenant's flow")
        assertTrue(catalog.products.values.all { it.barcode != null }, "every product carries a barcode for the POS resale path")
        assertEquals(
            mapOf(
                "Pan de Molde" to com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN,
                "Cookie" to com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN,
                "Bagel" to com.f776.ecopan.data.database.entity.ProductOrigin.PANADERIA,
                "Sandwich" to com.f776.ecopan.data.database.entity.ProductOrigin.PANADERIA
            ),
            catalog.products.values.associate { it.name to it.origin },
            "packaged goods are Almacén, what the shop bakes or assembles is Panadería"
        )

        val roles = users.users.values.map { it.role }
        assertEquals(1, roles.count { it == Role.GENERAL_MANAGER })
        assertEquals(3, roles.count { it == Role.BRANCH_MANAGER }, "one BRANCH_MANAGER per branch")
        assertTrue(Role.BAKER !in roles, "resale-style shop needs no BAKER users")
    }

    @Test
    fun `reference and unipan coexist idempotently when both are seeded together`() = runTest {
        val tenants = FakeTenantRepository()
        val branches = FakeBranchRepository()
        val users = FakeUserRepository()
        val ingredients = FakeIngredientRepository()
        val catalog = FakeCatalogRepository()
        val loader = newLoader(tenants, branches, users, ingredients, catalog)

        loader.loadAll(listOf("reference", "unipan"))
        loader.loadAll(listOf("reference", "unipan"))

        assertEquals(2, tenants.tenants.size, "SEED_PROFILE=reference,unipan seeds both tenants, re-running is a no-op")
        assertEquals(5, branches.branches.size)
        assertEquals(13, users.users.size)
        assertEquals(6, ingredients.ingredients.size, "unipan contributes none")
        assertEquals(12, catalog.products.size)
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun rawProfile(name: String): SeedProfile =
        json.decodeFromString(javaClass.classLoader.getResourceAsStream("seed/$name.json")!!.bufferedReader().readText())

    /** Regression guard: an Almacén product with no count for a branch is untracked there, silently. */
    @Test
    fun `every committed profile gives each Almacen product an opening count in every branch and a canonical Tipo`() {
        listOf("reference", "unipan").forEach { name ->
            val profile = rawProfile(name)
            val branchIds = profile.branches.map { it.id }.toSet()
            profile.products.forEach { product ->
                val kind = com.f776.ecopan.data.domain.ProductKind.fromName(product.productType)
                assertTrue(kind != null, "$name/${product.name}: productType '${product.productType}' is not Pan, Pastelería or Almacén")
                assertEquals(product.productType, kind.label, "$name/${product.name}: use the canonical spelling")
                if (kind.origin == ProductOrigin.ALMACEN) {
                    assertEquals("UNIT", product.soldBy, "$name/${product.name}: Almacén is unit-sold")
                    assertEquals(
                        branchIds, product.openingCounts.map { it.branchId }.toSet(),
                        "$name/${product.name}: needs an opening count for every branch"
                    )
                    assertTrue(product.openingCounts.all { it.units >= 0 }, "$name/${product.name}: negative opening count")
                    assertTrue(profile.formulas.none { it.productId == product.id }, "$name/${product.name}: Almacén has no formula")
                } else {
                    assertTrue(product.openingCounts.isEmpty(), "$name/${product.name}: Panadería has no product stock")
                }
            }
        }
    }

    /** The demo's ok -> low -> out story: one product starts low, one has room to be sold down live. */
    @Test
    fun `reference ships Almacen products for the till badge demo`() = runTest {
        val catalog = FakeCatalogRepository()
        val stock = FakeProductStockRepository()
        SeedLoader(FakeTenantRepository(), FakeBranchRepository(), FakeUserRepository(), FakeIngredientRepository(), catalog,
            FakeSaleRepository(), stock, FakeSeedRevisionRepository(), "America/Santiago", com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker()).loadOne("reference")

        val almacen = catalog.products.values.filter { it.origin == ProductOrigin.ALMACEN }
        assertEquals(3, almacen.size)
        assertTrue(almacen.all { it.lowStockThresholdUnits != null && it.barcode != null })
        assertEquals(almacen.size * 2, stock.movements.size, "one opening count per product per branch")
        val startsLow = rawProfile("reference").products.any { p ->
            p.openingCounts.any { it.units <= (p.lowStockThresholdUnits ?: -1) }
        }
        assertTrue(startsLow, "at least one product shows the low badge from the first screen")
    }

    @Test
    fun `reference seeds Panaderia and Almacen products`() = runTest {
        val catalog = FakeCatalogRepository()
        newLoader(catalog = catalog).loadAll(listOf("reference"))
        assertEquals(5, catalog.products.values.count { it.origin == ProductOrigin.PANADERIA })
        assertEquals(3, catalog.products.values.count { it.origin == ProductOrigin.ALMACEN })
    }
}
