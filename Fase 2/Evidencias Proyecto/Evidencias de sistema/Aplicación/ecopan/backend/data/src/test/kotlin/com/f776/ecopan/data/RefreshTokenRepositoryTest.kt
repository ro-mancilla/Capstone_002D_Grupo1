package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.RefreshTokenRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** #141: rotation is a conditional update, so two concurrent rotations of one token cannot both win. */
class RefreshTokenRepositoryTest : RepositoryTestBase() {

    private val tokens = RefreshTokenRepositoryImpl()
    private val userId = Uuid.random()

    @BeforeEach
    fun setUp() {
        truncateAll()
        runBlocking {
            val tenantId = Uuid.random()
            TenantRepositoryImpl().create(tenantId, "reference", "Reference Bakery", "CLP")
            UserRepositoryImpl().create(userId, tenantId, null, "u", "h", null, "U", Role.GENERAL_MANAGER)
        }
    }

    private suspend fun issue(familyId: Uuid? = null) = Uuid.random().let { id ->
        val now = Clock.System.now()
        tokens.create(id, userId, "hash-$id", "device-1", "Till", null, now, now + 30.days, familyId ?: id)
    }

    @Test
    fun `a login starts its own family and rotation inherits it`() = runTest {
        val first = issue()
        assertEquals(first.id, first.familyId)

        val now = Clock.System.now()
        val next = assertNotNull(tokens.rotate(first.id, Uuid.random(), "hash-next", now, now + 30.days))

        assertEquals(first.familyId, next.familyId)
        assertEquals("device-1", next.deviceId)
        assertNotNull(tokens.findByTokenHash(first.tokenHash)!!.revokedAt)
        assertNull(next.revokedAt)
    }

    @Test
    fun `two concurrent rotations of one token give exactly one winner`() {
        val first = runBlocking { issue() }
        val results = runBlocking(Dispatchers.IO) {
            (1..8).map { i ->
                async {
                    val now = Clock.System.now()
                    tokens.rotate(first.id, Uuid.random(), "hash-race-$i", now, now + 30.days)
                }
            }.awaitAll()
        }
        assertEquals(1, results.count { it != null }, "winners: $results")
    }

    @Test
    fun `a token already revoked cannot be rotated`() = runTest {
        val first = issue()
        tokens.revoke(first.id)
        val now = Clock.System.now()
        assertNull(tokens.rotate(first.id, Uuid.random(), "hash-late", now, now + 30.days))
        assertNull(tokens.findByTokenHash("hash-late"))
    }

    @Test
    fun `revoking a family revokes every token in it and no other`() = runTest {
        val root = issue()
        val sibling = issue(familyId = root.id)
        val stranger = issue()

        tokens.revokeFamily(root.familyId)

        assertNotNull(tokens.findByTokenHash(root.tokenHash)!!.revokedAt)
        assertNotNull(tokens.findByTokenHash(sibling.tokenHash)!!.revokedAt)
        assertNull(tokens.findByTokenHash(stranger.tokenHash)!!.revokedAt)
    }
}
