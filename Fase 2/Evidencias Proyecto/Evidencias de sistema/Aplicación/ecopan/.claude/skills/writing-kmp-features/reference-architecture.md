# Architecture Patterns — Module Structure, Encapsulation & DI

## Module Layout

Every feature and data domain follows an **api/impl split**:

```
:feature:<name>:api      — Public interfaces (NavKey routes, domain models)
:feature:<name>:impl     — Implementation (ViewModels, screens, DI modules)
:data:<name>:api         — Public interfaces (Repository, domain models)
:data:<name>:impl        — Implementation (Room sources, mappers)
:core:<name>             — Shared infrastructure (database, datastore, locale, ui)
```

## Dependency Direction

```
Entry point (app module)
  └─> feature:impl modules
        └─> feature:api modules
        └─> data:impl modules
              └─> data:api modules
              └─> core:* modules
```

- `api` modules have **no dependencies** on `impl` modules.
- `core` modules are standalone shared infrastructure.
- Feature `impl` modules depend on `data:api` (never `data:impl`) — all data access goes through repository interfaces.

## Packaging

| Content | Package |
|---------|---------|
| Repository/data source interfaces | `com.example.domain.<name>` |
| DI module | `com.example.feature.<name>.di` |
| ViewModels | `com.example.feature.<name>` |
| Screens / UI | `com.example.feature.<name>.ui` |
| Room entities/DAOs | `com.example.data.<name>.local` |
| Mappers | `com.example.data.<name>.local.mapper` |

## Visibility Rules

| Element | Visibility | Module |
|---------|-----------|--------|
| Repository / DataSource interfaces | `public` | `api` |
| NavKey route classes | `public` | `api` |
| Domain model data classes | `public` | `api` |
| Repository **implementations** | `internal` | `impl` |
| Local data source interfaces | `internal` | `impl` |
| Room DAOs and entities | `internal` | `impl` / `core:database` |
| Room-backed data source classes | `internal` | `impl` |
| Mapper functions | `internal` | `impl` |
| ViewModels | `internal` | `impl` |
| Screen composables | `internal` | `impl` |
| **DI modules (Koin Module)** | `public` | `impl` |
| `PlatformModule` (expect val) | `internal` | `impl` |
| expect/actual classes | `internal` | `impl` / `core:*` |

**Rationale:** DI modules are the **only public surface** of `impl` modules. Everything else is `internal` so consumers interact only through interfaces from `api` modules. The compiler enforces this — `internal` declarations cannot be imported across module boundaries.

## Dependency Injection (Koin)

### Module hierarchy

DI modules form a tree via `includes()`:

```kotlin
// Root module
val AppModule = module {
    includes(FeatureAModule, FeatureBModule, PreferencesModule)
    single(named("ioDispatcher")) { Dispatchers.IO }
    single(named("defaultDispatcher")) { Dispatchers.Default }
    single(named("mainDispatcher")) { Dispatchers.Main }
}

// Feature module
val FeatureAModule = module {
    includes(FeatureADataModule, FeatureAPlatformModule)
    viewModelOf(::FeatureAViewModel)
}

// Data module
val FeatureADataModule = module {
    includes(DatabaseModule)
    factoryOf(::DbFooDataSource).bind<LocalFooDataSource>()
    factoryOf(::FooRepositoryImpl).bind<FooRepository>()
}
```

### PlatformModule pattern

Every module needing platform-specific bindings uses:

```kotlin
// commonMain
val SomeModule = module {
    includes(PlatformModule)
}
internal expect val PlatformModule: Module

// androidMain
internal actual val PlatformModule: Module = module {
    factoryOf(::AndroidSpecificImpl).bind<SomeInterface>()
}

// iosMain / jvmMain — stub
internal actual val PlatformModule: Module = module {
    factory { object : SomeInterface { /* no-op */ } }
}
```

### Binding conventions

- `factoryOf(::Impl).bind<Interface>()` for data sources and repositories (factory scope).
- `viewModelOf(::ViewModel)` for ViewModels.
- `viewModel { ViewModel(get(), get()) }` when params need explicit resolution.
- `single { ... }` only for true singletons (Room DB, DataStore, Navigator).
- Always bind to **interfaces**, not concrete classes.

### Named qualifiers for dispatchers

```kotlin
get<CoroutineDispatcher>(named("ioDispatcher"))
get<CoroutineDispatcher>(named("defaultDispatcher"))
get<CoroutineDispatcher>(named("mainDispatcher"))
get<DataStore<Preferences>>(named("datastore"))
```

## expect/actual Patterns

| Use case | Pattern | Visibility |
|----------|---------|------------|
| Platform-specific DI | `expect val PlatformModule: Module` | `internal` |
| Platform-specific service | `expect class XxxImpl : Interface` | `internal` |
| Platform-specific component | `expect class Xxx` | `internal` |
| Platform-specific composable | `expect fun XxxScreen()` | `internal` |
| Room DB constructor | `expect object DbConstructor : RoomDatabaseConstructor<...>` | `public` |
| Platform locale | `expect class PlatformLocale` (via `actual typealias`) | `public` |

### Shared source sets

| Source set | Targets | Usage |
|-----------|---------|-------|
| `commonMain` | All | Interfaces, domain models, common logic |
| `androidMain` | Android only | Android-specific actuals |
| `iosMain` | iOS only | iOS-specific actuals |
| `jvmMain` | Desktop only | Desktop-specific actuals |
| `jvmAndAndroidMain` | JVM + Android | Shared `java.time` implementations |
| `jvmAndIosMain` | JVM + iOS | Shared Skia implementations |

### iOS platform code

- Import from `platform.*` — `platform.UIKit.*`, `platform.UserNotifications.*`, `platform.Foundation.*`.
- Use `@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)` for cinterop.
- Prefer `NSData`/`NSUrl`/`NSDateComponents` over raw `kotlinx.cinterop`.
