package com.f776.ecopan.notifier

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.startHealthEndpoint
import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.notifier.di.NotifierModule
import com.f776.ecopan.notifier.domain.NotificationProcessor
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
    val batchEventBroker: BatchEventBroker by inject()
    val processor: NotificationProcessor by inject()
}

private val logger = LoggerFactory.getLogger("com.f776.ecopan.notifier.Main")

private val RECONNECT_BACKOFF = 5.seconds

fun main() = runBlocking {
    startKoin {
        modules(NotifierModule)
    }

    // Consumes existing tables; the API creates the schema (init = true).
    connectToDb(MainContainer.settings.databaseSettings)

    // Purely so Azure App Service sees a container that answers on its port; see
    // startHealthEndpoint. Harmless everywhere else.
    val health = startHealthEndpoint()

    val shutdown = CompletableDeferred<Unit>()
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received; stopping consumer")
        shutdown.complete(Unit)
    })

    val consumerJob = launch {
        while (isActive) {
            try {
                MainContainer.batchEventBroker.consumeProcessed { event ->
                    MainContainer.processor.handle(event)
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

    shutdown.await()
    consumerJob.cancelAndJoin()
    health.stop(0)
}
