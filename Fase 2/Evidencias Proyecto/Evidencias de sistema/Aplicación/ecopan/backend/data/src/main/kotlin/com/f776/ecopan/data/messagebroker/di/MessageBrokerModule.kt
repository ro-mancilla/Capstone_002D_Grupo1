package com.f776.ecopan.data.messagebroker.di

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.BrokerBackend
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.data.messagebroker.SsePushBroker
import com.f776.ecopan.data.messagebroker.rabbitmq.RabbitBatchEventBroker
import com.f776.ecopan.data.messagebroker.rabbitmq.RabbitForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.rabbitmq.RabbitProductionBatchBroker
import com.f776.ecopan.data.messagebroker.rabbitmq.RabbitSsePushBroker
import com.f776.ecopan.data.messagebroker.servicebus.ServiceBusBatchEventBroker
import com.f776.ecopan.data.messagebroker.servicebus.ServiceBusForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.servicebus.ServiceBusProductionBatchBroker
import com.f776.ecopan.data.messagebroker.servicebus.ServiceBusRuntime
import com.f776.ecopan.data.messagebroker.servicebus.ServiceBusSsePushBroker
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.onClose
import org.koin.dsl.module

/**
 * Binds the four broker interfaces to one implementation family.
 *
 * The choice is made once, from [BaseSettings.brokerBackend], and nothing downstream knows which
 * family won — every consumer injects the interface. That is what turns "RabbitMQ in Docker
 * locally, Azure Service Bus in the cloud" into a configuration decision rather than a build
 * variant or a branch, and it is why the interfaces were worth having before there was a second
 * implementation to justify them.
 *
 * Koin resolves lazily, so the losing family is never constructed: under `RABBITMQ` no Service Bus
 * client is built and no connection string is needed, and under `SERVICE_BUS` nothing tries to
 * open an AMQP 0-9-1 connection to a host that is not there.
 */
internal val MessageBrokerModule = module {

    // Holds the shared AMQP 1.0 connection, the sender cache and the admin client. A singleton so
    // publishing does not pay for a fresh TLS handshake every time; closed with the Koin scope.
    single { ServiceBusRuntime(get<BaseSettings>().serviceBusSettings) } onClose { it?.close() }

    single<ProductionBatchBroker> {
        val settings = get<BaseSettings>()
        when (settings.brokerBackend) {
            BrokerBackend.RABBITMQ -> RabbitProductionBatchBroker(
                coroutineScope = get(named("defaultScope")),
                // Batch processing bounded to `prefetch` parallel batches, matching the AMQP
                // prefetch so the broker never delivers more work than the worker can process at
                // once. The Service Bus side gets the same bound from maxConcurrentCalls, which
                // the SDK enforces itself — hence no dispatcher to size here.
                jobDispatcher = Dispatchers.Default.limitedParallelism(
                    settings.rabbitMqSettings.prefetch
                ),
                config = settings.rabbitMqSettings
            )

            BrokerBackend.SERVICE_BUS -> ServiceBusProductionBatchBroker(
                runtime = get(),
                config = settings.serviceBusSettings
            )
        }
    }

    single<BatchEventBroker> {
        val settings = get<BaseSettings>()
        when (settings.brokerBackend) {
            BrokerBackend.RABBITMQ -> RabbitBatchEventBroker(
                scope = get(named("defaultScope")),
                config = settings.rabbitMqSettings
            )

            BrokerBackend.SERVICE_BUS -> ServiceBusBatchEventBroker(
                runtime = get(),
                config = settings.serviceBusSettings
            )
        }
    }

    single<ForecastRefreshBroker> {
        val settings = get<BaseSettings>()
        when (settings.brokerBackend) {
            BrokerBackend.RABBITMQ -> RabbitForecastRefreshBroker(
                scope = get(named("defaultScope")),
                config = settings.rabbitMqSettings
            )

            BrokerBackend.SERVICE_BUS -> ServiceBusForecastRefreshBroker(
                runtime = get(),
                config = settings.serviceBusSettings
            )
        }
    }

    single<SsePushBroker> {
        val settings = get<BaseSettings>()
        when (settings.brokerBackend) {
            BrokerBackend.RABBITMQ -> RabbitSsePushBroker(
                scope = get(named("defaultScope")),
                config = settings.rabbitMqSettings
            )

            BrokerBackend.SERVICE_BUS -> ServiceBusSsePushBroker(
                runtime = get(),
                config = settings.serviceBusSettings
            )
        }
    }
}
