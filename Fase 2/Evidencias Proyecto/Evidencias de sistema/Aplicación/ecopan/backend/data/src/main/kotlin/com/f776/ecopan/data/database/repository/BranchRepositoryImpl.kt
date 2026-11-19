package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.BranchTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.Branch
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

internal class BranchRepositoryImpl : BranchRepository {

    override suspend fun create(id: Uuid, tenantId: Uuid, name: String): Branch = suspendTransaction {
        val seq = nextServerSeq()
        BranchTable.insert {
            it[BranchTable.id] = id
            it[BranchTable.tenantId] = tenantId
            it[BranchTable.name] = name
            it[BranchTable.serverSeq] = seq
        }
        findByIdInternal(id)!!
    }

    override suspend fun findById(id: Uuid): Branch? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean): List<Branch> = suspendTransaction {
        BranchTable.selectAll()
            .where {
                if (includeInactive) {
                    BranchTable.tenantId eq tenantId
                } else {
                    (BranchTable.tenantId eq tenantId) and (BranchTable.active eq true)
                }
            }
            .map { it.toBranch() }
    }

    override suspend fun update(id: Uuid, name: String?, active: Boolean?): Branch? = suspendTransaction {
        val seq = nextServerSeq()
        BranchTable.update({ BranchTable.id eq id }) { stmt ->
            name?.let { stmt[BranchTable.name] = it }
            active?.let { stmt[BranchTable.active] = it }
            stmt[BranchTable.serverSeq] = seq
        }
        findByIdInternal(id)
    }

    private fun findByIdInternal(id: Uuid): Branch? =
        BranchTable.selectAll().where { BranchTable.id eq id }.singleOrNull()?.toBranch()

    private fun ResultRow.toBranch() = Branch(
        id = this[BranchTable.id],
        tenantId = this[BranchTable.tenantId],
        name = this[BranchTable.name],
        active = this[BranchTable.active],
        serverSeq = this[BranchTable.serverSeq],
        createdAt = this[BranchTable.createdAt]
    )
}
