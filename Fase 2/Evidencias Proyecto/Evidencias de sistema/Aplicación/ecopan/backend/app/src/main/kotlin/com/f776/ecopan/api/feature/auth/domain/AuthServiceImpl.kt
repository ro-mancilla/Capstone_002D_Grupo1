package com.f776.ecopan.api.feature.auth.domain

import at.favre.lib.crypto.bcrypt.BCrypt
import com.f776.ecopan.api.core.JwtSettings
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.feature.auth.controller.dto.BranchSummaryDto
import com.f776.ecopan.api.feature.auth.controller.dto.LoginResponse
import com.f776.ecopan.api.feature.auth.controller.dto.RefreshResponse
import com.f776.ecopan.api.feature.auth.controller.dto.TenantSummaryDto
import com.f776.ecopan.api.feature.auth.controller.dto.UserSummaryDto
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.RefreshTokenRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.database.repository.model.User
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

private val log = LoggerFactory.getLogger("com.f776.ecopan.api.auth")

internal class AuthServiceImpl(
    private val jwtSettings: JwtSettings,
    private val deviceSessionTtlDays: Int,
    private val tenantRepository: TenantRepository,
    private val userRepository: UserRepository,
    private val branchRepository: BranchRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) : AuthService {

    private val secureRandom = SecureRandom()

    override suspend fun login(
        tenantSlug: String,
        username: String,
        password: String,
        deviceId: String?,
        deviceName: String?
    ): LoginOutcome {
        val tenant = tenantRepository.findBySlug(tenantSlug) ?: return LoginOutcome.InvalidCredentials
        val user = userRepository.findByTenantAndUsername(tenant.id, username)
            ?: return LoginOutcome.InvalidCredentials

        if (!user.active) return LoginOutcome.InvalidCredentials
        if (!verify(password, user.passwordHash)) return LoginOutcome.InvalidCredentials

        val now = Clock.System.now()
        val ttlDays = if (deviceId != null) deviceSessionTtlDays else jwtSettings.refreshTtlDays
        val (rawRefreshToken, refreshHash) = generateOpaqueToken()

        refreshTokenRepository.create(
            id = Uuid.random(),
            userId = user.id,
            tokenHash = refreshHash,
            deviceId = deviceId,
            deviceName = deviceName,
            branchId = user.branchId,
            issuedAt = now,
            expiresAt = now + ttlDays.days
        )

        val accessToken = mintAccessToken(
            settings = jwtSettings,
            userId = user.id,
            tenantId = tenant.id,
            branchId = user.branchId,
            role = user.role,
            username = user.username
        )

        val branches = branchRepository.listByTenant(tenant.id)

        return LoginOutcome.Success(
            LoginResponse(
                accessToken = accessToken,
                refreshToken = rawRefreshToken,
                user = user.toDto(),
                tenant = TenantSummaryDto(
                    id = tenant.id.toString(),
                    name = tenant.name,
                    currencyCode = tenant.currencyCode,
                    enabledModules = tenant.enabledModules.map { it.name }
                ),
                branches = branches.map { BranchSummaryDto(it.id.toString(), it.name) }
            )
        )
    }

    /**
     * Rotation (02 §2), made safe against two kinds of second use (#141):
     *
     * - **Concurrent**: two requests carrying the same live token. [RefreshTokenRepository.rotate] is
     *   a conditional update, so exactly one wins; the other is refused like any invalid token. Both
     *   clients serialise their own refreshes (the dashboard across tabs, the till per device), so
     *   there is no grace window -- a duplicate is a network retry, and refusing it costs one sign-in.
     * - **Later reuse**: a token that was already rotated away. Only a copy can still hold it, so the
     *   whole family is revoked: the thief and the real holder both have to sign in again.
     */
    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val hash = sha256(refreshToken)
        val record = refreshTokenRepository.findByTokenHash(hash) ?: return RefreshOutcome.Invalid
        val now = Clock.System.now()

        if (record.revokedAt != null) {
            refreshTokenRepository.revokeFamily(record.familyId)
            log.warn(
                "refresh token reuse: revoked its family {} (user {}, device {})",
                record.familyId, record.userId, record.deviceId ?: "browser",
            )
            return RefreshOutcome.Invalid
        }
        if (record.expiresAt < now) return RefreshOutcome.Invalid

        val user = userRepository.findById(record.userId) ?: return RefreshOutcome.Invalid
        if (!user.active) return RefreshOutcome.Invalid

        val ttlDays = if (record.deviceId != null) deviceSessionTtlDays else jwtSettings.refreshTtlDays
        val (rawRefreshToken, refreshHash) = generateOpaqueToken()
        refreshTokenRepository.rotate(
            oldId = record.id,
            newId = Uuid.random(),
            newTokenHash = refreshHash,
            issuedAt = now,
            expiresAt = now + ttlDays.days,
        ) ?: return RefreshOutcome.Invalid

        val accessToken = mintAccessToken(
            settings = jwtSettings,
            userId = user.id,
            tenantId = user.tenantId,
            branchId = user.branchId,
            role = user.role,
            username = user.username
        )

        return RefreshOutcome.Success(RefreshResponse(accessToken, rawRefreshToken))
    }

    override suspend fun logout(refreshToken: String) {
        val hash = sha256(refreshToken)
        val record = refreshTokenRepository.findByTokenHash(hash) ?: return
        refreshTokenRepository.revoke(record.id)
    }

    private fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    private fun generateOpaqueToken(): Pair<String, String> {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return raw to sha256(raw)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun User.toDto() = UserSummaryDto(
        id = id.toString(),
        displayName = displayName,
        role = role.name,
        branchId = branchId?.toString()
    )
}
