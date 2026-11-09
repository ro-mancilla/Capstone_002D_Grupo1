package com.f776.ecopan.core.domain.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RolePermissionsTest {

    @Test
    fun `cashier can record sales but not adjust stock`() {
        assertTrue(RolePermissions.has(Role.CASHIER, Permission.SALES_RECORD))
        assertFalse(RolePermissions.has(Role.CASHIER, Permission.STOCK_ADJUST))
    }

    @Test
    fun `baker can record batches and read stock but not adjust it`() {
        assertTrue(RolePermissions.has(Role.BAKER, Permission.BATCH_RECORD))
        assertTrue(RolePermissions.has(Role.BAKER, Permission.STOCK_READ))
        assertFalse(RolePermissions.has(Role.BAKER, Permission.STOCK_ADJUST))
    }

    @Test
    fun `branch manager has cashier and baker permissions plus stock adjust and analytics`() {
        assertTrue(RolePermissions.has(Role.BRANCH_MANAGER, Permission.SALES_RECORD))
        assertTrue(RolePermissions.has(Role.BRANCH_MANAGER, Permission.BATCH_RECORD))
        assertTrue(RolePermissions.has(Role.BRANCH_MANAGER, Permission.STOCK_ADJUST))
        assertTrue(RolePermissions.has(Role.BRANCH_MANAGER, Permission.ANALYTICS_READ))
        assertFalse(RolePermissions.has(Role.BRANCH_MANAGER, Permission.MODULES_TOGGLE))
    }

    @Test
    fun `general manager and admin have every permission`() {
        for (permission in Permission.entries) {
            assertTrue(RolePermissions.has(Role.GENERAL_MANAGER, permission), "GM missing $permission")
            assertTrue(RolePermissions.has(Role.ADMIN, permission), "ADMIN missing $permission")
        }
    }

    @Test
    fun `branch-scoped roles are exactly cashier baker and branch manager`() {
        assertTrue(Role.CASHIER.isBranchScoped)
        assertTrue(Role.BAKER.isBranchScoped)
        assertTrue(Role.BRANCH_MANAGER.isBranchScoped)
        assertFalse(Role.GENERAL_MANAGER.isBranchScoped)
        assertFalse(Role.ADMIN.isBranchScoped)
    }
}
