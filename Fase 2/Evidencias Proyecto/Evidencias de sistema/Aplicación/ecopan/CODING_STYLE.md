# KMP Compose Multiplatform — Coding Style & Architecture Guide

This document describes the architecture, coding conventions, and patterns for building features in a Kotlin Multiplatform (KMP) Compose Multiplatform application. Use this guide when designing and implementing new features, data sources, or platform integrations.

---

## 1. Guiding Principles

- **Clean architecture boundaries** — domain interfaces live in `api` modules; implementations live in `impl` modules.
- **Encapsulation by default** — implementation classes, factories, mappers, and data sources are all `internal`. Only interfaces and DI modules are public.
- **No hardcoded platform APIs** — platform-specific behaviors go through `expect`/`actual` declarations or injected platform modules.
- **No hardcoded dispatchers** — all `CoroutineDispatcher` instances are injected via Koin named qualifiers.
- **Unidirectional data flow (UDF)** — screens emit sealed actions to view models; view models emit state and side-effect events.
- **Composable previews** — every screen composable has at least one preview annotated with `@LocalizedPreview` and/or `@AdaptivePreview`.

---

## 2. Module Architecture

### 2.1 Module layout

Every feature and every data domain follows an **`api`/`impl` split**:

```
:feature:<name>:api      — Public interfaces (NavKey routes, repository contracts, domain models)
:feature:<name>:impl     — Implementation (ViewModels, screens, DI modules, platform wiring)
:data:<name>:api         — Public interfaces (Repository, DataSource contracts, domain models)
:data:<name>:impl        — Implementation (Room-backed sources, DataStore-backed sources, mappers)
:core:<name>             — Shared infrastructure (database, datastore, locale, ui, test utilities)
```

### 2.2 Dependency direction

```
Entry point (app module)
  └─> feature:impl modules
        └─> feature:api modules
        └─> data:impl modules
              └─> data:api modules
              └─> core:* modules
```

- `api` modules have no dependencies on `impl` modules.
- `core` modules are standalone shared infrastructure.
- Feature `impl` modules depend on `data:api` (never `data:impl`) — all data access goes through repository interfaces.

### 2.3 Packaging

Packages are derived from the module path. For a module at `:feature:foo:api`:

| Content | Package |
|---------|---------|
| Public interfaces | `com.example.domain.foo` |
| NavKey routes | `com.example.feature.foo.route` |

For a module at `:feature:foo:impl`:

| Content | Package |
|---------|---------|
| DI module | `com.example.feature.foo.di` |
| ViewModels | `com.example.feature.foo` |
| Screens / UI | `com.example.feature.foo.ui` |
| Mappers | `com.example.feature.foo.mapper` |

For a module at `:data:foo:api`:

| Content | Package |
|---------|---------|
| Repository/data source interfaces | `com.example.domain.foo` |

For a module at `:data:foo:impl`:

| Content | Package |
|---------|---------|
| DI module | `com.example.data.foo.di` |
| Repository implementation | `com.example.data.foo.repository` |
| Local data source (internal interface) | `com.example.data.foo.local` |
| Room-backed data source | `com.example.data.foo.local` |
| Mappers | `com.example.data.foo.local.mapper` |

---

## 3. Encapsulation & Visibility

**The core rule: expose interfaces, hide implementations.**

### 3.1 Visibility modifier usage

| Element | Visibility | Module |
|---------|-----------|--------|
| Repository / DataSource **interfaces** | `public` (default) | `:data:foo:api`, `:feature:bar:api` |
| NavKey route classes | `public` (default) | `:feature:bar:api` |
| Domain model data classes | `public` (default) | `:data:foo:api`, `:feature:bar:api` |
| Repository **implementations** | `internal` | `:data:foo:impl` |
| Local data source interfaces | `internal` | `:data:foo:impl` |
| Room DAOs and entities | `internal` (or `public` for Room constructor) | `:data:foo:impl`, `:core:database` |
| Room-backed data source classes | `internal` | `:data:foo:impl` |
| Mapper functions | `internal` | `:data:foo:impl`, `:feature:bar:impl` |
| ViewModels | `internal` | `:feature:bar:impl` |
| Screen composables | `internal` | `:feature:bar:impl` |
| DI modules (Koin `Module`) | `public` (default) | `:data:foo:impl`, `:feature:bar:impl` |
| `PlatformModule` (expect val) | `internal` | `:data:foo:impl`, `:feature:bar:impl` |
| expect/actual classes | `internal` | `:data:foo:impl`, `:core:*` |

### 3.2 Rationale

- **DI modules are public** so the root application module can `include()` them. They are the only public surface of `impl` modules.
- **Everything else is `internal`** to enforce that consumers of a module interact only through its public interfaces (from the `api` module). Even within `impl`, data sources, mappers, and ViewModels are internal.
- The compiler prevents importing `internal` declarations across module boundaries, ensuring the dependency graph and interface contracts are respected.

### 3.3 Example

```
// :feature:foo:api — public surface
public interface FooRepository {
    fun getFoos(): Flow<List<Foo>>
    suspend fun createFoo(foo: CreateFoo)
}
public data class Foo(val id: Int, val name: String)

// :feature:foo:impl — all internal except DI module
internal class FooRepositoryImpl(
    private val localDataSource: LocalFooDataSource
) : FooRepository { ... }

internal interface LocalFooDataSource {
    fun getFoos(): Flow<List<FooEntity>>
}
internal class DbFooDataSource(private val dao: FooDao) : LocalFooDataSource { ... }

// Only this is public in the impl module:
public val FooDataModule = module {
    factoryOf(::DbFooDataSource).bind<LocalFooDataSource>()
    factoryOf(::FooRepositoryImpl).bind<FooRepository>()
}
```

---

## 4. Dependency Injection (Koin)

### 4.1 Module hierarchy

DI modules form a tree via `includes()`:

```kotlin
// Root AppModule (in the entry-point module)
val AppModule = module {
    includes(FeatureAModule, FeatureBModule, PreferencesModule, ReminderModule)
    // Global dispatchers
    single(named("ioDispatcher")) { Dispatchers.IO }
    single(named("defaultDispatcher")) { Dispatchers.Default }
    single(named("mainDispatcher")) { Dispatchers.Main }
}

// Feature module — includes its data dependency
val FeatureAModule = module {
    includes(FeatureADataModule, FeatureAPlatformModule)
    viewModelOf(::FeatureAViewModel)
}

// Data module — includes its own dependency
val FeatureADataModule = module {
    includes(DatabaseModule)
    factoryOf(::DbFooDataSource).bind<LocalFooDataSource>()
    factoryOf(::FooRepositoryImpl).bind<FooRepository>()
}
```

### 4.2 Platform module pattern

Every module that needs platform-specific bindings uses the `PlatformModule` expect/actual pattern:

```kotlin
// commonMain
val SomeModule = module {
    includes(PlatformModule)
    // common bindings
}
internal expect val PlatformModule: Module

// androidMain
internal actual val PlatformModule: Module = module {
    factoryOf(::AndroidSpecificImpl).bind<SomeInterface>()
}

// iosMain — stub implementations where not yet available
internal actual val PlatformModule: Module = module {
    factory { object : SomeInterface { /* no-op */ } }
}
```

### 4.3 Binding conventions

- Use `factoryOf(::Impl).bind<Interface>()` for data sources and repositories (factory scope).
- Use `viewModelOf(::ViewModel)` for ViewModels.
- Use `viewModel { ViewModel(get(), get()) }` when constructor parameters need explicit resolution (e.g., passing navigation-saved-state arguments).
- Use `single { ... }` only for truly global singletons (Room database, DataStore, Navigator).
- Always bind to **interfaces**, not concrete classes, so that consumers resolve by interface type.

### 4.4 Named qualifiers

All dispatchers and other platform-specific singletons use named qualifiers:

```kotlin
get<CoroutineDispatcher>(named("ioDispatcher"))
get<CoroutineDispatcher>(named("defaultDispatcher"))
get<CoroutineDispatcher>(named("mainDispatcher"))
get<DataStore<Preferences>>(named("datastore"))
```

---

## 5. Data Layer

### 5.1 Repository pattern

```
┌─────────────────────┐
│ Repository interface│  ← public (api module), exposes Flow<T> for reads, suspend for writes
└──────────┬──────────┘
           │ delegates to
┌──────────▼──────────┐
│ RepositoryImpl      │  ← internal (impl module), pure delegation shim
└──────────┬──────────┘
           │ delegates to
┌──────────▼──────────┐
│ Local*DataSource    │  ← internal interface (impl module), abstracts over storage
└──────────┬──────────┘
           │ implemented by
┌──────────▼──────────┐
│ Db*DataSource       │  ← internal class (impl module), Room DAO wrapper
└─────────────────────┘
```

**Return type conventions:**
- Reads always return `Flow<T>` (reactive, backed by Room DAO Flows or DataStore Flows).
- Writes always return `suspend fun(): Unit` or `suspend fun(): Int` (the affected row count).
- Never expose Room entities or internal types through the repository interface — map to domain models first.

### 5.2 Repository interface (api module)

```kotlin
interface FooRepository {
    fun getFoos(): Flow<List<Foo>>
    fun getFooById(id: Int): Flow<Foo?>
    suspend fun createFoo(foo: CreateFoo)
    suspend fun updateFoo(foo: UpdateFoo)
    suspend fun deleteFoo(id: Int)
}
```

### 5.3 Repository implementation (impl module)

```kotlin
internal class FooRepositoryImpl(
    private val localDataSource: LocalFooDataSource
) : FooRepository {
    override fun getFoos(): Flow<List<Foo>> = localDataSource.getFoos()
    override suspend fun createFoo(foo: CreateFoo) = localDataSource.createFoo(foo)
    // ... pure delegation
}
```

The repository impl delegates everything to the local data source. This keeps repositories thin and testable — all mapping and DB logic lives in `Db*DataSource`.

### 5.4 Room patterns

**Entities** — `internal data class` with `@Entity`, `@PrimaryKey`, `@ColumnInfo`:

```kotlin
@Entity(
    tableName = "foo",
    foreignKeys = [ForeignKey(entity = BarEntity::class, parentColumns = ["id"], childColumns = ["bar_id"], onDelete = CASCADE)],
    indices = [Index("bar_id")]
)
internal data class FooEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "bar_id") val barId: Int,
    @ColumnInfo(name = "name") val name: String,
)
```

**DAOs** — `internal interface` with `@Dao`:

```kotlin
@Dao
internal interface FooDao {
    @Query("SELECT * FROM foo WHERE bar_id = :barId")
    fun getFoosByBarId(barId: Int): Flow<List<FooEntity>>
    
    @Upsert
    suspend fun upsertFoo(foo: FooEntity): Long
}
```

**Database** — `@ConstructedBy` for KMP Room:

```kotlin
@Database(entities = [FooEntity::class, BarEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fooDao(): FooDao
}
```

**Relations and projections** — for queries that combine entities:
- Use `@Embedded` for entity fields within a relation data class.
- Use projection data classes for queries that return a subset of columns.
- Place these in `local/relation/` or `local/projection/` subpackages.

**Data source wrapping Room** — where mapping happens:

```kotlin
internal class DbFooDataSource(
    private val fooDao: FooDao,
    private val defaultDispatcher: CoroutineDispatcher
) : LocalFooDataSource {
    
    override fun getFoos(): Flow<List<Foo>> = fooDao.getAllFoos()
        .mapRecursive { it.toFoo() }
        .flowOn(defaultDispatcher)
    
    override suspend fun createFoo(foo: CreateFoo) = withContext(defaultDispatcher) {
        fooDao.upsertFoo(foo.toEntity())
    }
}
```

- Use `mapRecursive` (a utility extension on `Flow<List<T>>`) to map each list element.
- Use `flowOn(dispatcher)` to shift flow collection to the appropriate dispatcher.
- Use `withContext(dispatcher)` for suspend write operations.

### 5.5 DataStore (Preferences) pattern

All DataStore-backed implementations follow an identical pattern:

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

Key conventions:
- Use `.catch { if (it is IOException) ... }` to handle initial read failures (required by DataStore contract).
- Store keys as `private companion object` vals.
- Use the appropriate key type: `booleanPreferencesKey()`, `intPreferencesKey()`, `stringPreferencesKey()`, `stringSetPreferencesKey()`.
- The DataStore `single` is provided with qualifier `named("datastore")` from the `:core:datastore` module.

### 5.6 Mapper conventions

Mappers are `internal` extension functions placed in a `local/mapper/` subpackage:

```kotlin
// Entity → Domain
internal fun FooEntity.toFoo(): Foo = Foo(id = id, name = name)

// Domain → Entity (for writes)
internal fun CreateFoo.toFooEntity(): FooEntity = FooEntity(name = name)

// Relation → Domain
internal fun FooRelation.toFoo(): Foo = foo.toFoo()
```

- Extension functions, not static companion methods.
- One direction per function name: `toXxx()` for entity-to-domain, `toXxxEntity()` for domain-to-entity.
- Always `internal` — consumers outside the module should never see entity types.

---

## 6. Feature Layer

### 6.1 Route definitions (api module)

Routes are `@Serializable` classes or objects implementing the `NavKey` interface:

```kotlin
@Serializable
data class FooDetailRoute(val fooId: Int) : NavKey

@Serializable  
data object FooListRoute : NavKey
```

A `PolymorphicModuleBuilder` extension registers serializable subtypes:

```kotlin
fun PolymorphicModuleBuilder<NavKey>.fooRoutes() {
    subclass(FooDetailRoute.serializer())
    subclass(FooListRoute.serializer())
}
```

### 6.2 ViewModels

**Constructor convention** — all dependencies injected via constructor, dispatchers by named qualifier:

```kotlin
internal class FooViewModel(
    private val fooRepository: FooRepository,
    private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // ...
}
```

**State exposure** — prefer `stateIn()` for Flow-to-State chains:

```kotlin
val foos: StateFlow<List<FooUi>> = fooRepository.getFoos()
    .map { it.map { foo -> foo.toFooUi() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

For mutable local state, use `MutableStateFlow` with `asStateFlow()`:

```kotlin
private val _isLoading = MutableStateFlow(false)
val isLoading = _isLoading.asStateFlow()
```

For state surviving config changes, use `SavedStateHandle`:

```kotlin
private val _currentStep by savedStateHandle.getMutableStateFlow(
    coroutineScope = viewModelScope,
    defaultValue = 1
)
```

**Dispatcher injection in ViewModels:**

```kotlin
class FooViewModel(
    defaultDispatcher: CoroutineDispatcher = get(named("defaultDispatcher"))
) : ViewModel() {
    init {
        viewModelScope.launch(defaultDispatcher) { /* ... */ }
    }
}
```

### 6.3 Unidirectional data flow (Action / Event)

Every interactive screen uses a **sealed interface Action** for user intents and a **sealed interface Event** for one-shot side effects:

```kotlin
// User intents — navigational or state-changing
internal sealed interface FooAction {
    data class OnSelectFoo(val id: Int) : FooAction
    data object OnCreateNew : FooAction
    data object OnDelete : FooAction
}

// One-shot side effects — snackbar, navigation, etc.
internal sealed interface FooEvent {
    data class ShowMessage(val message: String) : FooEvent
    data class NavigateToDetail(val fooId: Int) : FooEvent
}
```

The ViewModel exposes actions via a single handler and events via a `Channel`:

```kotlin
private val _eventChannel = Channel<FooEvent>(Channel.BUFFERED)
val eventChannel = _eventChannel.receiveAsFlow()

fun onAction(action: FooAction) {
    when (action) {
        FooAction.OnCreateNew -> viewModelScope.launch { /* ... */ }
        is FooAction.OnSelectFoo -> { /* update state */ }
        FooAction.OnDelete -> { viewModelScope.launch { _eventChannel.send(FooEvent.ShowMessage("Deleted")) } }
    }
}
```

### 6.4 Screen composables (Root + Screen split)

**Two-layer pattern:**

```kotlin
// Root — wires ViewModel, handles side effects
@Composable
internal fun FooScreenRoot(
    viewModel: FooViewModel = koinViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onGoBack: () -> Unit,
) {
    val foos by viewModel.foos.collectAsStateWithLifecycle()
    
    ObserveAsEvents(viewModel.eventChannel) { event ->
        when (event) {
            is FooEvent.ShowMessage -> { /* snackbar */ }
            is FooEvent.NavigateToDetail -> onNavigateToDetail(event.fooId)
        }
    }
    
    FooScreen(
        foos = foos,
        onAction = viewModel::onAction,
        onGoBack = onGoBack,
    )
}

// Screen — pure composable, no ViewModel dependency, previewable
@Composable
internal fun FooScreen(
    foos: List<FooUi>,
    onAction: (FooAction) -> Unit,
    onGoBack: () -> Unit,
) {
    // UI only — all state and callbacks come from parameters
}
```

**Benefits:**
- `FooScreen` has no ViewModel dependency → previewable with sample data.
- `FooScreenRoot` handles all lifecycle-aware state collection and side effects.
- Navigation callbacks are passed from the outside (from the navigation entry block), keeping the screen decoupled from the navigator.

### 6.5 DI module for a feature

```kotlin
val FooFeatureModule = module {
    includes(FooDataModule, FooPlatformModule)
    viewModelOf(::FooViewModel)
}
```

---

## 7. Navigation (Navigation3 + Koin)

### 7.1 Navigation entries

Navigation entries are registered via a Koin extension that wires each route to a composable:

```kotlin
val FooNavigationModule = module {
    navigation<FooDetailRoute>(metadata = SLIDE_HORIZONTALLY) { route ->
        FooDetailScreenRoot(
            fooId = route.fooId,
            onGoBack = { get<Navigator>().goBack() },
        )
    }
    
    navigation<FooListRoute> {
        FooListScreenRoot()
    }
}
```

Navigation animation metadata keys are defined in a shared navigation module:
- `SLIDE_HORIZONTALLY` — for detail screens
- `SLIDE_VERTICALLY` — for bottom-sheet-like transitions
- `BOUNCE_APPEAR` — for modal-like entries

### 7.2 Navigator class

A simple injectable `Navigator` holds a reference to the root `NavBackStack`:

```kotlin
class Navigator {
    lateinit var backStack: NavBackStack<NavKey>
    fun goTo(destination: NavKey) { backStack.add(destination) }
    fun goBack() { backStack.removeLastOrNull() }
}
```

### 7.3 Root navigation setup (in the entry-point module)

```kotlin
val backStack = rememberNavBackStack(
    configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                fooRoutes()
                barRoutes()
            }
        }
    },
    FooListRoute  // start destination
)

NavDisplay(
    backStack = backStack,
    entryProvider = koinEntryProvider(),
)
```

### 7.4 Nested navigation for multi-step flows

Multi-step wizards (onboarding, form wizards) create their own independent `rememberNavBackStack` inside a parent scaffold. Each step is an `entry<T>` block:

```kotlin
val wizardBackStack = rememberNavBackStack(
    configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(Step1Route.serializer())
                subclass(Step2Route.serializer())
                subclass(Step3Route.serializer())
            }
        }
    },
    Step1Route
)

NavDisplay(backStack = wizardBackStack, entryProvider = entryProvider {
    entry<Step1Route> { StepOneScreen(onContinue = { wizardBackStack.add(Step2Route) }) }
    entry<Step2Route> { StepTwoScreen(onContinue = { wizardBackStack.add(Step3Route) }) }
    entry<Step3Route> { StepThreeScreen(onFinish = { parentNavigator.goBack() }) }
})
```

---

## 8. Platform Abstraction (expect/actual)

### 8.1 When to use each pattern

| Use case | Pattern | Visibility |
|----------|---------|------------|
| Platform-specific dependency injection | `expect val PlatformModule: Module` | `internal` |
| Platform-specific service with interface | `expect class XxxImpl : SomeInterface` | `internal` |
| Platform-specific component (e.g., Toaster, share) | `expect class Xxx` | `internal` |
| Platform-specific composable | `expect fun XxxScreen(...)` | `internal` |
| KMP Room database constructor | `expect object XxxDatabaseConstructor : RoomDatabaseConstructor<...>` | `public` |
| Platform-specific locale behavior | `expect fun getDefaultLocale(): PlatformLocale` | `public` |
| Platform-specific type | `expect class PlatformLocale` (via `actual typealias`) | `public` |

### 8.2 Shared source sets

Use intermediate source sets when multiple platforms share the same implementation:

| Source set | Targets | Usage |
|-----------|---------|-------|
| `commonMain` | All | Interfaces, domain models, common logic |
| `androidMain` | Android only | Android-specific actuals |
| `iosMain` | iOS only | iOS-specific actuals |
| `jvmMain` | Desktop (JVM) only | Desktop-specific actuals |
| `jvmAndAndroidMain` | JVM + Android | Shared Java/Kotlin stdlib implementations (e.g., `java.time`) |
| `jvmAndIosMain` | JVM + iOS | Shared Skia-based implementations (e.g., Compose `ImageBitmap`) |

### 8.3 Non-composable expect/actual

```kotlin
// commonMain
internal expect class FooServiceImpl : FooService {
    override suspend fun doSomething()
}

// androidMain
internal actual class FooServiceImpl(private val context: Context) : FooService {
    actual override suspend fun doSomething() { /* Android implementation */ }
}

// iosMain
internal actual class FooServiceImpl : FooService {
    actual override suspend fun doSomething() { /* iOS implementation */ }
}
```

Note that the `actual class` constructor may differ between platforms — Android may need `Context`, while iOS/JVM do not. The Koin DI module on each platform wires the appropriate actual class.

### 8.4 Composable expect/actual

```kotlin
// commonMain
@Composable
internal expect fun FooPlatformScreen(onBack: () -> Unit)

// androidMain
@Composable
internal actual fun FooPlatformScreen(onBack: () -> Unit) { /* full UI */ }

// iosMain / jvmMain — stub if not yet implemented
@Composable
internal actual fun FooPlatformScreen(onBack: () -> Unit) = Unit
```

### 8.5 iOS platform code

When writing iOS platform actuals:
- Import from `platform.*` — e.g., `platform.UIKit.*`, `platform.UserNotifications.*`, `platform.Foundation.*`.
- Use `@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)` for cinterop calls.
- Prefer `NSData`/`NSUrl`/`NSDateComponents` over raw `kotlinx.cinterop` when the higher-level types suffice.

---

## 9. Coroutines & Dispatchers

### 9.1 Rule: never hardcode dispatchers

```kotlin
// BAD
withContext(Dispatchers.IO) { ... }
viewModelScope.launch(Dispatchers.Default) { ... }

// GOOD
withContext(get<CoroutineDispatcher>(named("ioDispatcher"))) { ... }
viewModelScope.launch(get<CoroutineDispatcher>(named("defaultDispatcher"))) { ... }
```

All dispatchers are provided by the root DI module:

```kotlin
single(named("ioDispatcher")) { Dispatchers.IO }
single(named("defaultDispatcher")) { Dispatchers.Default }
single(named("mainDispatcher")) { Dispatchers.Main }
```

### 9.2 Dispatcher selection

| Operation type | Use |
|---------------|-----|
| Room database reads/writes | `defaultDispatcher` (or `ioDispatcher` for I/O-bound work) |
| DataStore reads/writes | `ioDispatcher` |
| Reminder scheduling (AlarmManager, UNNotificationCenter) | `ioDispatcher` |
| Network/file I/O | `ioDispatcher` |
| CPU-intensive work | `defaultDispatcher` |
| UI state updates | `mainDispatcher` (explicit only when needed, ViewModel usually collects on main) |

### 9.3 Coroutine scopes

For fire-and-forget tasks that should outlive the ViewModel scope, inject a global scope:

```kotlin
factory(named("defaultCoroutineScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
factory(named("ioCoroutineScope")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
```

Use `SupervisorJob()` so one child failure does not cancel siblings.

### 9.4 Channel for one-shot events

UDF side effects use `Channel` (not `SharedFlow`) to avoid replay:

```kotlin
private val _eventChannel = Channel<MyEvent>(Channel.BUFFERED)
val eventChannel = _eventChannel.receiveAsFlow()
```

Collect in the Root composable via `ObserveAsEvents`, which combines `LaunchedEffect` + `repeatOnLifecycle`:

```kotlin
@Composable
fun <T> ObserveAsEvents(
    events: Flow<T>,
    key1: Any? = null,
    key2: Any? = null,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1, key2, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect(onEvent)
        }
    }
}
```

---

## 10. UI Conventions (Compose Multiplatform)

### 10.1 Theme

- The theme is a custom composable wrapping `MaterialTheme` (or `MaterialExpressiveTheme` for Material 3 expressive variant).
- Colors are defined as a seed-based scheme with light/dark variants, using top-level `val` definitions.
- Typography uses two custom font families: one for display/text (serif or expressive sans) and one for body/labels (standard sans).
- Fonts are loaded via Compose Resources `Font(resourceUri, weight, style)`.
- Dynamic color support (Android 12+ Material You) is handled by the Android actual of the theme composable; other platforms fall back to the static scheme.

### 10.2 Screen composable structure

```kotlin
@Composable
internal fun MyScreen(
    state: MyUiState,
    onAction: (MyAction) -> Unit,
    onNavigate: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("My Screen") }) }
    ) { padding ->
        // Content with padding
    }
}
```

- Use `Scaffold` for screens with a top bar.
- Use `ModalBottomSheet` for detail views.
- Use `AlertDialog` for confirmation dialogs and pickers.

### 10.3 Adaptive layout

Every screen checks the window size class:

```kotlin
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

if (windowSizeClass.isCompact) {
    // Phone layout
} else {
    // Tablet/desktop layout
}
```

Extension properties are defined in a shared module:

```kotlin
val WindowSizeClass.isCompact get() = widthSizeClass == WindowWidthSizeClass.COMPACT
val WindowSizeClass.isMedium get() = widthSizeClass == WindowWidthSizeClass.MEDIUM
val WindowSizeClass.isExpanded get() = widthSizeClass == WindowWidthSizeClass.EXPANDED
```

### 10.4 UI models

- Annotate data classes with `@Stable` to optimize Compose recomposition.
- Annotate with `@Serializable` when state needs to survive config changes via `SavedStateHandle`.
- Use `@Serializable(with = ColorAsIntSerializer::class)` for `Color` fields.
- Define mapper extension functions `toXxxUi()` on domain models to convert to UI models.

### 10.5 Preview strategy

Every screen composable has at least two preview functions:

```kotlin
@LocalizedPreview  // RTL + multiple locales
@Composable
private fun MyScreenLocalizedPreview() {
    EcopanTheme {
        MyScreen(state = sampleState, onAction = {}, onNavigate = {}, onBack = {})
    }
}

@AdaptivePreview  // Multiple screen sizes
@Composable
private fun MyScreenAdaptivePreview() {
    EcopanTheme {
        MyScreen(state = sampleState, onAction = {}, onNavigate = {}, onBack = {})
    }
}
```

These are custom composite annotations built from `@Preview`:

```kotlin
@Preview(locale = "en")
@Preview(locale = "es")
@Preview(locale = "ja")
@Preview(locale = "ar")
annotation class LocalizedPreview
```

### 10.6 Experimental API usage

Use `@file:OptIn` at the top of each file to avoid per-usage annotation noise:

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
package com.example.feature.foo.ui
```

Common opt-ins:
- `ExperimentalMaterial3Api` — `Scaffold`, `TopAppBar`, `BottomSheet`
- `ExperimentalMaterial3ExpressiveApi` — expressive theme features
- `ExperimentalMaterial3AdaptiveApi` — adaptive navigation
- `ExperimentalCoroutinesApi` — custom coroutine utilities
- `ExperimentalForeignApi`, `BetaInteropApi` — iOS cinterop

### 10.7 Compose Resources

- All string resources, drawable resources, and fonts live in `src/commonMain/composeResources/`.
- Access via the generated `Res` class with namespaced imports to disambiguate between modules:

```kotlin
import feature_foo.generated.resources.Res as FooRes
import core_ui.generated.resources.Res as UiRes

Text(stringResource(FooRes.string.my_string))
```

---

## 11. Build System

### 11.1 Build-logic composite build

The project uses a Gradle [composite build](https://docs.gradle.org/current/userguide/composite_builds.html) at `build-logic/` that contains a single `:convention` module. The convention module registers custom Gradle plugins (defined at `build-logic/convention/src/main/kotlin/`) that encapsulate common configuration. These are declared in `settings.gradle.kts` with `includeBuild("build-logic")` at the root level and referenced from every module via the version catalog.

**How to reference plugins** — via the version catalog `libs.plugins.*` using the `alias(libs.plugins.<name>)` syntax:

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}
```

Never apply plugins by bare string ID (e.g., `id("com.f776.kotlinMultiplatform")`). Always use `alias(libs.plugins.xxx)`.

### 11.2 Convention plugin catalog

All convention plugins are defined in `build-logic/convention/build.gradle.kts` and their source lives under `build-logic/convention/src/main/kotlin/`. The plugin ID (used in the version catalog) and the `implementationClass` are listed below alongside what each plugin configures.

---

**`com.f776.kotlinMultiplatform`** — `KotlinMultiplatformConventionPlugin`

Applied to every KMP library module. Configures:

- Applies `org.jetbrains.kotlin.multiplatform`
- Creates targets: `jvm()` (JVM/desktop), `iosArm64()`, `iosSimulatorArm64()` (static frameworks)
- Sets `jvmToolchain(17)` globally
- Enables `-Xexpect-actual-classes` on all source sets
- Enables `@ExperimentalUuidApi` compiler opt-in on all source sets
- Adds `kermit` logging to `commonMain`
- Adds `kotlin.test` + `project(":core:test")` to `commonTest`
- Uses the default KMP hierarchy template (`applyDefaultHierarchyTemplate()`)

---

**`com.f776.androidKmpLibrary`** — `AndroidLibraryConventionPlugin`

Applied to every KMP library module that targets Android. Configures:

- Applies `com.android.kotlin.multiplatform.library`
- Derives `namespace` from the module path (e.g., `:data:foo:impl` → `com.f776.ecopan.data.foo.impl`)
- Sets `compileSdk`, `minSdk` from version catalog
- Enables `androidResources`
- Configures host-side unit tests (Robolectric, coroutines test, Compose UI test)
- Configures device-side instrumented tests (AndroidJUnitRunner)

---

**`com.f776.composeMultiplatform`** — `ComposeConventionPlugin`

Applied to every KMP library module that contains Compose UI. Configures:

- Applies `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose` (Compose compiler)
- Sets `generateResClass = always` (Compose Resources code generation)
- Adds common Compose dependencies to all source sets: runtime, foundation, material3, ui, resources, ui-tooling-preview, adaptive-navigation, material-icons-extended, lifecycle-viewmodel-compose, lifecycle-runtime-compose, adaptive-layout
- Adds Compose UI test to `commonTest`
- Adds `ui-tooling` (debug) and `activity-compose` to `androidMain`
- Adds desktop compose (`currentOs`) and coroutines-swing to `jvmMain`
- Configures the Compose compiler stability config from root `compose_compiler_config.conf`
- Configures optional Compose compiler metrics/reports via Gradle properties

---

**`com.f776.roomMultiplatform`** — `RoomDatabaseConventionPlugin`

Applied to the module containing Room database, DAOs, and entities (typically `:core:database`). Configures:

- Applies `com.google.devtools.ksp` + `androidx.room`
- Sets schema directory to `$projectDir/schemas`
- Adds `room-runtime` to `commonMain` and `sqlite-bundled` to `jvmMain`
- Adds KSP compiler processors for all targets: `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`, `kspJvm`

---

**`com.f776.androidApplication`** — `AndroidApplicationConventionPlugin`

Applied to the Android phone app entry-point module (`:android:app`). Configures:

- Applies `com.android.application` + Compose compiler plugin
- Sets `jvmToolchain(17)` for Kotlin Android compilation
- Sets `namespace`, `applicationId`, `compileSdk`, `minSdk`, `targetSdk`, `versionCode`, and `versionName` (from `version.properties`)
- Enables ProGuard/R8 minification and resource shrinking for release builds
- Configures Java 17 compatibility
- Enables Compose build features

---

**`com.f776.desktopApplication`** — `DesktopApplicationConventionPlugin`

Applied to the JVM desktop entry-point module (`:jvm:app`). Configures:

- Applies `org.jetbrains.kotlin.jvm` + `org.jetbrains.compose` + Compose compiler
- Sets `generateResClass = always`
- Configures Compose compiler stability
- Adds all common Compose dependencies directly (runtime, foundation, material3, ui, resources, tooling, adaptive, icons, lifecycle, adaptive-layout)
- Adds desktop compose (`currentOs`) and coroutines-swing
- Configures native distribution packaging: DMG, MSI, DEB with icons, copyright, and entitlements
- Enables ProGuard for release builds

---

**`com.f776.glanceWidget`** — `GlanceWidgetConventionPlugin`

Applied to Glance app widget modules. Configures:

- Applies `org.jetbrains.compose` + Compose compiler
- Adds Glance dependencies (`glance-appwidget`, `glance-material`, `glance-appwidget-preview`, `glance-preview`) to `androidMain`
- Adds Compose runtime, foundation, and activity-compose to `androidMain`

---

**`com.f776.wearApplication`** — `WearApplicationConventionPlugin`

Applied to Wear OS app entry-point modules. Configures:

- Applies `com.android.application`
- Sets namespace, compileSdk, minSdk (wear-specific, lower than phone)
- Configures version code/name, Java 17, ProGuard, and packaging exclusions
- Configures host and device tests

---

### 11.3 Which plugins to use per module type

The table below shows exactly which convention plugins to apply for each layer. Additional plugins like `kotlinSerialization` are listed in the "Extra" column when commonly needed.

| Module type | Required convention plugins | Extra common plugins | Example |
|------------|---------------------------|----------------------|---------|
| **data:api** (interface-only) | `kotlinMultiplatform` | — | `:data:foo:api` |
| **data:impl** (no Compose UI) | `kotlinMultiplatform`, `androidKmpLibrary` | — | `:data:foo:impl` |
| **core:database** (Room) | `kotlinMultiplatform`, `androidKmpLibrary`, `roomMultiplatform` | `kotlinSerialization` | `:core:database` |
| **core:datastore** | `kotlinMultiplatform`, `androidKmpLibrary` | — | `:core:datastore` |
| **core:ui** (shared Compose components) | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` | `:core:ui` |
| **core:locale** | `kotlinMultiplatform`, `androidKmpLibrary` | — | `:core:locale` |
| **core:common** (utilities, no Android) | `kotlinMultiplatform` | — | `:core:common` |
| **feature:api** (routes only) | `kotlinMultiplatform`, `androidKmpLibrary` | `kotlinSerialization` | `:feature:foo:api` |
| **feature:impl** (ViewModels, screens) | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` | `:feature:foo:impl` |
| **Entry point: Android app** | `androidApplication`, `composeCompiler` (standalone) | `kotlinSerialization`, `googleServices`, `crashlytics`, `baselineprofile` | `:android:app` |
| **Entry point: JVM desktop** | `desktopApplication` | — | `:jvm:app` |
| **Entry point: root compose app** | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` | `:composeApp` |
| **Glance widget** | `androidLibrary` + `composeCompiler` (standalone) | — | `:android:feature:widget` |
| **Wear app** | `wearApplication`, `composeCompiler` (standalone) | — | `:android:wear` |

**Key rules:**
- Every KMP library module must always start with `kotlinMultiplatform`. If it targets Android, also add `androidKmpLibrary`.
- If the module has composable UI, also add `composeMultiplatform`. This brings in all Compose deps automatically — do not re-add them manually.
- If the module uses Room, also add `roomMultiplatform`. This brings in KSP, Room runtime, and KSP processors — do not add KSP manually.
- Do **not** manually apply `kotlin("multiplatform")`, `kotlin("plugin.compose")`, or `kotlinx-serialization` Gradle plugin by their base IDs — use the convention plugin or the version catalog alias.
- The root `:composeApp` module (shared KMP code for all entry points) applies `kotlinMultiplatform` + `androidKmpLibrary` + `composeMultiplatform`. It should not use `androidApplication` or `desktopApplication`.

### 11.4 Build script examples by layer

**data:api module** (interface-only, no Android needed):

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
    }
}
```

**data:impl module** (Room-backed repository, no Compose UI):

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.database)
            api(projects.data.foo.api)
            implementation(libs.koin.core)
        }
    }
}
```

Note that a data:impl module does not need `androidKmpLibrary` if it delegates Android-specific work to other modules. If it has its own Android source set (e.g., for platform-specific DI), add `androidKmpLibrary`.

**feature:api module** (routes and serializers):

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation3.runtime)
        }
    }
}
```

**feature:impl module** (ViewModels, screens, DI):

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.navigation)
            api(projects.feature.foo.api)
            implementation(projects.data.foo.impl)

            implementation(libs.koin.compose.viewmodel)
            implementation(libs.bundles.navigation3)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

**core:database module** (Room):

```kotlin
plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.roomMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}
```

### 11.5 Version catalog

All dependency versions and coordinates are centralized in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.4.0"
compose-multiplatform = "1.11.1"
koin = "4.2.1"
room = "2.8.4"
android-compileSdk = "37"
android-minSdk = "24"
android-targetSdk = "37"

[libraries]
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }

# Custom convention plugins (from build-logic)
f776-kotlinMultiplatform = { id = "com.f776.kotlinMultiplatform", version = "unspecified" }
f776-androidKmpLibrary = { id = "com.f776.androidKmpLibrary", version = "unspecified" }
f776-composeMultiplatform = { id = "com.f776.composeMultiplatform", version = "unspecified" }
f776-roomMultiplatform = { id = "com.f776.roomMultiplatform", version = "unspecified" }
f776-androidApplication = { id = "com.f776.androidApplication", version = "unspecified" }
f776-desktopApplication = { id = "com.f776.desktopApplication", version = "unspecified" }
f776-wearApplication = { id = "com.f776.wearApplication", version = "unspecified" }
f776-glanceWidget = { id = "com.f776.glanceWidget", version = "unspecified" }
```

**Rules:**
- Always use version catalog references — never hardcode version strings in build.gradle.kts.
- Group related libraries under the same version reference where possible.
- Custom convention plugins have `version = "unspecified"` because their version is determined by the composite build, not by a published artifact.
- Use `api()` for dependencies that should be transitively visible to consumers of the module (e.g., coroutines core from a data:api module).
- Use `implementation()` for dependencies that are internal to the module.
- Use `implementation(projects.xxx)` for project dependencies — never hardcode project paths as strings.

### 11.6 Module naming

- Module paths use lowercase with hyphens: `:feature:schedule:api`, `:data:preferences:impl`.
- Use `:core:*` for shared infrastructure, `:data:*` for data layer with `api`/`impl` split, `:feature:*` for feature layer with `api`/`impl` split.
- The `namespace` for Android is derived automatically by `androidKmpLibrary` from the module path.

---

## 12. Testing

### 12.1 Test utilities

Common test utilities live in `:core:test` and are auto-depended by all KMP modules via convention plugin:

```kotlin
// kotlin.sourceSets.commonTest.dependencies {
//     implementation(projects.core.test)
// }
```

### 12.2 Main dispatcher rule

```kotlin
object MainDispatcherRule {
    fun createTestDispatcher(): TestDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    fun setMainDispatcher(dispatcher: TestDispatcher) { Dispatchers.setMain(dispatcher) }
    fun resetMainDispatcher() { Dispatchers.resetMain() }
}
```

### 12.3 Compose test matchers

```kotlin
fun hasRole(role: Role) = SemanticsMatcher("Searches for role: $role") {
    it.config.getOrNull(SemanticsProperties.Role) == role
}
```

### 12.4 Repository and ViewModel testing

- Test repository implementations by providing a fake `Local*DataSource` — the RepositoryImpl is a pure delegation shim.
- Test ViewModels by providing fake repositories and `UnconfinedTestDispatcher` for deterministic coroutine execution.
- Use `viewModelScope` with a test dispatcher via `Dispatchers.setMain(testDispatcher)`.

---

## 13. Quick Reference Checklist

When implementing a new feature, verify:

- [ ] **api/impl split**: interfaces in `<feature>:api`, implementations in `<feature>:impl`
- [ ] **Visibility**: all implementations, mappers, data sources are `internal`
- [ ] **Koin module**: the `impl` module has a single public `val FooModule: Module` that `includes()` dependencies
- [ ] **Platform module**: if the feature needs platform-specific code, use `expect val PlatformModule: Module`
- [ ] **Routes**: `@Serializable data class/object : NavKey` in the `api` module
- [ ] **State**: `StateFlow`, exposed via `asStateFlow()` or `stateIn()`
- [ ] **Actions**: sealed interface, processed by `onAction()` in the ViewModel
- [ ] **Events**: sealed interface, sent via `Channel`, collected via `ObserveAsEvents`
- [ ] **Root+Screen split**: Screen composable has no ViewModel, receives all state/callbacks as parameters
- [ ] **Previews**: at least one `@LocalizedPreview` and one `@AdaptivePreview`
- [ ] **Dispatchers**: all injected by named qualifier, never hardcoded
- [ ] **Mappers**: `internal` extension functions in a `mapper/` subpackage
- [ ] **Opt-ins**: `@file:OptIn` at file level, not per-usage
- [ ] **Dependencies**: `api` module has no dependencies on `impl`; `impl` depends on `api` modules only
