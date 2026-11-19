package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role

interface RolePermissionRepository {
    /** Full grid, loaded once at startup into an in-memory map (01 §4, 02 §2). */
    suspend fun loadAll(): Map<Role, Set<Permission>>
}
