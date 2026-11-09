package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.OutboxMessage
import com.f776.ecopan.data.database.repository.OutboxRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.domain.Role
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ProductionBatchRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val batches = ProductionBatchRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `create then re-push is idempotent and starts PENDING`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val batchId = Uuid.random()
        val first = batches.create(batchId, tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-phone-1")
        assertIs<IngestResult.Applied<*>>(first)
        assertEquals(DecrementStatus.PENDING, first.value.decrementStatus)

        val second = batches.create(batchId, tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), true, "baker-phone-1")
        assertIs<IngestResult.Duplicate<*>>(second)
        assertEquals(first.value.serverSeq, second.value.serverSeq)
    }

    /** #142: the batch and its announcement commit together, and a re-push never announces twice. */
    @Test
    fun `an applied batch enqueues its message in the same transaction and a duplicate does not`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val baker = users.create(Uuid.random(), tenantId, branch.id, "baker1", "hash", null, "Baker One", Role.BAKER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        val batchId = Uuid.random()

        repeat(2) { batches.create(batchId, tenantId, branch.id, baker.id, product.id, 10_000, Clock.System.now(), false, "baker-phone-1") }

        val published = mutableListOf<OutboxMessage>()
        OutboxRepositoryImpl(Clock.System).relayDue(10) { published += it }
        val message = Json.decodeFromString<ProductionBatchMessage>(published.single().payload)
        assertEquals(OutboxTopic.PRODUCTION_BATCH, published.single().topic)
        assertEquals(batchId.toString(), message.batchId)
        assertEquals(10_000, message.mixWeightGrams)
    }

    @Test
    fun `a batch whose transaction rolls back leaves no message`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        // No such baker: the insert fails on its foreign key, inside the same transaction as the enqueue.
        runCatching {
            batches.create(Uuid.random(), tenantId, branch.id, Uuid.random(), Uuid.random(), 10_000, Clock.System.now(), false, "phone")
        }

        val published = mutableListOf<OutboxMessage>()
        OutboxRepositoryImpl(Clock.System).relayDue(10) { published += it }
        assertEquals(emptyList(), published)
    }
}
