package com.f776.unigrid.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.f776.unigrid.core.database.converter.EntitlementModuleSetConverter
import com.f776.unigrid.core.database.converter.InstantConverter
import com.f776.unigrid.core.database.converter.LocalDateConverter
import com.f776.unigrid.core.database.converter.LocalTimeConverter
import com.f776.unigrid.core.database.converter.OutboxStateConverter
import com.f776.unigrid.core.database.converter.PaymentMethodConverter
import com.f776.unigrid.core.database.converter.ProductOriginConverter
import com.f776.unigrid.core.database.converter.RoleConverter
import com.f776.unigrid.core.database.converter.SoldByConverter
import com.f776.unigrid.core.database.converter.UUIDConverter
import com.f776.unigrid.core.database.dao.BranchDao
import com.f776.unigrid.core.database.dao.ForecastDao
import com.f776.unigrid.core.database.dao.FormulaDao
import com.f776.unigrid.core.database.dao.IngredientDao
import com.f776.unigrid.core.database.dao.NoteDao
import com.f776.unigrid.core.database.dao.OutboxDao
import com.f776.unigrid.core.database.dao.ProductDao
import com.f776.unigrid.core.database.dao.ProductionBatchDao
import com.f776.unigrid.core.database.dao.SaleDao
import com.f776.unigrid.core.database.dao.StockMovementDao
import com.f776.unigrid.core.database.dao.TenantDao
import com.f776.unigrid.core.database.dao.UserDao
import com.f776.unigrid.core.database.entity.BranchEntity
import com.f776.unigrid.core.database.entity.ForecastEntity
import com.f776.unigrid.core.database.entity.FormulaEntity
import com.f776.unigrid.core.database.entity.FormulaLineEntity
import com.f776.unigrid.core.database.entity.IngredientEntity
import com.f776.unigrid.core.database.entity.NoteEntity
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.ProductEntity
import com.f776.unigrid.core.database.entity.ProductionBatchEntity
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import com.f776.unigrid.core.database.entity.StockMovementEntity
import com.f776.unigrid.core.database.entity.TenantEntity
import com.f776.unigrid.core.database.entity.UserEntity

@Database(
    entities = [
        NoteEntity::class,
        TenantEntity::class,
        BranchEntity::class,
        UserEntity::class,
        ProductEntity::class,
        IngredientEntity::class,
        FormulaEntity::class,
        FormulaLineEntity::class,
        OutboxEntity::class,
        SaleEntity::class,
        SaleLineEntity::class,
        StockMovementEntity::class,
        ProductionBatchEntity::class,
        ForecastEntity::class,
    ],
    version = 14,
    autoMigrations = [
        // All purely additive (new tables only, or new nullable columns needing no default value)
        // — Room generates these from the exported schema diff, no hand-written SQL needed.
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        // 9: products.imageUrl -- a nullable column, so Room derives the migration itself.
        AutoMigration(from = 8, to = 9),
        // 10: the day-old bread feature was removed (team decision, 2026-09): its policy table
        // and the two per-line flags go. Deletions are ambiguous to Room's diff, so this one
        // names them in a spec.
        AutoMigration(from = 9, to = 10, spec = DropDayOldSpec::class),
        // 11: products.origin -- a NOT NULL column whose @ColumnInfo default (PANADERIA) lets Room derive it.
        AutoMigration(from = 10, to = 11),
        // 12: products.lowStockThresholdUnits / stockUnits -- two nullable columns, no default needed.
        AutoMigration(from = 11, to = 12),
        // 13: forecasts -- a new table (the baker's cached producción sugerida), so Room derives it.
        AutoMigration(from = 12, to = 13),
        // 14: sales.receiptNumber -- the per-till "Venta N.º" correlativo; a defaulted column, so Room derives it.
        AutoMigration(from = 13, to = 14),
    ],
)
@TypeConverters(
    LocalTimeConverter::class,
    LocalDateConverter::class,
    UUIDConverter::class,
    RoleConverter::class,
    EntitlementModuleSetConverter::class,
    InstantConverter::class,
    SoldByConverter::class,
    ProductOriginConverter::class,
    OutboxStateConverter::class,
    PaymentMethodConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tenantDao(): TenantDao
    abstract fun branchDao(): BranchDao
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun formulaDao(): FormulaDao
    abstract fun outboxDao(): OutboxDao
    abstract fun saleDao(): SaleDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun productionBatchDao(): ProductionBatchDao
    abstract fun forecastDao(): ForecastDao

    companion object {
        const val DATABASE_NAME = "app.db"
    }
}
