package com.f776.ecopan.api.feature.tenant.domain

import at.favre.lib.crypto.bcrypt.BCrypt
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.tenant.controller.dto.BranchDto
import com.f776.ecopan.api.feature.tenant.controller.dto.TenantProfileResponse
import com.f776.ecopan.api.feature.tenant.controller.dto.UpdateModulesRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertBranchRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertUserRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UserListItemDto
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.database.repository.model.Branch
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Role
import kotlin.uuid.Uuid

/** Bcrypt cost 12 (02 §2), same KDF/cost for both web passwords and offline-unlock PINs. */
private const val BCRYPT_COST = 12

internal class TenantServiceImpl(
    private val tenantRepository: TenantRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository
) : TenantService {

    override suspend fun getProfile(tenantId: Uuid): TenantProfileResponse? {
        val tenant = tenantRepository.findById(tenantId) ?: return null
        val branches = branchRepository.listByTenant(tenantId)
        return tenant.toProfile(branches)
    }

    override suspend fun updateModules(tenantId: Uuid, request: UpdateModulesRequest): ServiceOutcome<TenantProfileResponse> {
        val modules = request.enabledModules.mapNotNull { name -> runCatching { Module.valueOf(name) }.getOrNull() }.toSet()
        if (modules.size != request.enabledModules.toSet().size) {
            return ServiceOutcome.Invalid("INVALID_MODULE", "One or more module names are unknown")
        }
        val tenant = tenantRepository.updateEnabledModules(tenantId, modules) ?: return ServiceOutcome.NotFound
        val branches = branchRepository.listByTenant(tenantId)
        return ServiceOutcome.Success(tenant.toProfile(branches))
    }

    override suspend fun listUsers(tenantId: Uuid): List<UserListItemDto> =
        userRepository.listByTenant(tenantId).map { it.toListItem() }

    override suspend fun createUser(tenantId: Uuid, request: UpsertUserRequest): ServiceOutcome<UserListItemDto> {
        val role = request.role.toRoleOrNull() ?: return ServiceOutcome.Invalid("INVALID_ROLE", "Unknown role: ${request.role}")
        val password = request.password
            ?: return ServiceOutcome.Invalid("PASSWORD_REQUIRED", "password is required to create a user")

        val branchValidation = validateBranch(tenantId, request.branchId)
        if (branchValidation is ServiceOutcome.Invalid) return branchValidation
        val branchId = (branchValidation as ServiceOutcome.Success).value

        val user = userRepository.create(
            id = Uuid.random(),
            tenantId = tenantId,
            branchId = branchId,
            username = request.username,
            passwordHash = hash(password),
            pinHash = request.pin?.let(::hash),
            displayName = request.displayName,
            role = role
        )
        return ServiceOutcome.Success(user.toListItem())
    }

    override suspend fun updateUser(tenantId: Uuid, userId: Uuid, request: UpsertUserRequest): ServiceOutcome<UserListItemDto> {
        val existing = userRepository.findById(userId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound

        val role = request.role.toRoleOrNull() ?: return ServiceOutcome.Invalid("INVALID_ROLE", "Unknown role: ${request.role}")

        val branchValidation = validateBranch(tenantId, request.branchId)
        if (branchValidation is ServiceOutcome.Invalid) return branchValidation
        val branchId = (branchValidation as ServiceOutcome.Success).value

        val updated = userRepository.update(
            id = userId,
            passwordHash = request.password?.let(::hash),
            pinHash = request.pin?.let(::hash),
            displayName = request.displayName,
            role = role,
            branchId = branchId,
            clearBranchId = branchId == null,
            active = request.active
        ) ?: return ServiceOutcome.NotFound
        return ServiceOutcome.Success(updated.toListItem())
    }

    override suspend fun listBranches(tenantId: Uuid, includeInactive: Boolean): List<BranchDto> =
        branchRepository.listByTenant(tenantId, includeInactive).map { it.toDto() }

    override suspend fun createBranch(tenantId: Uuid, request: UpsertBranchRequest): BranchDto {
        val branch = branchRepository.create(Uuid.random(), tenantId, request.name)
        return if (!request.active) {
            branchRepository.update(branch.id, active = false)!!.toDto()
        } else {
            branch.toDto()
        }
    }

    override suspend fun updateBranch(tenantId: Uuid, branchId: Uuid, request: UpsertBranchRequest): BranchDto? {
        val existing = branchRepository.findById(branchId)
        if (existing == null || existing.tenantId != tenantId) return null
        return branchRepository.update(branchId, name = request.name, active = request.active)?.toDto()
    }

    /**
     * `branchId` must reference an existing branch of the same tenant, or be null (01 §4 --
     * `branch_id` nullable means tenant-wide). `MULTI_BRANCH_ROLES` disabled doesn't add a stricter
     * check here beyond "the branch must already exist": that module gates *creating* additional
     * branch structure (T5), not which of the tenant's existing branches a user may be pinned to.
     */
    private suspend fun validateBranch(tenantId: Uuid, branchId: String?): ServiceOutcome<Uuid?> {
        if (branchId == null) return ServiceOutcome.Success(null)
        val parsed = runCatching { Uuid.parse(branchId) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_BRANCH", "branchId is not a valid uuid")
        val branch = branchRepository.findById(parsed)
        if (branch == null || branch.tenantId != tenantId) {
            return ServiceOutcome.Invalid("INVALID_BRANCH", "branchId does not belong to this tenant")
        }
        return ServiceOutcome.Success(parsed)
    }

    private fun hash(secret: String): String = BCrypt.withDefaults().hashToString(BCRYPT_COST, secret.toCharArray())

    private fun String.toRoleOrNull(): Role? = runCatching { Role.valueOf(this) }.getOrNull()

    private fun Tenant.toProfile(branches: List<Branch>) = TenantProfileResponse(
        id = id.toString(),
        name = name,
        slug = slug,
        currencyCode = currencyCode,
        enabledModules = enabledModules.map { it.name },
        branches = branches.map { it.toDto() }
    )

    private fun Branch.toDto() = BranchDto(id = id.toString(), name = name, active = active)

    private fun User.toListItem() = UserListItemDto(
        id = id.toString(),
        username = username,
        displayName = displayName,
        role = role.name,
        branchId = branchId?.toString(),
        active = active
    )
}
