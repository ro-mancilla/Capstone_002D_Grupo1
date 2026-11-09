package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.Sale
import com.f776.ecopan.data.database.repository.model.SaleInput
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SaleRepository {
    /**
     * Sale + lines ingested atomically in one transaction (02 §4.1) -- idempotent by [id]
     * (`INSERT ... ON CONFLICT (id) DO NOTHING`); a repeat push of the same [id] is a [IngestResult.Duplicate]
     * and never re-inserts lines.
     */
    suspend fun createWithLines(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        cashierId: Uuid,
        soldAt: Instant,
        paymentMethod: PaymentMethod,
        totalMinor: Long,
        deviceId: String,
        lines: List<SaleLineInput>
    ): IngestResult<Sale>

    /**
     * Bulk-insert many sales and their lines in **one** transaction, skipping ids already present.
     *
     * Exists for boot-time seeding (plan 11 SEED-2), which inserts thousands of historical sales on
     * every container start. Doing that through [createWithLines] means one transaction and one
     * `SELECT nextval` round trip per sale, which turns a few seconds of work into minutes. This
     * path batches the rows and draws the sequence once per sale inside a single transaction.
     *
     * Deliberately **not** for the sync ingest path: that one needs per-sale
     * [IngestResult.Applied]/[IngestResult.Duplicate] reporting so a client learns which of its
     * pushes were new. Here the caller only needs the count actually written.
     *
     * @return how many sales were inserted (ids already present are skipped, making a re-seed a no-op).
     */
    suspend fun createAllWithLines(sales: List<SaleInput>): Int

    suspend fun findById(id: Uuid): Sale?

    /** Rows with `server_seq > since`, up to `limit + 1` (the extra row lets callers detect "more"). */
    suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<Sale>
}
