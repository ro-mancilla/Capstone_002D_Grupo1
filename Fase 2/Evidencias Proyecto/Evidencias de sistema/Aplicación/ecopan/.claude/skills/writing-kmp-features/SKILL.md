---
name: "writing-kmp-features"
description: "Design and implement features in a KMP Compose Multiplatform app following clean architecture. Use when creating new data sources, repositories, ViewModels, screens, navigation routes, platform abstractions, or DI modules. Covers api/impl module split, Koin injection, Room patterns, unidirectional data flow, Navigation3, expect/actual, and convention plugins."
metadata:
  version: "1.0.0"
  category: "architecture"
  tags: ["kmp", "compose", "multiplatform", "clean-architecture", "koin", "navigation", "room", "expect-actual"]
  triggers:
    include:
      - "create a new feature"
      - "add a data source"
      - "implement repository"
      - "build a screen"
      - "set up navigation route"
      - "wire dependency injection"
      - "add platform-specific code"
      - "create a new module"
      - "build gradle module"
      - "convention plugin"
      - "Room database entity or DAO"
      - "expect actual class"
      - "ViewModel with state"
      - "unidirectional data flow"
      - "Navigation3 route"
      - "Koin module wiring"
    exclude: []
---

# KMP Feature Development Guide

## When To Use

Use this skill when implementing any feature in this KMP Compose Multiplatform app. It applies to:
- Creating new data modules (api/impl split, repositories, Room entities, DataStore prefs)
- Building feature modules (routes, ViewModels, screens, DI wiring)
- Adding platform-specific code (expect/actual classes, composables, or DI modules)
- Setting up build scripts with convention plugins

## Core Principles

1. **api/impl split** — interfaces in `:x:api`, implementations in `:x:impl`. The `api` module has zero dependencies on `impl`.
2. **Visibility** — implement everything as `internal`. Only interfaces (in `api` modules) and DI module vals (in `impl` modules) are public. See [reference-architecture.md](reference-architecture.md).
3. **No hardcoded dispatchers** — always inject via Koin named qualifiers (`named("ioDispatcher")`, `named("defaultDispatcher")`).
4. **No hardcoded platform APIs** — use `expect`/`actual` or `PlatformModule` injection. See [reference-platform.md](reference-platform.md).
5. **Unidirectional data flow** — Screen → Action → ViewModel → State/Event → Screen.

## Workflow

### New Feature (end-to-end)

1. Create `:feature:<name>:api` module — define `@Serializable NavKey` route classes and serializer extensions.
2. Create `:feature:<name>:impl` module — build ViewModel, screens, DI module. See [reference-feature-layer.md](reference-feature-layer.md).
3. Create `:data:<name>:api` module (if new data domain) — define repository interfaces and domain models.
4. Create `:data:<name>:impl` module — implement repository, local data sources, mappers. See [reference-data-layer.md](reference-data-layer.md).
5. Wire DI modules together with `includes()` in the parent module.
6. Register navigation entries and serializer subclasses in the root nav setup.
7. Write build.gradle.kts with the correct convention plugins. See [reference-build.md](reference-build.md).

### New Screen (within existing feature)

1. Define the route as `@Serializable data class/object : NavKey` in the feature's `api` module.
2. Create the ViewModel with injected deps, `StateFlow` state, and `Channel`-based events.
3. Create the Screen composable (no ViewModel ref — receives all state/callbacks as parameters).
4. Create the Root composable (wires ViewModel, collects state via `collectAsStateWithLifecycle`, handles events via `ObserveAsEvents`).
5. Register the navigation entry via `navigation<T>()` in the feature's DI module.
6. Add previews (`@LocalizedPreview`, `@AdaptivePreview`).

### New Data Source

1. Define the repository interface in `:data:<name>:api` — reads as `Flow<T>`, writes as `suspend fun`.
2. Define an `internal interface LocalXxxDataSource` in `:data:<name>:impl`.
3. Implement the Room-backed `DbXxxDataSource` (or DataStore-backed source) as an `internal class`.
4. Implement the repository as `internal class XxxRepositoryImpl` — pure delegation to the local data source.
5. Add Koin bindings: `factoryOf(::DbImpl).bind<LocalInterface>()`, `factoryOf(::RepoImpl).bind<RepoInterface>()`.
6. Wire platform dependencies via `internal expect val PlatformModule: Module` if needed.
7. Write mappers as `internal` extension functions in a `local/mapper/` subpackage.

## Guardrails

- Every `impl` module must expose exactly one public symbol: its Koin `Module` val. Everything else is `internal`.
- `api` modules must never depend on `impl` modules. Dependency direction: feature→data→core.
- Repository interfaces live in `domain.<name>` package; implementations in `data.<name>` package.
- All Room entities, DAOs, relations, and type converters are `internal`.
- All mappers are `internal` extension functions, never methods on the entity/domain classes.
- Composable `Screen` functions never take `ViewModel` parameters — they receive plain state and lambdas.
- Use `sealed interface` (not `sealed class`) for Action and Event types.
- One-shot side effects use `Channel` + `receiveAsFlow()`, consumed via `ObserveAsEvents` in the Root composable.
- `@file:OptIn` at file level for experimental APIs; never scatter `@OptIn` per-usage.
- Apply convention plugins via `alias(libs.plugins.f776.xxx)` — never by bare string ID.

## Anti-Patterns

- Hardcoding `Dispatchers.IO`, `Dispatchers.Default`, or `Dispatchers.Main` anywhere.
- Applying `ComposeConventionPlugin` to a module that contains no Compose UI.
- Calling `id("com.f776.kotlinMultiplatform")` instead of `alias(libs.plugins.f776.kotlinMultiplatform)`.
- Manually adding Compose dependencies when `composeMultiplatform` convention plugin already provides them.
- Exposing Room entities or internal data source types through repository interfaces.
- Skipping the `catch { if (it is IOException) emit(emptyPreferences()) }` block on DataStore flows.
- Putting navigation logic or ViewModel references in Screen composables.
- Using `SharedFlow` for one-shot events (use `Channel` instead).
- Referencing `impl` modules from `api` modules.

## Reference Files

- [reference-architecture.md](reference-architecture.md) — Module structure, encapsulation rules, DI patterns, Koin module hierarchy
- [reference-build.md](reference-build.md) — Convention plugin catalog, which plugins per module type, build script examples
- [reference-data-layer.md](reference-data-layer.md) — Repository pattern, Room entities/DAOs/relations, DataStore, mappers
- [reference-feature-layer.md](reference-feature-layer.md) — Routes, ViewModels, Action/Event UDF, screens, navigation setup, UI conventions
- [reference-platform.md](reference-platform.md) — expect/actual patterns, shared source sets, PlatformModule DI, iOS/JVM stubs
- [reference-quick-checklist.md](reference-quick-checklist.md) — 15-item verification list for new features

## Examples

### Creating a repository-backed feature from scratch

1. **API module** — interface only:
   - `:feature:search:api` → `SearchRoute` (NavKey), `searchRoutes()` serializer extension
   - `:data:search:api` → `SearchRepository` interface with `Flow<List<Result>>` and `suspend fun search(query)`

2. **Data impl** — Room + delegation:
   - `:data:search:impl` → `DbSearchDataSource : LocalSearchDataSource`, `SearchRepositoryImpl`
   - Koin: `val SearchDataModule = module { includes(DatabaseModule) ... }`

3. **Feature impl** — ViewModel + Screen:
   - `:feature:search:impl` → `SearchViewModel(onAction)`, `SearchScreenRoot`, `SearchScreen`
   - Koin: `val SearchModule = module { includes(SearchDataModule) viewModelOf(::SearchViewModel) }`

4. **Build** — convention plugins:
   - `:data:search:api` → plugins: `kotlinMultiplatform`
   - `:data:search:impl` → plugins: `kotlinMultiplatform`, `androidKmpLibrary`
   - `:feature:search:api` → plugins: `kotlinMultiplatform`, `androidKmpLibrary`, `kotlinSerialization`
   - `:feature:search:impl` → plugins: `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform`, `kotlinSerialization`

### Adding platform-specific behavior

- For DI: use `internal expect val PlatformModule: Module` + per-platform `internal actual val`.
- For a service: use `internal expect class XxxImpl : Interface` with platform actuals.
- For a composable: use `@Composable internal expect fun XxxScreen()` with platform actuals.
- iOS/JVM defaults are empty/Unit stubs when the feature is Android-only.
