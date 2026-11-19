package com.f776.ecopan.domain.tenant

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * The till's device session is long-lived (one online login per device, 01 §4) and out of scope
 * here — this tracks only which cached [UserAccount] currently has the till unlocked. `null`
 * means locked: the app should show the lock screen rather than any top-level destination.
 */
interface SessionProvider {
    val currentUserId: Flow<Uuid?>

    /** Called once [TenantRepository.verifyPin] has already confirmed the PIN. */
    suspend fun unlock(userId: Uuid)
    suspend fun lock()
}
