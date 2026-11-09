@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.pos.screens.pos

import com.f776.ecopan.feature.shell.ShellContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.hardware.DeviceDisplay
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.deriveDeviceDisplay
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.ManualWeightEntry
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.PaymentResult
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ProductWeightScript
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLayout
import com.f776.ecopan.domain.hardware.ReceiptLine
import com.f776.ecopan.domain.hardware.SimulatedDrawer
import com.f776.ecopan.domain.hardware.SimulatedTerminalControl
import com.f776.ecopan.domain.production.ProductionRepository
import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleLine
import com.f776.ecopan.domain.sales.SaleRepository
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.LoginOutcome
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ecopan.jvm.feature.pos.impl.generated.resources.Res
import ecopan.jvm.feature.pos.impl.generated.resources.pos_receipt_thanks
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MANUAL_DRIVER_ID = "manual"
private const val MOCK_DRIVER_ID = "mock"
private const val DEFAULT_PRINTER_DRIVER = "mock"
private const val CARD_AUTO_APPROVE_DELAY_MS = 2200L

/**
 * POS main-screen view model (05 §2/§3). Cart lines are transient screen state only — nothing is
 * persisted until checkout, so payment/receipt/till are all panes owned by this single ViewModel
 * rather than separate NavKey destinations (05 §2, §7, §10 — "keeps cart state in one ViewModel").
 */
internal class PosViewModel(
    private val productRepository: ProductRepository,
    private val driverRegistry: DriverRegistry,
    private val saleRepository: SaleRepository,
    private val authRepository: AuthRepository,
    private val productionRepository: ProductionRepository,
    private val shellContext: ShellContext,
    /** Only for [SyncEngine.bootstrap] after a reconnect -- the rest goes through [shellContext]. */
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _eventChannel = Channel<PosEvent>()
    val eventChannel = _eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState = _uiState.asStateFlow()

    private var products: List<Product> = emptyList()

    /** The badge is drawn from the last synced stock; offline it is still shown, flagged as possibly out of date. */
    private val stockIsStale: Boolean
        get() = _uiState.value.syncIndicator.let { it is SyncIndicatorState.OfflinePending || it is SyncIndicatorState.Attention }

    /** The boleta that is on screen, kept so "Reimprimir" prints exactly what was sold. */
    private var shownReceiptDoc: ReceiptDoc? = null
    private var weighJob: Job? = null
    private var cardAuthJob: Job? = null
    private var tillJob: Job? = null

    private val activeScale = driverRegistry.scale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private val activeScaleDriverId = driverRegistry.profile
        .map { it.scale.driverId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), MANUAL_DRIVER_ID)

    // Eagerly shared: nothing else keeps these hot the way the weigh-panel collector does for
    // activeScale, but their `.value` needs to be readable from a plain (non-suspend) function
    // call at any time (finalizeSale, till open) — WhileSubscribed would never start collecting
    // without an active subscriber and `.value` would stay stuck at the initial `null` forever.
    private val activeDrawer = driverRegistry.drawer
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeTerminal = driverRegistry.terminal
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A till caches exactly one branch (01 §4-style single-tenant device) — the first (only) one. */
    private val shift = shellContext.shift
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeBranchId = shift.map { it?.branchId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activeCashierId = shift.map { it?.userId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Paper width and which printer is in use; read when a sale is finalised, so no subscriber is needed. */
    private val printerChoice = driverRegistry.profile
        .map { it.printer }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DriverChoice(DEFAULT_PRINTER_DRIVER))

    private val drawerChoiceId = driverRegistry.profile
        .map { it.drawer.driverId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "mock")

    init {
        viewModelScope.launch {
            productRepository.observeActiveProducts().collect { active ->
                products = active
                rebuildProducts()
            }
        }
        viewModelScope.launch {
            combine(
                activeScaleDriverId,
                activeScale.flatMapLatest { it?.status ?: flowOf(DeviceStatus.DISCONNECTED) },
            ) { driverId, status ->
                when {
                    driverId == MANUAL_DRIVER_ID -> ScaleMode.MANUAL
                    // The demo scale reports CONNECTED; the top bar must not read "Balanza OK" for it.
                    deriveDeviceDisplay(driverRegistry.availableScaleDrivers.find { it.id == driverId }, status) ==
                        DeviceDisplay.Simulated -> ScaleMode.SIMULATED
                    status == DeviceStatus.CONNECTED -> ScaleMode.CONNECTED
                    else -> ScaleMode.DISCONNECTED
                }
            }.collect { mode -> _uiState.update { it.copy(scaleMode = mode) } }
        }
        // Batch-ready notice (05 §3 item 9, spec 3d): purely informational, collected best-effort.
        // Batches reach this feed over SSE while online and via the pull applier otherwise,
        // de-duplicated by id upstream -- so offline it simply goes quiet and catches up on
        // reconnect. Batches within BATCH_GROUP_WINDOW of each other share one notice.
        viewModelScope.launch {
            productionRepository.batchReadyFeed().collect { batch ->
                // Name the product from the catalogue when it is there, else from the wire; leave it out
                // when neither knows it -- a notice never shows an identifier.
                val fact = BatchFact(
                    productName = products.firstOrNull { it.id == batch.productId }?.name
                        ?: batch.productTypeName.takeIf { it.isNotBlank() },
                    massGrams = batch.mixWeightGrams,
                    // Only this device's own branch is known by name; another branch's batch is left unnamed.
                    branchName = shift.value?.takeIf { it.branchId == batch.branchId }?.branchName?.takeIf { it.isNotBlank() },
                )
                _uiState.update { it.copy(batchNotices = it.batchNotices.withBatch(batch.batchId, batch.readyAt, fact)) }
            }
        }
        // Network-state indicator (03 §6.1) -- must be visible at all times on the POS sale screen.
        viewModelScope.launch {
            shellContext.indicator.collect { indicator ->
                _uiState.update { it.copy(syncIndicator = indicator) }
                rebuildProducts()
            }
        }
        // Still the raw status, and only for this: `SyncIndicatorState.Attention` folds
        // `AuthRequired` and `Error` together, but tapping the indicator must offer a reconnect
        // for the first and a plain retry for the second.
        viewModelScope.launch {
            syncEngine.status.collect { lastSyncStatus = it }
        }
    }

    fun onAction(action: PosAction) {
        when (action) {
            is PosAction.OnCategorySelected -> selectCategory(action.category)
            is PosAction.OnSearchQueryChanged -> onSearchQueryChanged(action.query)
            is PosAction.OnProductTapped -> onProductTapped(action.productId)
            is PosAction.OnManualWeightChanged -> onManualWeightChanged(action.kgText)
            is PosAction.OnAddWeighedLine -> addWeighedLine(action.grams)
            PosAction.OnCloseWeighPanel -> closeWeighPanel()
            is PosAction.OnVoidLine -> voidLine(action.lineId)
            is PosAction.OnStepLineQuantity -> stepLineQuantity(action.lineId, action.delta)
            PosAction.OnDiscardSale -> discardSale()
            PosAction.OnCharge -> charge()
            PosAction.OnSwitchToManualScale -> switchToManualScale()
            is PosAction.OnPaymentMethodSelected -> selectPaymentMethod(action.method)
            PosAction.OnBackToMethodPicker -> backToMethodPicker()
            is PosAction.OnCashTenderedChanged -> onCashTenderedChanged(action.text)
            is PosAction.OnQuickCashChip -> onCashTenderedChanged(action.amountMinor.toString())
            PosAction.OnConfirmCashPayment -> confirmCashPayment()
            PosAction.OnStartCardAuth -> startCardAuth()
            PosAction.OnDeclineCardAuthDemo -> declineCardAuthDemo()
            PosAction.OnConfirmOtherPayment -> confirmOtherPayment()
            PosAction.OnCancelPayment -> cancelPayment()
            PosAction.OnDismissReceipt -> dismissReceipt()
            PosAction.OnReprintReceipt -> reprintReceipt()
            PosAction.OnOpenTill -> openTill()
            PosAction.OnCloseTill -> closeTill()
            is PosAction.OnBarcodeScanned -> onBarcodeScanned(action.code)
            is PosAction.OnDismissBatchNotice -> dismissBatchNotice(action.id)
            PosAction.OnSyncIndicatorTapped -> onSyncIndicatorTapped()
            is PosAction.OnReconnectUsernameChanged ->
                _uiState.update { it.copy(reconnect = it.reconnect?.copy(username = action.value, invalidCredentials = false)) }

            is PosAction.OnReconnectPasswordChanged ->
                _uiState.update { it.copy(reconnect = it.reconnect?.copy(password = action.value, invalidCredentials = false)) }

            PosAction.OnReconnectSubmitted -> reconnect()
            PosAction.OnReconnectDismissed -> _uiState.update { it.copy(reconnect = null) }
        }
    }

    private fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        rebuildProducts()
    }

    private fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        rebuildProducts()
    }

    private fun rebuildProducts() {
        val perCategory = products.groupingBy { it.productType }.eachCount()
        val categories = listOf(PosCategoryUi(ALL_CATEGORY, products.size)) +
            perCategory.keys.sorted().map { PosCategoryUi(it, perCategory.getValue(it)) }
        val selected = _uiState.value.selectedCategory
        val query = _uiState.value.searchQuery.trim()
        val filtered = products
            .filter { selected == ALL_CATEGORY || it.productType == selected }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .map { it.toUi() }
        _uiState.update { it.copy(loading = false, categories = categories, products = filtered) }
    }

    private fun Product.toUi(): PosProductUi = PosProductUi(
        id = id,
        name = name,
        category = productType,
        soldBy = soldBy,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        imageUrl = imageUrl,
        stockBadge = stockBadge(stale = stockIsStale),
    )

    private fun onProductTapped(productId: Uuid) {
        val product = products.firstOrNull { it.id == productId } ?: return
        when (product.soldBy) {
            SoldBy.UNIT -> addOrIncrementUnitLine(product)
            SoldBy.WEIGHT -> openWeighPanel(product)
        }
    }

    private fun addOrIncrementUnitLine(product: Product) {
        _uiState.update { it.copy(cart = it.cart.addUnit(product)) }
    }

    private fun openWeighPanel(product: Product) {
        val pricePerKg = product.pricePerKgMinor ?: return
        val driverId = activeScaleDriverId.value
        val manual = driverId == MANUAL_DRIVER_ID
        weighJob?.cancel()
        _uiState.update {
            it.copy(
                weighPanel = WeighPanelUi(
                    productId = product.id,
                    productName = product.name,
                    pricePerKgMinor = pricePerKg,
                    grams = 0,
                    stable = false,
                    isManualEntry = manual,
                    source = when (driverId) {
                        MANUAL_DRIVER_ID -> WeighSource.Manual
                        MOCK_DRIVER_ID -> WeighSource.Simulated
                        else -> WeighSource.Real
                    },
                )
            )
        }
        // Before observing: a scripted scale drops the previous product's last reading when it is
        // told the new one, and observing first would replay that stale weight into this panel.
        (activeScale.value as? ProductWeightScript)?.onProductSelected(product.name)
        weighJob = viewModelScope.launch {
            activeScale.filterNotNull().flatMapLatest { it.readings }.collect { reading ->
                _uiState.update { state ->
                    val panel = state.weighPanel ?: return@update state
                    if (panel.productId != product.id) return@update state
                    state.copy(weighPanel = panel.onReading(reading.grams, reading.stable))
                }
            }
        }
    }

    private fun onManualWeightChanged(kgText: String) {
        // Copy from the panel *inside* the update, not one read before it: the readings collector
        // writes the same field, and reading it first dropped whatever arrived in between.
        _uiState.update { it.copy(weighPanel = it.weighPanel?.onManualText(kgText)) }
        val grams = parseKgTextToGrams(kgText) ?: return
        (activeScale.value as? ManualWeightEntry)?.submitWeight(grams)
    }

    private fun closeWeighPanel() {
        weighJob?.cancel()
        weighJob = null
        _uiState.update { it.copy(weighPanel = null) }
    }

    private fun addWeighedLine(displayedGrams: Long) {
        val panel = _uiState.value.weighPanel ?: return
        if (!panel.canAdd || displayedGrams <= 0) return
        val product = products.firstOrNull { it.id == panel.productId } ?: return
        _uiState.update {
            it.copy(
                cart = it.cart.addWeighed(product, displayedGrams, panel.pricePerKgMinor),
                weighPanel = null,
            )
        }
        weighJob?.cancel()
        weighJob = null
    }

    private fun voidLine(lineId: Uuid) {
        _uiState.update { it.copy(cart = it.cart.remove(lineId)) }
    }

    private fun stepLineQuantity(lineId: Uuid, delta: Int) {
        _uiState.update { it.copy(cart = it.cart.step(lineId, delta)) }
    }

    private fun discardSale() {
        weighJob?.cancel()
        weighJob = null
        _uiState.update { it.copy(cart = Cart(), weighPanel = null) }
    }

    private fun switchToManualScale() {
        viewModelScope.launch { driverRegistry.select(DeviceInterface.SCALE, DriverChoice(MANUAL_DRIVER_ID)) }
    }

    // --- Barcode HID capture (05 §3 item 4, §6 edge cases, §9 item 8) ---

    private fun onBarcodeScanned(code: String) {
        viewModelScope.launch {
            val product = productRepository.findByBarcode(code)
            when {
                product == null -> _eventChannel.send(PosEvent.UnknownBarcodeScanned)
                // A WEIGHT product hit by barcode routes to the weigh panel — never a bare unit
                // line (05 §6 edge case table is explicit about this).
                product.soldBy == SoldBy.WEIGHT -> openWeighPanel(product)
                else -> addOrIncrementUnitLine(product)
            }
        }
    }

    // --- Batch-ready notice (05 §3 item 9, §9 item 10) ---

    private fun dismissBatchNotice(id: Uuid) {
        _uiState.update { it.copy(batchNotices = it.batchNotices.filterNot { notice -> notice.id == id }) }
    }

    // --- Sync status indicator (03 §6.1) ---

    /** Manual "Sync now" trigger (03 §6.1) -- fire-and-forget; [syncEngine.status] drives the UI. */
    /**
     * Tapping the pill asks for a sync -- except when the session is the thing that is broken, in
     * which case `syncNow()` is exactly the call that cannot succeed and the tap has to offer the
     * fix instead. That was the old behaviour for every state, so a till whose session had died
     * had a button that looked like it did something and never did.
     */
    /**
     * Mirror of the last [SyncEngine.status] seen, because the interface exposes a plain `Flow` and
     * the tap handler needs the current phase synchronously -- the same reason `SyncSchedulerImpl`
     * keeps one.
     */
    private var lastSyncStatus: SyncStatus = SyncStatus.Idle(ConnectivityStatus.ONLINE)

    private fun onSyncIndicatorTapped() {
        // Keyed off the domain status, not the pill: `Attention` is the single colour the indicator
        // has for "a human is needed", and it now also covers server-rejected rows, where a
        // re-login is not the fix and offering one would be misdirection. Only a dead session
        // opens the reconnect form.
        if (lastSyncStatus is SyncStatus.AuthRequired) {
            _uiState.update { it.copy(reconnect = ReconnectUi()) }
            return
        }
        viewModelScope.launch { shellContext.syncNow() }
    }

    private fun reconnect() {
        val form = _uiState.value.reconnect ?: return
        if (form.username.isBlank() || form.password.isBlank() || form.submitting) return
        val slug = shift.value?.tenantSlug?.takeIf { it.isNotEmpty() } ?: return

        _uiState.update {
            it.copy(reconnect = form.copy(submitting = true, invalidCredentials = false, needsConnection = false))
        }
        viewModelScope.launch {
            // `login` persists the new device session and refreshes the tenant caches without
            // touching the local database, so anything already queued in the outbox is still
            // queued after this -- and `bootstrap()` then drains it.
            when (authRepository.login(slug, form.username, form.password, RECONNECT_DEVICE_NAME)) {
                is LoginOutcome.Success -> {
                    _uiState.update { it.copy(reconnect = null) }
                    syncEngine.bootstrap()
                }

                LoginOutcome.InvalidCredentials -> _uiState.update {
                    it.copy(reconnect = form.copy(submitting = false, invalidCredentials = true))
                }

                else -> _uiState.update {
                    it.copy(reconnect = form.copy(submitting = false, needsConnection = true))
                }
            }
        }
    }

    // --- Payment (05 §7) ---

    private fun charge() {
        val state = _uiState.value
        if (state.cart.lines.isEmpty()) return
        _uiState.update { it.copy(paymentDialog = PaymentDialogUi(totalMinor = state.cart.totalMinor)) }
    }

    private fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentDialog = it.paymentDialog?.copy(method = method, cardPhase = CardAuthPhase.IDLE)) }
    }

    private fun backToMethodPicker() {
        cardAuthJob?.cancel()
        _uiState.update {
            it.copy(paymentDialog = it.paymentDialog?.copy(method = null, cardPhase = CardAuthPhase.IDLE, declineReason = null))
        }
    }

    private fun onCashTenderedChanged(text: String) {
        val digits = text.filter(Char::isDigit).take(9)
        _uiState.update { it.copy(paymentDialog = it.paymentDialog?.copy(cashTenderedText = digits)) }
    }

    private fun confirmCashPayment() {
        val dialog = _uiState.value.paymentDialog ?: return
        val tendered = dialog.cashTenderedText.toLongOrNull() ?: return
        val change = tendered - dialog.totalMinor
        if (change < 0) return
        viewModelScope.launch {
            finalizeSale(Payment.Cash(tenderedMinor = tendered, changeMinor = change))
        }
    }

    private fun startCardAuth() {
        val terminal = activeTerminal.value ?: return
        val totalMinor = _uiState.value.paymentDialog?.totalMinor ?: return
        _uiState.update { it.copy(paymentDialog = it.paymentDialog?.copy(cardPhase = CardAuthPhase.AUTHORIZING)) }
        cardAuthJob = viewModelScope.launch {
            val resultJob = async { terminal.request(totalMinor) }
            val autoApprove = launch {
                delay(CARD_AUTO_APPROVE_DELAY_MS)
                (terminal as? SimulatedTerminalControl)?.resolve(PaymentResult.Approved(PaymentMethod.CARD))
            }
            when (val result = resultJob.await()) {
                is PaymentResult.Approved -> {
                    autoApprove.cancel()
                    _uiState.update { it.copy(paymentDialog = it.paymentDialog?.copy(cardPhase = CardAuthPhase.APPROVED)) }
                    finalizeSale(Payment.Card)
                }
                is PaymentResult.Declined -> {
                    autoApprove.cancel()
                    _uiState.update {
                        it.copy(paymentDialog = it.paymentDialog?.copy(cardPhase = CardAuthPhase.DECLINED, declineReason = result.reason))
                    }
                }
                PaymentResult.Cancelled -> {
                    autoApprove.cancel()
                    _uiState.update { it.copy(paymentDialog = it.paymentDialog?.copy(cardPhase = CardAuthPhase.IDLE)) }
                }
            }
        }
    }

    private fun declineCardAuthDemo() {
        (activeTerminal.value as? SimulatedTerminalControl)?.resolve(PaymentResult.Declined("Rechazada por el emisor"))
    }

    private fun confirmOtherPayment() {
        val method = _uiState.value.paymentDialog?.method ?: return
        if (method != PaymentMethod.TRANSFER && method != PaymentMethod.OTHER) return
        viewModelScope.launch {
            finalizeSale(Payment.Other(method))
        }
    }

    private fun cancelPayment() {
        cardAuthJob?.cancel()
        _uiState.update { it.copy(paymentDialog = null) }
    }

    private suspend fun finalizeSale(payment: Payment) {
        val branchId = activeBranchId.value
        val cashierId = activeCashierId.value
        if (branchId == null || cashierId == null) {
            _eventChannel.send(PosEvent.NoActiveBranchOrSession)
            return
        }

        val lines = _uiState.value.cart.lines
        val total = _uiState.value.cart.totalMinor
        val saleId = Uuid.random()
        val now = Clock.System.now()

        // recordSale commits before any hardware side effect — a printer/drawer failure never
        // loses the sale (05 §7). It also hands back the till's own sale number.
        val receiptNumber = saleRepository.recordSale(
            Sale(
                id = saleId,
                branchId = branchId,
                cashierId = cashierId,
                timestamp = now,
                paymentMethod = payment.method,
                totalMinor = total,
                cashTenderedMinor = payment.tenderedMinor,
                changeMinor = payment.changeMinor,
                lines = lines.map { it.toSaleLine() },
            )
        )

        // Attempted independently of printing: cash must still be storable when the printer is
        // the thing that failed (05 §6).
        var drawer = DrawerNote.None
        if (payment.kicksDrawer) {
            val device = activeDrawer.value
            val opened = runCatching { device?.open() }.isSuccess && device != null
            if (opened) drawer = if (device is SimulatedDrawer || isSimulated(DeviceInterface.DRAWER)) DrawerNote.OpenedSimulated else DrawerNote.Opened
        }

        val paymentNote = when (payment.method) {
            PaymentMethod.CARD ->
                if (activeTerminal.value is SimulatedTerminalControl) PaymentNote.CardApprovedSimulated else PaymentNote.CardApproved
            PaymentMethod.TRANSFER -> PaymentNote.TransferRecorded
            PaymentMethod.OTHER -> PaymentNote.OtherRecorded
            PaymentMethod.CASH -> PaymentNote.None
        }

        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val doc = ReceiptDoc(
            header = listOfNotNull(
                shift.value?.tenantName?.takeIf { it.isNotBlank() },
                shift.value?.branchName?.takeIf { it.isNotBlank() },
            ),
            lines = lines.map { it.toReceiptLine() },
            totalMinor = total,
            paymentMethod = payment.method,
            footer = listOf(getString(Res.string.pos_receipt_thanks)),
            saleId = saleId,
            receiptNumber = receiptNumber,
            issuedAtLabel = "${local.day.pad2()}-${(local.month.ordinal + 1).pad2()}-${local.year} ${local.hour.pad2()}:${local.minute.pad2()}",
            cashTenderedMinor = payment.tenderedMinor,
            changeMinor = payment.changeMinor,
        )
        shownReceiptDoc = doc
        val paperWidth = PaperWidth.fromConfig(printerChoice.value.config)

        var printFailed = false
        if (payment.prints) printFailed = !tryPrint(doc)

        _uiState.update {
            it.copy(
                receipt = ReceiptUi(
                    saleId = saleId,
                    receiptNumber = receiptNumber,
                    timeLabel = "${local.hour}:${local.minute.pad2()}",
                    lines = lines,
                    totalMinor = total,
                    paymentMethod = payment.method,
                    cashTenderedMinor = payment.tenderedMinor,
                    changeMinor = payment.changeMinor,
                    paperLines = ReceiptLayout.render(doc, paperWidth),
                    paperWidth = paperWidth,
                    paperSimulated = isSimulated(DeviceInterface.PRINTER, paper = true),
                    drawer = drawer,
                    paymentNote = paymentNote,
                    printFailed = printFailed,
                ),
                paymentDialog = null,
            )
        }
    }

    /**
     * `true` when the printer took the document. A throwing or failing printer is a message on the
     * boleta, never a lost sale.
     *
     * Resolves the printer by *waiting* for one rather than sampling `activePrinter.value`: that
     * value is null between construction and the first driver emission, and a sale completed in
     * that window skipped printing with no signal at all. `PrintResult` is read rather than
     * discarded, so a printer that reports `Failed` is no longer silent.
     */
    private suspend fun tryPrint(doc: ReceiptDoc): Boolean {
        val printer = runCatching { driverRegistry.printer.first() }.getOrNull() ?: return false
        return runCatching { printer.print(doc) }.getOrNull() == PrintResult.Ok
    }

    private fun reprintReceipt() {
        val doc = shownReceiptDoc ?: return
        viewModelScope.launch {
            val printed = tryPrint(doc)
            _uiState.update { it.copy(receipt = it.receipt?.copy(printFailed = !printed)) }
        }
    }

    /**
     * Whether what [iface] does is pretend: a demo driver, or a drawer kicked through a printer that
     * is a demo or absent. [paper] also counts "Sin impresora" as simulated, because the boleta then
     * only exists on screen and carries the SIMULADO strips.
     */
    private fun isSimulated(iface: DeviceInterface, paper: Boolean = false): Boolean {
        val printer = driverRegistry.availablePrinterDrivers.find { it.id == printerChoice.value.driverId }
        val display = when (iface) {
            DeviceInterface.PRINTER -> deriveDeviceDisplay(printer)
            DeviceInterface.DRAWER ->
                deriveDeviceDisplay(driverRegistry.availableDrawerDrivers.find { it.id == drawerChoiceId.value }, printer = printer)
            DeviceInterface.TERMINAL -> DeviceDisplay.Ready
            DeviceInterface.SCALE ->
                deriveDeviceDisplay(driverRegistry.availableScaleDrivers.find { it.id == activeScaleDriverId.value })
        }
        return display == DeviceDisplay.Simulated || (paper && display == DeviceDisplay.None)
    }

    private fun Int.pad2() = toString().padStart(2, '0')

    private fun PosCartLineUi.toReceiptLine() = ReceiptLine(
        name = name,
        quantityLabel = if (soldBy == SoldBy.WEIGHT) formatGramsAsKg(weightGrams ?: 0) else (quantity ?: 0).toString(),
        unitPriceMinor = unitPriceMinorSnapshot,
        lineTotalMinor = totalMinor,
        perKg = soldBy == SoldBy.WEIGHT,
    )

    private fun PosCartLineUi.toSaleLine() = SaleLine(
        id = Uuid.random(),
        productId = productId,
        productTypeSnapshot = productTypeSnapshot,
        soldBy = soldBy,
        weightGrams = weightGrams,
        quantity = quantity,
        unitPriceMinorSnapshot = unitPriceMinorSnapshot,
        lineTotalMinor = totalMinor,
    )

    private fun dismissReceipt() {
        // The on-screen receipt is the last step of the sale flow — dismissing it clears the cart
        // for the next customer (05 §7 item 7's "cart resets to Idle").
        shownReceiptDoc = null
        _uiState.update { it.copy(receipt = null, cart = Cart()) }
    }

    // --- Till (read-only today's sales, 05 §11 open question resolved as a lean pane) ---

    private fun openTill() {
        _uiState.update { it.copy(tillOpen = true) }
        tillJob?.cancel()
        tillJob = viewModelScope.launch {
            activeBranchId.filterNotNull()
                .flatMapLatest { branchId -> saleRepository.observeTodaySales(branchId) }
                .collect { sales ->
                    _uiState.update { it.copy(tillSales = sales.sortedByDescending { s -> s.timestamp }.map { s -> s.toUi() }) }
                }
        }
    }

    private fun closeTill() {
        tillJob?.cancel()
        tillJob = null
        _uiState.update { it.copy(tillOpen = false, tillSales = emptyList()) }
    }

    private fun Sale.toUi(): TillSaleUi {
        val local = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val timeLabel = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        return TillSaleUi(id = id, receiptNumber = receiptNumber, timeLabel = timeLabel, paymentMethod = paymentMethod, totalMinor = totalMinor)
    }
}

/**
 * `SyncStatus -> SyncIndicatorState` (03 §6.1, work item 8) -- `:core:ui` stays domain-free, so
 * this per-screen translation is the seam, same as `:mobile:feature:production:impl`'s
 * `SyncIndicatorMapping.kt`. It cannot be hoisted to `:composeApp` (where 03 §6.1 sketches it)
 * because this module is a child of `:composeApp`, nor to `:core:ui`, which 03 §6.1 itself
 * requires stay ignorant of `:data:sync:api` -- see `AppViewModel`'s KDoc.
 * Duplicated rather than shared: `PosViewModel` lives in `jvm/feature/pos/impl`, which `composeApp`
 * depends on (not the other way around), so there is no shared module both already sit under to
 * hold one copy without inventing a new one just for an ~8-line pure function.
 */

/** Kept stable across reconnects so a re-login updates the same device row in the admin list
 * rather than minting a second "Caja" every time a session is renewed. */
private const val RECONNECT_DEVICE_NAME = "Caja"
