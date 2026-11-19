package com.f776.ecopan.data.catalog.local.mapper

import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaLine
import com.f776.ecopan.domain.catalog.FormulaUpsert
import com.f776.unigrid.core.database.entity.FormulaEntity
import com.f776.unigrid.core.database.entity.FormulaLineEntity
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal fun toDomain(formula: FormulaEntity, lines: List<FormulaLineEntity>) = Formula(
    id = formula.id,
    tenantId = formula.tenantId,
    productId = formula.productId,
    active = formula.active,
    lines = lines.map { FormulaLine(it.ingredientId, it.basisPoints) },
)

internal fun FormulaUpsert.toEntity() = FormulaEntity(
    id = id,
    tenantId = tenantId,
    productId = productId,
    active = active,
    serverSeq = serverSeq,
    cachedAt = Clock.System.now(),
)

internal fun FormulaUpsert.lineEntities(): List<FormulaLineEntity> = lines.map { line ->
    FormulaLineEntity(
        id = Uuid.random(),
        tenantId = tenantId,
        formulaId = id,
        ingredientId = line.ingredientId,
        basisPoints = line.basisPoints,
    )
}
