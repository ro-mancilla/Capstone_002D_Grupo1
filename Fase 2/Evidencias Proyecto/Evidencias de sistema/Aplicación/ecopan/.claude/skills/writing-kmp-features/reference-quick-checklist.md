# Quick Reference Checklist

When implementing a new feature, verify every item:

- [ ] **api/impl split**: interfaces in `<feature>:api`, implementations in `<feature>:impl`
- [ ] **Visibility**: all implementations, mappers, data sources are `internal`
- [ ] **Koin module**: the `impl` module has a single public `val FooModule: Module` that `includes()` dependencies
- [ ] **Platform module**: if the feature needs platform-specific code, use `internal expect val PlatformModule: Module`
- [ ] **Routes**: `@Serializable data class/object : NavKey` in the `api` module
- [ ] **State**: `StateFlow`, exposed via `asStateFlow()` or `stateIn()`
- [ ] **Actions**: `sealed interface`, processed by `onAction()` in the ViewModel
- [ ] **Events**: `sealed interface`, sent via `Channel`, collected via `ObserveAsEvents`
- [ ] **Root+Screen split**: Screen composable has no ViewModel, receives all state/callbacks as parameters
- [ ] **Previews**: at least one `@LocalizedPreview` and one `@AdaptivePreview` per screen
- [ ] **Dispatchers**: all injected by named qualifier (`named("ioDispatcher")` etc.), never hardcoded
- [ ] **Mappers**: `internal` extension functions in a `mapper/` subpackage
- [ ] **Opt-ins**: `@file:OptIn` at file level, not per-usage
- [ ] **Dependencies**: `api` module has no dependencies on `impl`; `impl` depends on `api` modules only
- [ ] **Build plugins**: correct convention plugins per module type (`kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` etc.)
- [ ] **No hardcoded plugins**: use `alias(libs.plugins.f776.xxx)`, never bare string IDs
