package com.f776.ecopan.data.messagebroker.rabbitmq

import com.f776.ecopan.data.core.RabbitMqSettings
import dev.kourier.amqp.connection.AMQPConfig
import dev.kourier.amqp.connection.amqpConfig

/** Builds the kourier connection config from settings; shared by all brokers in this package. */
internal fun RabbitMqSettings.toAmqpConfig(): AMQPConfig = amqpConfig {
    server {
        host = this@toAmqpConfig.host
        port = this@toAmqpConfig.port
        user = this@toAmqpConfig.username
        password = this@toAmqpConfig.password
    }
}
