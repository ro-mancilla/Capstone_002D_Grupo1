package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.IngredientRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 02 §8: server_seq is one global sequence -- strictly increasing across mixed tables. */
class ServerSeqTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val ingredients = IngredientRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `server_seq is strictly increasing across different tables`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")

        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val ingredient1 = ingredients.create(Uuid.random(), tenantId, "Flour", null)
        val ingredient2 = ingredients.create(Uuid.random(), tenantId, "Salt", null)
        val branch2 = branches.create(Uuid.random(), tenantId, "Second")

        val seqs = listOf(branch.serverSeq, ingredient1.serverSeq, ingredient2.serverSeq, branch2.serverSeq)
        val sorted = seqs.sorted()

        assertTrue(seqs == sorted, "Expected strictly increasing sequence, got $seqs")
        assertTrue(seqs.toSet().size == seqs.size, "server_seq values must be unique: $seqs")
    }
}
