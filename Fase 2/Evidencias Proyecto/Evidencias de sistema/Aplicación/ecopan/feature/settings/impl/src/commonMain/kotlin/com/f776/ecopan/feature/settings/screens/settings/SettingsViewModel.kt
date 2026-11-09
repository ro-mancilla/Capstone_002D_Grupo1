package com.f776.ecopan.feature.settings.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.preferences.AppTheme
import com.f776.ecopan.domain.preferences.ThemePreferencesDataSource
import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.domain.reminder.permission.ReminderPermissionManager
import com.f776.ecopan.domain.sync.RejectedOperation
import com.f776.ecopan.domain.sync.RejectedOperations
import com.f776.ecopan.domain.tenant.DeviceSessionStore
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.feature.settings.config.SettingsBuildKonfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val themePreferencesDataSource: ThemePreferencesDataSource,
    private val reminderPermissionManager: ReminderPermissionManager,
    private val tenantRepository: TenantRepository,
    private val deviceSessionStore: DeviceSessionStore,
    private val rejectedOperations: RejectedOperations,
) : ViewModel() {
    val isNotificationPermissionRequired: Boolean
        get() = reminderPermissionManager.isNotificationPermissionRequired()

    private val _notificationPermissionGranted = MutableStateFlow(false)

    /**
     * The name this till was enrolled under ("Caja 1"). A one-shot suspend read, not a stream, so it
     * is loaded once into a flow the combine below can take part in rather than being fetched
     * inside the combine (which would re-read it on every unrelated emission).
     */
    private val deviceName = MutableStateFlow<String?>(null)

    /**
     * The bakery this device is enrolled to ("Panadería El Sol") and what it was enrolled as ("Caja 1") and the
     * branch it sells from ("Sucursal Principal"), kept as two values. Blank until the bootstrap pull has cached them, which is
     * the honest state: an unenrolled device genuinely has no bakery to name. The device's UUID is
     * never shown; it is for support logs, not for people.
     */
    private val installation: Flow<InstallationUiState> =
        combine(tenantRepository.observeTenant(), tenantRepository.observeBranches(), deviceName) { tenant, branches, name ->
            InstallationUiState(
                version = SettingsBuildKonfig.APP_VERSION,
                build = SettingsBuildKonfig.APP_BUILD,
                bakeryName = tenant?.name.orEmpty(),
                deviceName = name.orEmpty(),
                branchName = branches.firstOrNull()?.name.orEmpty(),
            )
        }

    init {
        viewModelScope.launch { deviceName.value = deviceSessionStore.deviceName() }
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferencesDataSource.theme,
        themePreferencesDataSource.useDynamicColors,
        reminderPermissionManager.hasAlarmPermission(),
        reminderPermissionManager.hasNotificationPermission(),
        _notificationPermissionGranted,
        installation,
        rejectedOperations.rejected,
    ) { flows ->
        SettingsUiState(
            theme = flows[0] as AppTheme,
            useDynamicColors = flows[1] as Boolean,
            hasAlarmPermission = flows[2] as Boolean,
            hasNotificationPermission = (flows[3] as Boolean) || (flows[4] as Boolean),
            installation = flows[5] as InstallationUiState,
            rejected = flows[6] as List<RejectedOperation>,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            themePreferencesDataSource.setTheme(theme)
        }
    }

    fun toggleDynamicColors(useDynamicColors: Boolean) {
        viewModelScope.launch {
            themePreferencesDataSource.setUseDynamicColors(useDynamicColors)
        }
    }

    /** Back into the push queue; the scheduler sends it on its next cycle. */
    fun retryRejected(mutationId: Uuid) {
        viewModelScope.launch { rejectedOperations.retry(mutationId) }
    }

    /** Gives up on the operation; it stays in the outbox as a record but is never sent again. */
    fun discardRejected(mutationId: Uuid) {
        viewModelScope.launch { rejectedOperations.discard(mutationId) }
    }

    fun requestAlarmPermission() {
        reminderPermissionManager.requestAlarmPermission()
    }

    fun requestNotificationPermission() {
        viewModelScope.launch {
            val result = reminderPermissionManager.requestNotificationPermission()
            if (result == PermissionResult.GRANTED) {
                _notificationPermissionGranted.update { true }
            }
        }
    }
}

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val useDynamicColors: Boolean = true,
    val hasAlarmPermission: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val installation: InstallationUiState = InstallationUiState(),
    val rejected: List<RejectedOperation> = emptyList(),
)

/** The "Acerca de" rows: what this build is, and which bakery, device ("Caja 1") and branch it belongs to. */
data class InstallationUiState(
    val version: String = "",
    val build: String = "",
    val bakeryName: String = "",
    val deviceName: String = "",
    val branchName: String = "",
)
