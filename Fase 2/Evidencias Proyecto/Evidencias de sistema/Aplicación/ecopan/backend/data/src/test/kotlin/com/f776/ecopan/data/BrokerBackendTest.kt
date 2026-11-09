package com.f776.ecopan.data

import com.f776.ecopan.data.core.BrokerBackend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [BrokerBackend.parse] is a one-line function guarding the single most consequential switch in the
 * deployment: it decides whether every service talks AMQP 0-9-1 to a container or AMQP 1.0 to Azure
 * Service Bus. Getting it wrong does not fail loudly at parse time — it silently selects the wrong
 * family, and the first symptom is a worker that connects to nothing and consumes nothing.
 *
 * Hence tests for what looks like a trivial `when`: the failure mode is disproportionate to the
 * code, which is exactly when a cheap test earns its keep.
 */
class BrokerBackendTest {

    @Test
    fun `an unset variable falls back to RabbitMQ`() {
        // The default matters: docker-compose sets no BROKER_BACKEND at all, and must keep working.
        assertEquals(BrokerBackend.RABBITMQ, BrokerBackend.parse(null))
        assertEquals(BrokerBackend.RABBITMQ, BrokerBackend.parse(""))
        assertEquals(BrokerBackend.RABBITMQ, BrokerBackend.parse("   "))
    }

    @Test
    fun `service bus is recognised however it is spelled in a shell script`() {
        listOf(
            "service_bus",
            "SERVICE_BUS",
            "service-bus",
            "Service Bus",
            "servicebus",
            "  service_bus  ",
            "azure_service_bus"
        ).forEach { spelling ->
            assertEquals(
                BrokerBackend.SERVICE_BUS,
                BrokerBackend.parse(spelling),
                "expected '$spelling' to select Service Bus"
            )
        }
    }

    @Test
    fun `rabbitmq is recognised however it is spelled`() {
        listOf("rabbitmq", "RABBITMQ", "rabbit_mq", "Rabbit").forEach { spelling ->
            assertEquals(
                BrokerBackend.RABBITMQ,
                BrokerBackend.parse(spelling),
                "expected '$spelling' to select RabbitMQ"
            )
        }
    }

    @Test
    fun `a typo fails loudly instead of quietly picking a default`() {
        // The important half. Falling back to RabbitMQ on "serviebus" would deploy to Azure with
        // every service dialling a hostname that does not resolve, and the error would surface as
        // a connection timeout three layers down rather than as "you misspelled a variable".
        val failure = assertFailsWith<IllegalArgumentException> { BrokerBackend.parse("serviebus") }
        assertEquals(true, failure.message?.contains("serviebus"))
    }
}
