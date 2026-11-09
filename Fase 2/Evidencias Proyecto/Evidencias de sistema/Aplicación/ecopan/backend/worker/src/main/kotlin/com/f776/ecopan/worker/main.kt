package com.f776.ecopan.worker

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.startHealthEndpoint
import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.database.ensureOutbox
import com.f776.ecopan.data.database.ensureScheduledRuns
import com.f776.ecopan.data.database.repository.OutboxRepository
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.data.outbox.OutboxRelay
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.serialization.json.Json
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.worker.di.WorkerModule
import com.f776.ecopan.worker.domain.DailyForecastScheduler
import com.f776.ecopan.worker.domain.ForecastRefreshProcessor
import com.f776.ecopan.worker.domain.FormulaDecrementProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory
import kotlin.getValue
import kotlin.time.Duration.Companion.seconds

object MainContainer : KoinComponent {
    val settings: BaseSettings by inject()
    val productionBatchBroker: ProductionBatchBroker by inject()
    val processor: FormulaDecrementProcessor by inject()
    val forecastRefreshBroker: ForecastRefreshBroker by inject()
    val forecastRefreshProcessor: ForecastRefreshProcessor by inject()
    val dailyForecastScheduler: DailyForecastScheduler by inject()
    val outbox: OutboxRepository by inject()
    val batchEventBroker: BatchEventBroker by inject()
}

private val logger = LoggerFactory.getLogger("com.f776.ecopan.worker.Main")

private val RECONNECT_BACKOFF = 5.seconds
private val SCHEDULER_TICK = 60.seconds

fun main() = runBlocking {
    startKoin {
        modules(WorkerModule)
    }

    connectToDb(MainContainer.settings.databaseSettings)
    // The app owns schema init and may not have run yet; the scheduler's claim table must exist.
    ensureScheduledRuns()
    // ... and the outbox the decrement writes its BatchProcessed notice to (#143).
    ensureOutbox()

    // Purely so Azure App Service sees a container that answers on its port; see
    // startHealthEndpoint. Harmless everywhere else.
    val health = startHealthEndpoint()

    // Signalled by the JVM shutdown hook (SIGTERM / Ctrl-C) to unblock graceful shutdown below.
    val shutdown = CompletableDeferred<Unit>()
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received; stopping consumer")
        shutdown.complete(Unit)
    })

    // Consume forever, reconnecting with backoff if the broker connection drops, so the worker
    // recovers on its own instead of sitting idle after a broker restart. Load-bearing for the
    // RabbitMQ backend, which has no recovery of its own; under Service Bus the SDK's processor
    // client reconnects internally and this loop only re-enters on a failure it judged terminal.
    val consumerJob = launch {
        while (isActive) {
            try {
                MainContainer.productionBatchBroker.consume { message ->
                    MainContainer.processor.processMessage(message)
                }
                logger.warn("Consumer ended; reconnecting in $RECONNECT_BACKOFF")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Consumer failed; reconnecting in $RECONNECT_BACKOFF", e)
            }
            delay(RECONNECT_BACKOFF)
        }
    }

    // Second, independent consume loop for F2 forecast-refresh requests (02 §9 item 7) -- stubbed
    // until `08-ml-forecasting.md`, but wired end to end now.
    val forecastConsumerJob = launch {
        while (isActive) {
            try {
                MainContainer.forecastRefreshBroker.consume { message ->
                    MainContainer.forecastRefreshProcessor.processMessage(message)
                }
                logger.warn("Forecast-refresh consumer ended; reconnecting in $RECONNECT_BACKOFF")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Forecast-refresh consumer failed; reconnecting in $RECONNECT_BACKOFF", e)
            }
            delay(RECONNECT_BACKOFF)
        }
    }

    // Daily forecast refresh trigger. Every replica ticks; the DB claim inside tick() makes the
    // refresh fire once per local day across all of them.
    val schedulerJob = launch {
        while (isActive) {
            try {
                MainContainer.dailyForecastScheduler.tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Daily forecast scheduler tick failed; retrying in $SCHEDULER_TICK", e)
            }
            delay(SCHEDULER_TICK)
        }
    }

    // Publishes the BatchProcessed notices completeDecrement wrote, in its own transaction, to the
    // outbox (#143). Every replica runs one; SKIP LOCKED keeps them from sending a row twice.
    val relayJob = OutboxRelay(
        MainContainer.outbox,
        mapOf(OutboxTopic.BATCH_PROCESSED to { payload -> MainContainer.batchEventBroker.publishProcessed(Json.decodeFromString<BatchProcessed>(payload)) }),
    ).start(this)

    shutdown.await()
    // Stop consuming and let any in-flight, unacked messages return to the queue.
    consumerJob.cancelAndJoin()
    forecastConsumerJob.cancelAndJoin()
    schedulerJob.cancelAndJoin()
    relayJob.cancelAndJoin()
    health.stop(0)
}
