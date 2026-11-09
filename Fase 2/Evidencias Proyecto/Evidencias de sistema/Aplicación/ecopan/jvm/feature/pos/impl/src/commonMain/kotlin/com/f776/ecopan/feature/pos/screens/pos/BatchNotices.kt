package com.f776.ecopan.feature.pos.screens.pos

import androidx.compose.runtime.Composable
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_branch
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_full
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_many
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_mass
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_names_more
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_names_two
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_nothing
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_plain
import ecopan.jvm.feature.pos.impl.generated.resources.pos_notice_product
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Batches that become ready within this long of the first one share a single notice. */
internal val BATCH_GROUP_WINDOW: Duration = 10.seconds

/** What the till knows about one ready batch. Anything unknown is `null` and simply left out of the words. */
internal data class BatchFact(val productName: String?, val massGrams: Long?, val branchName: String?)

/** One dismissible notice: a single batch, or several that arrived within [BATCH_GROUP_WINDOW]. */
internal data class BatchNoticeUi(val id: Uuid, val firstReadyAt: Instant, val batches: List<BatchFact>)

/** A sentence of the copy deck as a resource plus its arguments; an argument may itself be a [PosCopy]. */
internal class PosCopy(val resource: StringResource, val args: List<Any> = emptyList())

internal class NoticeText(val title: PosCopy, val supporting: List<PosCopy>)

/**
 * Adds a batch to the notices: it joins the newest notice when it became ready within
 * [BATCH_GROUP_WINDOW] of that notice's first batch, otherwise it starts a notice of its own.
 */
internal fun List<BatchNoticeUi>.withBatch(batchId: Uuid, readyAt: Instant, fact: BatchFact): List<BatchNoticeUi> {
    val last = lastOrNull()
    return if (last != null && readyAt - last.firstReadyAt <= BATCH_GROUP_WINDOW && last.firstReadyAt - readyAt <= BATCH_GROUP_WINDOW) {
        dropLast(1) + last.copy(batches = last.batches + fact)
    } else {
        this + BatchNoticeUi(id = batchId, firstReadyAt = readyAt, batches = listOf(fact))
    }
}

/**
 * The words of a notice (copy deck "Aviso de lote"). Never an identifier, and a part nobody knows
 * is omitted rather than filled in:
 * - product and branch known: "Lote de Marraqueta listo · 12 kg de masa" / "Sucursal Principal"
 * - only the branch: "Lote listo en Sucursal Principal"
 * - nothing: "Hay un lote nuevo listo"
 * - several: "3 lotes nuevos listos" / "Sucursal Principal · Marraqueta, Hallulla y 1 más"
 */
internal fun BatchNoticeUi.text(): NoticeText {
    if (batches.size > 1) {
        val branch = batches.firstNotNullOfOrNull { it.branchName.knownName() }
        val names = batches.mapNotNull { it.productName.knownName() }.distinct()
        return NoticeText(
            title = PosCopy(Res.string.pos_notice_many, listOf(batches.size)),
            supporting = listOfNotNull(branch?.let(::plain), namesCopy(names)),
        )
    }
    val fact = batches.single()
    val product = fact.productName.knownName()
    val branch = fact.branchName.knownName()
    val mass = fact.massGrams?.takeIf { it > 0 }?.let { PosCopy(Res.string.pos_notice_mass, listOf(formatMassKg(it))) }
    return when {
        product != null -> NoticeText(
            title = if (mass != null) PosCopy(Res.string.pos_notice_full, listOf(product, mass)) else PosCopy(Res.string.pos_notice_product, listOf(product)),
            supporting = listOfNotNull(branch?.let(::plain)),
        )

        branch != null -> NoticeText(
            title = PosCopy(Res.string.pos_notice_branch, listOf(branch)),
            supporting = listOfNotNull(mass),
        )

        else -> NoticeText(title = PosCopy(Res.string.pos_notice_nothing), supporting = listOfNotNull(mass))
    }
}

private fun plain(text: String) = PosCopy(Res.string.pos_notice_plain, listOf(text))

/** "Marraqueta", "Marraqueta y Hallulla", "Marraqueta, Hallulla y 1 más"; nothing when no name is known. */
private fun namesCopy(names: List<String>): PosCopy? = when (names.size) {
    0 -> null
    1 -> plain(names.first())
    2 -> PosCopy(Res.string.pos_notice_names_two, names)
    else -> PosCopy(Res.string.pos_notice_names_more, listOf(names[0], names[1], names.size - 2))
}

/** A name worth showing: not blank, and not a UUID that leaked in place of a name. */
private fun String?.knownName(): String? = this?.trim()?.takeIf { it.isNotEmpty() && Uuid.parseOrNull(it) == null }

/** `12000` -> `12`, `12500` -> `12,5`, `450` -> `0,45`: kilos trimmed to what is there, comma decimal. */
internal fun formatMassKg(grams: Long): String {
    val whole = grams / 1000
    val fraction = (grams % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole,$fraction"
}

@Composable
internal fun PosCopy.text(): String = stringResource(resource, *args.map { if (it is PosCopy) it.text() else it }.toTypedArray())

internal suspend fun PosCopy.resolve(): String =
    getString(resource, *args.map { if (it is PosCopy) it.resolve() else it }.toTypedArray())
