package com.f776.ecopan.feature.production.screens.stockglance

import com.f776.ecopan.feature.shell.ShellContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class StockGlanceViewModel(
    inventoryRepository: InventoryRepository,
    ingredientRepository: IngredientRepository,
    private val shellContext: ShellContext,
) : ViewModel() {

    private val activeBranchId = shellContext.shift
        .map { it.branchId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow(StockGlanceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            activeBranchId.filterNotNull()
                .flatMapLatest { branchId -> inventoryRepository.stockLevels(branchId) }
                // The threshold lives on the ingredient, the balance on the branch's movement
                // ledger; the screen needs both on one row, so they are joined here rather than
                // widening StockLevel (which the till reads too and has no use for a reorder point).
                .combine(ingredientRepository.observeActiveIngredients()) { levels, ingredients ->
                    val thresholds = ingredients.associate { it.id to it.lowStockThresholdGrams }
                    levels.sortedBy { it.name }.map { level ->
                        StockRowUi(
                            ingredientId = level.ingredientId,
                            name = level.name,
                            currentGrams = level.currentGrams,
                            lowStockThresholdGrams = thresholds[level.ingredientId],
                        )
                    }
                }
                .collect { rows ->
                    // copy(), not a fresh StockGlanceUiState -- the header fields below are written
                    // by their own collectors and must survive a stock-levels emission.
                    _uiState.update { it.copy(loading = false, rows = rows) }
                }
        }
        viewModelScope.launch {
            shellContext.indicator.collect { indicator ->
                _uiState.update { it.copy(syncIndicator = indicator) }
            }
        }
        viewModelScope.launch {
            shellContext.lastSyncedAt.collect { at -> _uiState.update { it.copy(lastSyncedAt = at) } }
        }
        viewModelScope.launch {
            shellContext.shift.collect { shift ->
                _uiState.update { it.copy(tenantName = shift.tenantName) }
            }
        }
    }

    /** Manual "Sync now" (03 §6.1) -- fire-and-forget; [SyncEngine.status] drives the indicator back. */
    fun onSyncIndicatorTapped() {
        viewModelScope.launch { shellContext.syncNow() }
    }
}
