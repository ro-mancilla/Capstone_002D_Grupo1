package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.IngredientTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.Ingredient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class IngredientRepositoryImpl : IngredientRepository {

    override suspend fun create(
        id: Uuid,
        tenantId: Uuid,
        name: String,
        lowStockThresholdGrams: Long?
    ): Ingredient = suspendTransaction {
        val seq = nextServerSeq()
        IngredientTable.insert {
            it[IngredientTable.id] = id
            it[IngredientTable.tenantId] = tenantId
            it[IngredientTable.name] = name
            it[IngredientTable.lowStockThresholdGrams] = lowStockThresholdGrams
            it[IngredientTable.serverSeq] = seq
        }
        findByIdInternal(id)!!
    }

    override suspend fun findById(id: Uuid): Ingredient? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean): List<Ingredient> = suspendTransaction {
        IngredientTable.selectAll()
            .where {
                if (includeInactive) {
                    IngredientTable.tenantId eq tenantId
                } else {
                    (IngredientTable.tenantId eq tenantId) and (IngredientTable.active eq true)
                }
            }
            .map { it.toIngredient() }
    }

    override suspend fun update(
        id: Uuid,
        name: String?,
        lowStockThresholdGrams: Long?,
        active: Boolean?
    ): Ingredient? = suspendTransaction {
        val seq = nextServerSeq()
        IngredientTable.update({ IngredientTable.id eq id }) { stmt ->
            name?.let { stmt[IngredientTable.name] = it }
            lowStockThresholdGrams?.let { stmt[IngredientTable.lowStockThresholdGrams] = it }
            active?.let { stmt[IngredientTable.active] = it }
            stmt[IngredientTable.updatedAt] = Clock.System.now()
            stmt[IngredientTable.serverSeq] = seq
        }
        findByIdInternal(id)
    }

    private fun findByIdInternal(id: Uuid): Ingredient? =
        IngredientTable.selectAll().where { IngredientTable.id eq id }.singleOrNull()?.toIngredient()

    private fun ResultRow.toIngredient() = Ingredient(
        id = this[IngredientTable.id],
        tenantId = this[IngredientTable.tenantId],
        name = this[IngredientTable.name],
        lowStockThresholdGrams = this[IngredientTable.lowStockThresholdGrams],
        active = this[IngredientTable.active],
        serverSeq = this[IngredientTable.serverSeq],
        createdAt = this[IngredientTable.createdAt],
        updatedAt = this[IngredientTable.updatedAt]
    )
}
