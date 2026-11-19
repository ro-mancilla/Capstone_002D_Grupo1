package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.RolePermissionTable
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

internal class RolePermissionRepositoryImpl : RolePermissionRepository {

    override suspend fun loadAll(): Map<Role, Set<Permission>> = suspendTransaction {
        RolePermissionTable.selectAll()
            // Strict: a value outside the enums fails loudly here (the DB CHECK should make it impossible).
            .map { row -> row[RolePermissionTable.role] to row[RolePermissionTable.permission] }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }
}
