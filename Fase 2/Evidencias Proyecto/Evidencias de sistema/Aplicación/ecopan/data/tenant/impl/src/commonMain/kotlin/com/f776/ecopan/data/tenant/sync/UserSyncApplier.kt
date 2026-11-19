package com.f776.ecopan.data.tenant.sync

import com.f776.ecopan.core.domain.auth.Role
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.Uuid

/**
 * Server-authoritative config applier for `USER` (03 §4.3 family 1 — overwrite by id).
 *
 * This is what makes the PIN lock screen work for anyone other than the person who enrolled the
 * device. Enrollment caches only its own user (and the login response carries no `pinHash`, so not
 * even that one is PIN-unlockable); every other staff member — and every real `pinHash` — arrives
 * here. Without this applier the staff picker stayed empty and nobody could unlock the till.
 *
 * `pinHash` is a bcrypt hash, never a PIN. It is verified locally by `TenantRepositoryImpl.verifyPin`
 * so a shift change works with no network at all (01 §4).
 */
internal class UserSyncApplier(
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.USER

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<UserPullPayload>(payload)
        val role = runCatching { Role.valueOf(wire.role) }.getOrNull() ?: return

        tenantRepository.upsertUsers(
            listOf(
                UserAccountUpsert(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    branchId = wire.branchId?.let { Uuid.parse(it) },
                    username = wire.username,
                    displayName = wire.displayName,
                    role = role,
                    active = wire.active,
                    pinHash = wire.pinHash,
                    serverSeq = serverSeq,
                ),
            ),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Mirrors the backend's `UserPullPayload`. */
@Serializable
private data class UserPullPayload(
    val id: String,
    val branchId: String? = null,
    val username: String,
    val displayName: String,
    val role: String,
    val active: Boolean = true,
    val pinHash: String? = null,
)
