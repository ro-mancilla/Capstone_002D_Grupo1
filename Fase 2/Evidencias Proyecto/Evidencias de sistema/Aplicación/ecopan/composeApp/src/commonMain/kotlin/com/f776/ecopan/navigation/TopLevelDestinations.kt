package com.f776.ecopan.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.compose.resources.StringResource

/** One navigation-suite tab: the root of its own back-stack segment, plus how it is labelled. */
internal data class TopLevelDestination(
    val key: NavKey,
    val icon: ImageVector,
    val label: StringResource,
)

/**
 * The tabs this platform's client actually has — the one place the client roles differ.
 *
 * `:composeApp` is a single binary family shared by the desktop till and the baker's phone
 * (01 §1), but they are two *different products* (SPECS.md Client 1 vs Client 2): the till sells
 * and owns the hardware, the phone logs bake batches. A cashier's Caja tab on a phone is not a
 * harmless extra — a phone has no scale, no drawer and no printer, and SPECS.md says the till is
 * the system of record for 100% of sales, so a second place to ring one up contradicts the spec
 * outright.
 *
 * Enforced by the build, not by discipline. `:jvm:feature:pos:*` builds a JVM artifact only and
 * `:mobile:feature:production:*` builds Android and iOS ones only, so neither client has the
 * other's screens on its compile classpath — there is no variant to resolve, and the actuals below
 * *cannot* name them even by mistake. That is what the `:jvm:` and `:mobile:` tiers are for
 * (CLAUDE.md, "client-exclusive feature modules nest under their client's folder").
 */
internal expect val TopLevelDestinations: List<TopLevelDestination>

/**
 * Registers every [NavKey] reachable on this platform with the back stack's serializer.
 *
 * Must cover the [TopLevelDestinations] keys *and* any detail routes pushed on top of them —
 * `rememberNavBackStack` serializes the whole stack, so an unregistered key fails at save time,
 * not at navigate time.
 */
internal expect fun PolymorphicModuleBuilder<NavKey>.platformRoutes()
