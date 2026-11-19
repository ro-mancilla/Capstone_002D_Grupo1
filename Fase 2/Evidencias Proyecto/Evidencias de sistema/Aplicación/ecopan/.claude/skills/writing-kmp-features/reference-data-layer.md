# Data Layer — Repositories, Room & DataStore

## Repository Pattern

```
┌─────────────────────┐
│ Repository interface│  ← public (api), Flow<T> for reads, suspend for writes
└──────────┬──────────┘
           │ delegates to
┌──────────▼──────────┐
│ RepositoryImpl      │  ← internal (impl), pure delegation shim
└──────────┬──────────┘
           │ delegates to
┌──────────▼──────────┐
│ Local*DataSource    │  ← internal interface (impl), abstracts over storage
└──────────┬──────────┘
           │ implemented by
┌──────────▼──────────┐
│ Db*DataSource       │  ← internal class (impl), Room DAO wrapper
└─────────────────────┘
```

### Repository interface (api module)

```kotlin
interface FooRepository {
    fun getFoos(): Flow<List<Foo>>
    fun getFooById(id: Int): Flow<Foo?>
    suspend fun createFoo(foo: CreateFoo)
    suspend fun updateFoo(foo: UpdateFoo)
    suspend fun deleteFoo(id: Int)
}
```

**Return type rules:**
- Reads: always `Flow<T>` (reactive, backed by Room Flow or DataStore Flow).
- Writes: always `suspend fun(): Unit` or `suspend fun(): Int`.
- Never expose Room entities through the repository — map to domain models.

### Repository implementation (impl module)

```kotlin
internal class FooRepositoryImpl(
    private val localDataSource: LocalFooDataSource
) : FooRepository {
    override fun getFoos(): Flow<List<Foo>> = localDataSource.getFoos()
    override suspend fun createFoo(foo: CreateFoo) = localDataSource.createFoo(foo)
}
```

Pure delegation. All mapping and DB logic lives in `Db*DataSource`.

## Room Patterns

### Entity

```kotlin
@Entity(
    tableName = "foo",
    foreignKeys = [ForeignKey(
        entity = BarEntity::class, parentColumns = ["id"],
        childColumns = ["bar_id"], onDelete = CASCADE
    )],
    indices = [Index("bar_id")]
)
internal data class FooEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "bar_id") val barId: Int,
    @ColumnInfo(name = "name") val name: String,
)
```

### DAO

```kotlin
@Dao
internal interface FooDao {
    @Query("SELECT * FROM foo WHERE bar_id = :barId")
    fun getFoosByBarId(barId: Int): Flow<List<FooEntity>>

    @Upsert
    suspend fun upsertFoo(foo: FooEntity): Long
}
```

### Database

```kotlin
@Database(entities = [FooEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fooDao(): FooDao
}

// commonMain
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
```

### Data source wrapping Room

Where mapping happens — use `mapRecursive` and `flowOn`:

```kotlin
internal class DbFooDataSource(
    private val fooDao: FooDao,
    private val defaultDispatcher: CoroutineDispatcher,
) : LocalFooDataSource {

    override fun getFoos(): Flow<List<Foo>> = fooDao.getAllFoos()
        .mapRecursive { it.toFoo() }
        .flowOn(defaultDispatcher)

    override suspend fun createFoo(foo: CreateFoo) = withContext(defaultDispatcher) {
        fooDao.upsertFoo(foo.toFooEntity())
    }
}
```

- Use `mapRecursive` (from `:core:common`) to map each list element.
- Use `flowOn(dispatcher)` to shift collection.
- Use `withContext(dispatcher)` for writes.

### Relations and projections

- Relationships: `data class FooRelation(@Embedded val foo: FooEntity, val bars: List<BarEntity>)`
- Projections: `data class FooProjection(@ColumnInfo(name = "col") val col: String)`
- Place in `local/relation/` or `local/projection/` subpackages.

## DataStore (Preferences) Pattern

All DataStore implementations follow this exact template:

```kotlin
internal class FooPreferencesDataSource(
    private val dataStore: DataStore<Preferences>
) : FooPreferencesDataSourceInterface {

    override val fooValue: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[FOO_KEY] ?: "default" }

    override suspend fun setFooValue(value: String) {
        dataStore.edit { preferences -> preferences[FOO_KEY] = value }
    }

    private companion object {
        val FOO_KEY = stringPreferencesKey("foo_key")
    }
}
```

**Key conventions:**
- Always include `.catch { if (it is IOException) emit(emptyPreferences()) }`.
- Store keys as `private companion object` vals.
- Key types: `booleanPreferencesKey()`, `intPreferencesKey()`, `stringPreferencesKey()`, `stringSetPreferencesKey()`.
- The DataStore `single` is provided with qualifier `named("datastore")` from `:core:datastore`.

## Mapper Conventions

`internal` extension functions in `local/mapper/`:

```kotlin
// Entity → Domain
internal fun FooEntity.toFoo(): Foo = Foo(id = id, name = name)

// Domain → Entity
internal fun CreateFoo.toFooEntity(): FooEntity = FooEntity(name = name)

// Relation → Domain
internal fun FooRelation.toFoo(): Foo = foo.toFoo()
```

- Extension functions, not companion methods.
- One direction per function: `toXxx()` for entity→domain, `toXxxEntity()` for domain→entity.
- Always `internal`.
