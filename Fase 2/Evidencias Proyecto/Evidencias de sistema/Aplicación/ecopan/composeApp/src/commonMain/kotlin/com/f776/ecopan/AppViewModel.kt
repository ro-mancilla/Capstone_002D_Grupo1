package com.f776.ecopan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.domain.preferences.ThemePreferencesDataSource
import com.f776.ecopan.domain.production.BatchReadyStream
import com.f776.ecopan.domain.sync.SyncScheduler
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Deliberately does **not** carry the `SyncStatus -> SyncIndicatorState` mapping, despite 03 §6.1
 * placing it here. That placement doesn't survive contact with the module graph: the indicator has
 * to render inside `:jvm:feature:pos:impl` and `:mobile:feature:production:impl`, and neither can
 * depend on `:composeApp` (they are its children).
 *
 * It is not in `:core:ui` either, which 03 §6.1's other half forbids (`:core:ui` must not know
 * `:data:sync:api`). It lives in `:feature:shell:impl` — a third option this comment used to
 * overlook: a feature-tier module sits below `:composeApp` and above `:core:ui`, and may know
 * `:data:sync:api`. Both clients read it from `ShellContext.indicator`, so the mapping exists
 * once rather than once per screen.
 */
class AppViewModel(
    themePreferencesDataSource: ThemePreferencesDataSource,
    tenantRepository: TenantRepository,
    private val syncScheduler: SyncScheduler,
    private val batchReadyStream: BatchReadyStream,
) : ViewModel() {

    init {
        // 03 §7: "SyncSchedulerImpl.start() (called from :composeApp on session-ready)". Until this
        // existed the scheduler was bound but never started, so nothing ever synced on its own --
        // only a manual "Sync now" tap or the login bootstrap moved data.
        //
        // A cached tenant is the session-ready signal: `AuthRepositoryImpl.login` upserts it as
        // part of enrollment, so non-null means this device holds a device session and has
        // bootstrapped. Deliberately *not* gated on PIN unlock -- per 01 §4 the device session
        // authenticates sync while the staff PIN is a local-only lock, so a locked till must keep
        // flushing its outbox rather than stranding sales until someone signs in.
        viewModelScope.launch {
            tenantRepository.observeTenant()
                .map { it != null }
                .distinctUntilChanged()
                .collect { enrolled ->
                    if (enrolled) {
                        syncScheduler.start()
                        // 06 §6's live batch-ready push. Same gate as the scheduler: it needs the
                        // device session to authenticate, and it is pure latency -- if it never
                        // starts, batches still arrive on the pull feed.
                        batchReadyStream.start()
                    } else {
                        syncScheduler.stop()
                        batchReadyStream.stop()
                    }
                }
        }
    }

    override fun onCleared() {
        syncScheduler.stop()
        batchReadyStream.stop()
        super.onCleared()
    }

    val theme = themePreferencesDataSource.theme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1.seconds),
            initialValue = AppTheme.SYSTEM
        )

    val useDynamicColors = themePreferencesDataSource.useDynamicColors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1.seconds),
            initialValue = false
        )

}
