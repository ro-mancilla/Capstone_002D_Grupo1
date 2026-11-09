package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.UserTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Role
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

internal class UserRepositoryImpl : UserRepository {

    override suspend fun create(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid?,
        username: String,
        passwordHash: String,
        pinHash: String?,
        displayName: String,
        role: Role
    ): User = suspendTransaction {
        val seq = nextServerSeq()
        UserTable.insert {
            it[UserTable.id] = id
            it[UserTable.tenantId] = tenantId
            it[UserTable.branchId] = branchId
            it[UserTable.username] = username
            it[UserTable.passwordHash] = passwordHash
            it[UserTable.pinHash] = pinHash
            it[UserTable.displayName] = displayName
            it[UserTable.role] = role
            it[UserTable.serverSeq] = seq
        }
        findByIdInternal(id)!!
    }

    override suspend fun findById(id: Uuid): User? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun findByTenantAndUsername(tenantId: Uuid, username: String): User? = suspendTransaction {
        UserTable.selectAll()
            .where { (UserTable.tenantId eq tenantId) and (UserTable.username eq username) }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun listByTenant(tenantId: Uuid): List<User> = suspendTransaction {
        UserTable.selectAll().where { UserTable.tenantId eq tenantId }.map { it.toUser() }
    }

    override suspend fun update(
        id: Uuid,
        passwordHash: String?,
        pinHash: String?,
        displayName: String?,
        role: Role?,
        branchId: Uuid?,
        clearBranchId: Boolean,
        active: Boolean?
    ): User? = suspendTransaction {
        val seq = nextServerSeq()
        UserTable.update({ UserTable.id eq id }) { stmt ->
            passwordHash?.let { stmt[UserTable.passwordHash] = it }
            pinHash?.let { stmt[UserTable.pinHash] = it }
            displayName?.let { stmt[UserTable.displayName] = it }
            role?.let { stmt[UserTable.role] = it }
            if (clearBranchId) {
                stmt[UserTable.branchId] = null
            } else {
                branchId?.let { stmt[UserTable.branchId] = it }
            }
            active?.let { stmt[UserTable.active] = it }
            stmt[UserTable.serverSeq] = seq
        }
        findByIdInternal(id)
    }

    private fun findByIdInternal(id: Uuid): User? =
        UserTable.selectAll().where { UserTable.id eq id }.singleOrNull()?.toUser()

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        tenantId = this[UserTable.tenantId],
        branchId = this[UserTable.branchId],
        username = this[UserTable.username],
        passwordHash = this[UserTable.passwordHash],
        pinHash = this[UserTable.pinHash],
        displayName = this[UserTable.displayName],
        role = this[UserTable.role],
        active = this[UserTable.active],
        serverSeq = this[UserTable.serverSeq],
        createdAt = this[UserTable.createdAt]
    )
}
