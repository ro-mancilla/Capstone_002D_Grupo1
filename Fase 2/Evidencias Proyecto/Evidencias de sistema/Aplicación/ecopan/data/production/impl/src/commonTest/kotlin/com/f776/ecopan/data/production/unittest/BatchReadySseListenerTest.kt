package com.f776.ecopan.data.production.unittest

import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.data.production.sse.BatchReadySseListener
import com.f776.ecopan.data.production.unittest.fake.FakeProductRepository
import com.f776.ecopan.data.production.unittest.fake.FakeSyncEventStream
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.production.BatchReadyStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 06 §6's live path. Note what is *not* asserted anywhere here: a database write. The SSE payload
 * carries no `bakerId`/`tenantId`, so this listener announces only — `ProductionBatchSyncApplier`
 * owns persistence. See the listener's KDoc.
 *
 * Uses `runCurrent()`, never `advanceUntilIdle()`: the listener runs in `backgroundScope` (its
 * collect loop never completes, and `runTest` waits on its own children), and `advanceUntilIdle`
 * does not dispatch background work — every assertion here silently saw zero events until this
 * was corrected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchReadySseListenerTest {

    private val batchId = Uuid.random()
    private val branchId = Uuid.random()
    private val productId = Uuid.random()

    private fun batchReadyFrame(
        id: Uuid = batchId,
        productType: String? = "Pan",
    ) = buildString {
        append("""{"batchId":"$id","branchId":"$branchId","productId":"$productId",""")
        append(""""mixWeightGrams":10000,"batchAt":1753000000000,"decrementStatus":"APPLIED"""")
        if (productType != null) append(""","productType":"$productType"""")
        append("}")
    }

    private fun TestScope.listener(
        stream: FakeSyncEventStream,
        bus: BatchReadyBus,
        products: FakeProductRepository = FakeProductRepository(),
    ): BatchReadyStream = BatchReadySseListener(
        eventStream = stream,
        batchReadyBus = bus,
        productRepository = products,
        // backgroundScope, not `this`: both the listener's collect loop and the bus collector
        // below are non-terminating by design, and runTest waits on its own children.
        scope = backgroundScope,
    )

    private fun TestScope.collectAnnouncements(bus: BatchReadyBus): MutableList<BatchReadyAnnouncement> {
        val seen = mutableListOf<BatchReadyAnnouncement>()
        backgroundScope.launch { bus.feed.collect { seen += it } }
        return seen
    }

    @Test
    fun `a batch-ready frame becomes an announcement`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)

        listener(stream, bus).start()
        runCurrent()
        stream.emit("batch-ready", batchReadyFrame())
        runCurrent()

        assertEquals(1, seen.size)
        assertEquals(batchId, seen.single().batchId)
        assertEquals(10_000L, seen.single().mixWeightGrams)
    }

    @Test
    fun `non batch-ready frames are ignored`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)

        listener(stream, bus).start()
        runCurrent()
        stream.emit("sync-available", """{"anything":true}""")
        stream.emit("heartbeat", "")
        runCurrent()

        assertTrue(seen.isEmpty())
    }

    /** `EventsRouter` replays the last 24h on every reconnect; without id de-duplication a flaky
     *  link would repaint the counter's banner stack each time. */
    @Test
    fun `snapshot replay after a reconnect does not re-announce`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)

        listener(stream, bus).start()
        runCurrent()
        stream.emit("batch-ready", batchReadyFrame())
        stream.emit("batch-snapshot", batchReadyFrame()) // same batch, replayed on reconnect
        runCurrent()

        assertEquals(1, seen.size, "announced once despite arriving twice")
    }

    /** The snapshot-on-connect payload has no `productType`; requiring it would silently drop
     *  every replayed frame. */
    @Test
    fun `snapshot-shaped frames without productType still decode`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)

        listener(stream, bus).start()
        runCurrent()
        stream.emit("batch-snapshot", batchReadyFrame(productType = null))
        runCurrent()

        assertEquals(1, seen.size)
        assertEquals("", seen.single().productTypeName, "no local product, no server name -- blank, not a crash")
    }

    @Test
    fun `a malformed frame is skipped without killing the stream`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)

        listener(stream, bus).start()
        runCurrent()
        stream.emit("batch-ready", """{"nonsense":true}""")
        stream.emit("batch-ready", batchReadyFrame())
        runCurrent()

        assertEquals(1, seen.size, "the good frame after the bad one still arrives")
        assertEquals(batchId, seen.single().batchId)
    }

    @Test
    fun `start is idempotent and stop ends the subscription`() = runTest {
        val stream = FakeSyncEventStream()
        val bus = BatchReadyBus()
        val seen = collectAnnouncements(bus)
        val listener = listener(stream, bus)

        listener.start()
        listener.start()
        runCurrent()
        assertEquals(1, stream.subscriptions, "a second start must not open a second connection")

        listener.stop()
        runCurrent()
        stream.emit("batch-ready", batchReadyFrame())
        runCurrent()

        assertTrue(seen.isEmpty(), "nothing arrives once stopped")
    }
}
