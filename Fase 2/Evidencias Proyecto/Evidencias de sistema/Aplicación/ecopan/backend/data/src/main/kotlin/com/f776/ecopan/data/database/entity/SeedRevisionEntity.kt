package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Which revision of each seed profile this database has had applied.
 *
 * Not a domain table: it carries no `server_seq` and is never synced to a client. It exists purely
 * so `SeedLoader` can tell "this profile has already been loaded" from "this profile has been
 * *edited* since it was loaded" — the two cases that a create-if-absent seeder cannot distinguish,
 * which is why a committed change to `reference.json` used to reach new installs only.
 */
object SeedRevisionTable : Table("seed_revisions") {
    val profile = varchar("profile", 64)
    val revision = integer("revision")
    val appliedAt = timestamp("applied_at")

    override val primaryKey = PrimaryKey(profile)
}
