package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * The transactional outbox (#136): a message written in the same transaction as the state change it
 * announces, then published by an [com.f776.ecopan.data.outbox.OutboxRelay]. So a committed batch is
 * announced even if the broker was down when it arrived, and a rolled-back one never is.
 * Infra table -- no `server_seq`, never synced.
 */
object OutboxTable : Table("outbox_messages") {
    val id = uuid("id")
    /** [com.f776.ecopan.data.outbox.OutboxTopic] name: which publisher sends it. */
    val topic = varchar("topic", 64)
    /** The message, as its broker client serialises it. */
    val payload = text("payload")
    val createdAt = timestamp("created_at")
    /** Not before this: pushed back after a failed publish. */
    val availableAt = timestamp("available_at")
    val attempts = integer("attempts").default(0)
    val lastError = text("last_error").nullable()
    /** Set once published; the relay only looks at rows where this is null. */
    val sentAt = timestamp("sent_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_outbox_pending", false, sentAt, availableAt)
    }
}
