package com.f776.ecopan.data.database.repository

/** Persisted bookkeeping for [com.f776.ecopan.data.database.entity.SeedRevisionTable]. */
interface SeedRevisionRepository {
    /** The revision of [profile] this database last had applied, or `null` if it never has. */
    suspend fun appliedRevision(profile: String): Int?

    suspend fun recordApplied(profile: String, revision: Int)
}
