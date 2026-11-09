package com.f776.ecopan.feature.pos.screens.pos

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.money.lineTotalForWeight
import com.f776.ecopan.domain.hardware.PaperWidth
import kotlin.uuid.Uuid

/** Sentinel category key, never shown directly — the screen renders it via `pos_category_all`. */
internal const val ALL_CATEGORY = ""

internal data class PosUiState(
    val loading: Boolean = true,
    val categories: List<PosCategoryUi> = listOf(PosCategoryUi(ALL_CATEGORY, 0)),
    val selectedCategory: String = ALL_CATEGORY,
    val searchQuery: String = "",
    val products: List<PosProductUi> = emptyList(),
    /** Lines and their total, together — see [Cart]. */
    val cart: Cart = Cart(),
    val weighPanel: WeighPanelUi? = null,
    val scaleMode: ScaleMode = ScaleMode.DISCONNECTED,
    val paymentDialog: PaymentDialogUi? = null,
    val receipt: ReceiptUi? = null,
    val reconnect: ReconnectUi? = null,
    val tillOpen: Boolean = false,
    val tillSales: List<TillSaleUi> = emptyList(),
    /** Dismissible "lote listo" notices, oldest first (05 §3 item 9, spec 3d). */
    val batchNotices: List<BatchNoticeUi> = emptyList(),
    /** 03 §6.1 -- must be visible at all times on the POS sale screen. */
    val syncIndicator: SyncIndicatorState = SyncIndicatorState.OnlineIdle,
)

/**
 * Payment is a dialog/pane within POS, not a NavKey (05 §2 — "keeps cart state in one ViewModel
 * and back-gesture semantics trivial"). `method == null` shows the method picker.
 */
internal data class PaymentDialogUi(
    val totalMinor: Long,
    val method: PaymentMethod? = null,
    val cashTenderedText: String = "",
    val cardPhase: CardAuthPhase = CardAuthPhase.IDLE,
    val declineReason: String? = null,
)

internal enum class CardAuthPhase { IDLE, AUTHORIZING, APPROVED, DECLINED }

/** Whether the cash drawer was told to open for this sale, and whether that drawer is a demo. */
internal enum class DrawerNote { None, Opened, OpenedSimulated }

/**
 * The non-cash counterpart of [DrawerNote]: what happened to the money for a card, transfer or other
 * sale, and whether the card terminal is a demo. Cash is [None] because its drawer note says it.
 */
internal enum class PaymentNote { None, CardApproved, CardApprovedSimulated, TransferRecorded, OtherRecorded }

/**
 * The boleta after a sale is recorded. [receiptNumber] is the till's own "Venta N.º 0001" and is the
 * only identifier shown anywhere; [saleId] is kept for the data layer and never rendered.
 * [paperLines] are exactly the lines the printer receives for [paperWidth], so the preview cannot
 * differ from the paper; [paperSimulated] puts the "SIMULADO" strips on it. A sale whose print
 * failed stays recorded: [printFailed] only offers "Reimprimir".
 * [cashTenderedMinor]/[changeMinor] are null for every method except cash.
 */
internal data class ReceiptUi(
    val saleId: Uuid,
    val receiptNumber: Int,
    val timeLabel: String,
    val lines: List<PosCartLineUi>,
    val totalMinor: Long,
    val paymentMethod: PaymentMethod,
    val cashTenderedMinor: Long? = null,
    val changeMinor: Long? = null,
    val paperLines: List<String> = emptyList(),
    val paperWidth: PaperWidth = PaperWidth.Mm58,
    val paperSimulated: Boolean = false,
    val drawer: DrawerNote = DrawerNote.None,
    val paymentNote: PaymentNote = PaymentNote.None,
    val printFailed: Boolean = false,
)

/** A category filter chip: [count] is how many active products carry it. */
internal data class PosCategoryUi(
    val name: String,
    val count: Int,
)

internal data class TillSaleUi(
    val id: Uuid,
    val receiptNumber: Int,
    val timeLabel: String,
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,
)

internal data class PosProductUi(
    val id: Uuid,
    val name: String,
    val category: String,
    val soldBy: SoldBy,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val imageUrl: String?,
    /** Null for everything that needs no warning: Panadería, in stock, or no stock figure yet. */
    val stockBadge: StockBadgeUi? = null,
)

/** How worried the cashier should be about an Almacén tile. Never gates a sale. */
internal enum class StockLevel { LOW, OUT, NEGATIVE }

/**
 * [stale] is true while the till is offline (or sync needs attention): the level is then the *last
 * known* one, and the tile says so by drawing the badge outlined rather than filled.
 */
internal data class StockBadgeUi(
    val level: StockLevel,
    val stockUnits: Long,
    val stale: Boolean,
)

/**
 * The way back from a dead device session.
 *
 * A device session can expire or be revoked while the till is mid-shift. Sync then reports
 * `AuthRequired`, which is terminal -- only a fresh login fixes it -- and until this existed the
 * till had no way to perform one: the sync pill's tap ran `syncNow()`, which is precisely the call
 * that cannot succeed, and the enrollment screen is unreachable once a tenant is cached. A till in
 * that state went on selling offline while its outbox grew, with nothing on screen offering a fix.
 *
 * Re-login happens **in place**: `AuthRepository.login` persists new tokens and refreshes the
 * tenant caches without touching the local database, so the queued sales survive and drain on the
 * next push. That is the whole reason this is a dialog over the POS rather than a route back to
 * enrollment, which would strand them.
 */
internal data class ReconnectUi(
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val invalidCredentials: Boolean = false,
    val needsConnection: Boolean = false,
)

internal enum class ScaleMode { CONNECTED, SIMULATED, MANUAL, DISCONNECTED }

/** Where the weigh panel's reading comes from; decides the chip on the readout. */
internal enum class WeighSource { Real, Manual, Simulated }

/**
 * One open weigh session. [isManualEntry] drives which input the screen shows (live number vs a
 * kg text field) but both paths update [grams]/[stable] through the same [PosViewModel] collector
 * on the active `Scale.readings` — manual entry is not a separate data path (04 §5).
 */
internal data class WeighPanelUi(
    val productId: Uuid,
    val productName: String,
    val pricePerKgMinor: Long,
    val grams: Long,
    val stable: Boolean,
    val isManualEntry: Boolean,
    val manualKgText: String = "",
    val source: WeighSource = if (isManualEntry) WeighSource.Manual else WeighSource.Real,
) {
    /**
     * An empty scale must not create a zero line, and an unstable reading must not be captured
     * (05 §6). "Agregar" is live only for a reading that can be trusted: a typed weight counts as
     * settled, but a scale's reading must be flagged stable, and a zero is never a sale line.
     * Stated here because the screen and the ViewModel both need the answer.
     */
    val canAdd: Boolean get() = if (isManualEntry) grams > 0 else stable && grams > 0

    /** Whether the readout may say "Estabilizando…". A typed weight never stabilises, so manual entry has no such state. */
    val showsStabilising: Boolean get() = !isManualEntry && !stable

    /** Shown as "Estable" in the readout; only a scale's reading has a settled state. */
    val settled: Boolean get() = !showsStabilising

    /**
     * What the customer is shown while the bag is on the scale -- the *same* rule that prices the
     * line it becomes. The screen used to inline its own copy of the rounding, so the quoted
     * price and the charged price were two code paths that happened to agree.
     */
    val previewTotalMinor: Long get() = lineTotalForWeight(grams, pricePerKgMinor)

    fun onReading(grams: Long, stable: Boolean): WeighPanelUi = copy(grams = grams, stable = stable)

    /**
     * Manual entry still reaches [grams] through the active scale (04 §5: not a separate data
     * path), so this only records the text -- except when the text stops being a weight at all.
     * Clearing "0,520" to "" or "0," left the previous grams in place with Add still armed, so
     * the till could charge for a weight the field no longer showed.
     */
    fun onManualText(text: String): WeighPanelUi {
        val parsed = parseKgTextToGrams(text)
        return if (parsed == null) copy(manualKgText = text, grams = 0, stable = false)
        else copy(manualKgText = text)
    }
}

internal data class PosCartLineUi(
    val lineId: Uuid,
    val productId: Uuid,
    val productTypeSnapshot: String,
    val soldBy: SoldBy,
    val name: String,
    val metaLabel: String,
    val weightGrams: Long?,
    val quantity: Int?,
    val unitPriceMinorSnapshot: Long,
    val totalMinor: Long,
)
