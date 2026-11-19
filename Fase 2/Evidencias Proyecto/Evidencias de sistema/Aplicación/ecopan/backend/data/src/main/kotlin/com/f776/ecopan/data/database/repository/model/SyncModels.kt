package com.f776.ecopan.data.database.repository.model

/**
 * One row of the pull merge-sort (02 §4.2). [data] is one of [Branch], [User], [Product],
 * [Ingredient], [Formula], [Sale], [ProductionBatch], [StockMovement] -- the app
 * layer switches on the runtime type to build the wire DTO for [entityType].
 */
data class PullRow(val entityType: String, val serverSeq: Long, val data: Any)

/**
 * [hasMore] is true when at least one source table had more rows beyond [SyncPullPage.rows]'s
 * trim-to-limit cut (02 §4.2 -- computed from over-fetching `limit + 1` per table before merging).
 */
data class SyncPullPage(val rows: List<PullRow>, val hasMore: Boolean)
