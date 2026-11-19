package com.f776.ecopan.data.sync.unittest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.f776.ecopan.data.sync.background.OutboxFlushWorker
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncError
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * 03 §7's Android hardening. The behaviour worth pinning is the *retry policy*: WorkManager retries
 * are a scarce, battery-relevant resource, so the worker must only ask for one when a retry could
 * actually accomplish something.
 */
// Pinned to 34, squeezed between two build-environment limits rather than chosen: the project
// targets SDK 37, which Robolectric 4.16.1 refuses outright (maxSdkVersion=36), and its SDK 35/36
// sandboxes require Java 21 while this build is on `jvmToolchain(17)` (CLAUDE.md). Nothing under
// test is SDK-sensitive -- this only selects the sandbox's API level -- so it costs no coverage.
// Revisit if the toolchain moves to 21.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxFlushWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class StubSyncEngine(private val result: SyncResult) : SyncEngine {
        var syncCalls = 0
            private set

        override val status: Flow<SyncStatus> = emptyFlow()

        override suspend fun syncNow(): SyncResult {
            syncCalls += 1
            return result
        }

        override suspend fun bootstrap(): BootstrapResult = BootstrapResult(success = true, error = null)
    }

    private fun pendingRow() = OutboxEntity(
        mutationId = Uuid.random(),
        entityType = "SALE",
        opType = "CREATE",
        payload = "{}",
        createdAt = Clock.System.now(),
        state = OutboxState.PENDING,
    )

    private suspend fun runWorker(engine: SyncEngine, outbox: FakeOutboxDao): ListenableWorker.Result =
        TestListenableWorkerBuilder<OutboxFlushWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters,
                    ) = OutboxFlushWorker(appContext, workerParameters, engine, outbox)
                },
            )
            .build()
            .doWork()

    @Test
    fun `an empty outbox succeeds without waking the sync engine`() = runTest {
        val engine = StubSyncEngine(SyncResult(pushed = 0, pulled = 0, error = null))

        val result = runWorker(engine, FakeOutboxDao())

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, engine.syncCalls, "nothing to send -- do not spend a wakeup on it")
    }

    @Test
    fun `a successful flush succeeds`() = runTest {
        val outbox = FakeOutboxDao().apply { insert(pendingRow()) }
        val engine = StubSyncEngine(SyncResult(pushed = 1, pulled = 0, error = null))

        assertEquals(ListenableWorker.Result.success(), runWorker(engine, outbox))
        assertEquals(1, engine.syncCalls)
    }

    @Test
    fun `a failed flush with rows still pending asks for a retry`() = runTest {
        val outbox = FakeOutboxDao().apply { insert(pendingRow()) }
        val engine = StubSyncEngine(
            SyncResult(pushed = 0, pulled = 0, error = SyncError("offline", retriable = true)),
        )

        assertEquals(ListenableWorker.Result.retry(), runWorker(engine, outbox))
    }

    /**
     * The push half drained the outbox and only the *pull* half failed. Retrying would re-run a
     * pull the foreground scheduler redoes anyway — not worth a second wakeup.
     */
    @Test
    fun `a failed flush that still drained the outbox does not retry`() = runTest {
        val outbox = FakeOutboxDao()
        val engine = object : SyncEngine {
            override val status: Flow<SyncStatus> = emptyFlow()
            override suspend fun syncNow(): SyncResult {
                outbox.clear()
                return SyncResult(pushed = 1, pulled = 0, error = SyncError("pull failed", retriable = true))
            }

            override suspend fun bootstrap() = BootstrapResult(success = true, error = null)
        }
        outbox.insert(pendingRow())

        assertEquals(ListenableWorker.Result.success(), runWorker(engine, outbox))
    }
}
