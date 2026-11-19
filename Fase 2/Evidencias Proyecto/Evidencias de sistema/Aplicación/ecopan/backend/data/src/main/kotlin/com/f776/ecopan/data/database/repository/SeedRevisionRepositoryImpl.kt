package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.SeedRevisionTable
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

class SeedRevisionRepositoryImpl : SeedRevisionRepository {

    override suspend fun appliedRevision(profile: String): Int? = suspendTransaction {
        SeedRevisionTable.selectAll()
            .where { SeedRevisionTable.profile eq profile }
            .singleOrNull()
            ?.get(SeedRevisionTable.revision)
    }

    override suspend fun recordApplied(profile: String, revision: Int) {
        suspendTransaction {
            val now = Clock.System.now()
            val updated = SeedRevisionTable.update({ SeedRevisionTable.profile eq profile }) {
                it[SeedRevisionTable.revision] = revision
                it[appliedAt] = now
            }
            if (updated == 0) {
                SeedRevisionTable.insert {
                    it[SeedRevisionTable.profile] = profile
                    it[SeedRevisionTable.revision] = revision
                    it[appliedAt] = now
                }
            }
        }
    }
}
