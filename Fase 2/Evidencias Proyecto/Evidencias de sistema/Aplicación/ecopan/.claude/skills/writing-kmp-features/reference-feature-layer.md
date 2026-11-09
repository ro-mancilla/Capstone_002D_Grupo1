# Feature Layer — Routes, ViewModels, Screens & Navigation

## Route Definitions (api module)

Routes are `@Serializable` classes/objects implementing `NavKey`:

```kotlin
@Serializable
data class FooDetailRoute(val fooId: Int) : NavKey

@Serializable
data object FooListRoute : NavKey
```

Serializer registration:

```kotlin
fun PolymorphicModuleBuilder<NavKey>.fooRoutes() {
    subclass(FooDetailRoute.serializer())
    subclass(FooListRoute.serializer())
}
```

## ViewModels

**Constructor** — all deps injected, dispatchers by named qualifier:

```kotlin
internal class FooViewModel(
    private val fooRepository: FooRepository,
    private val defaultDispatcher: CoroutineDispatcher,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
```

**State exposure** — prefer `stateIn()` for Flow chains:

```kotlin
val foos: StateFlow<List<FooUi>> = fooRepository.getFoos()
    .map { it.map { foo -> foo.toFooUi() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

For mutable local state, use `MutableStateFlow` + `asStateFlow()`:

```kotlin
private val _isLoading = MutableStateFlow(false)
val isLoading = _isLoading.asStateFlow()
```

For state surviving config changes:

```kotlin
private val _currentStep by savedStateHandle.getMutableStateFlow(
    coroutineScope = viewModelScope, defaultValue = 1
)
```

## Unidirectional Data Flow (Action / Event)

Every interactive screen uses **sealed interface Action** and **sealed interface Event**:

```kotlin
internal sealed interface FooAction {
    data class OnSelectFoo(val id: Int) : FooAction
    data object OnCreateNew : FooAction
}

internal sealed interface FooEvent {
    data class ShowMessage(val message: String) : FooEvent
    data class NavigateToDetail(val fooId: Int) : FooEvent
}
```

ViewModel handler:

```kotlin
private val _eventChannel = Channel<FooEvent>(Channel.BUFFERED)
val eventChannel = _eventChannel.receiveAsFlow()

fun onAction(action: FooAction) {
    when (action) {
        FooAction.OnCreateNew -> viewModelScope.launch { /* ... */ }
        is FooAction.OnSelectFoo -> { /* update state */ }
    }
}
```

## Screen Composables (Root + Screen split)

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

    FooScreen(foos = foos, onAction = viewModel::onAction, onGoBack = onGoBack)
}

// Screen — pure composable, no ViewModel, previewable
@Composable
internal fun FooScreen(
    foos: List<FooUi>,
    onAction: (FooAction) -> Unit,
    onGoBack: () -> Unit,
) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Foo") }) }) { padding ->
        // UI only — no ViewModel, no navigation
    }
}
```

**Benefits:** Screen is previewable; Root handles lifecycle and side effects; navigation from outside.

## Navigation (Navigation3 + Koin)

### Navigation entries

```kotlin
val FooNavigationModule = module {
    navigation<FooDetailRoute>(metadata = SLIDE_HORIZONTALLY) { route ->
        FooDetailScreenRoot(
            onEdit = { get<Navigator>().goTo(EditRoute(it)) },
            onGoBack = { get<Navigator>().goBack() },
        )
    }
    navigation<FooListRoute> {
        FooListScreenRoot()
    }
}
```

Animation metadata: `SLIDE_HORIZONTALLY`, `SLIDE_VERTICALLY`, `BOUNCE_APPEAR`.

### Navigator class

```kotlin
class Navigator {
    lateinit var backStack: NavBackStack<NavKey>
    fun goTo(destination: NavKey) { backStack.add(destination) }
    fun goBack() { backStack.removeLastOrNull() }
}
```

### Root setup

```kotlin
val backStack = rememberNavBackStack(
    configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) { fooRoutes(); barRoutes() }
        }
    },
    FooListRoute
)
NavDisplay(backStack = backStack, entryProvider = koinEntryProvider())
```

### Nested navigation (multi-step wizards)

Multi-step wizards create their own `rememberNavBackStack` inside a parent scaffold:

```kotlin
val wizardBackStack = rememberNavBackStack(
    configuration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(Step1Route.serializer())
                subclass(Step2Route.serializer())
            }
        }
    },
    Step1Route
)
NavDisplay(backStack = wizardBackStack, entryProvider = entryProvider {
    entry<Step1Route> { StepOneScreen(onContinue = { wizardBackStack.add(Step2Route) }) }
    entry<Step2Route> { StepTwoScreen(onFinish = { parentNavigator.goBack() }) }
})
```

## UI Conventions

### Theme
- Custom composable wrapping `MaterialTheme` with seed-based light/dark color schemes.
- Two font families: display (Figtree) and body (Roboto), loaded via Compose Resources `Font()`.
- Dynamic color on Android 12+ via actual implementation; other platforms use static scheme.

### Adaptive layout
```kotlin
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
if (windowSizeClass.isCompact) { /* phone */ } else { /* tablet/desktop */ }
```

### UI models
- `@Stable` annotation on UI data classes for Compose optimization.
- `@Serializable` when state survives config changes via `SavedStateHandle`.
- `@Serializable(with = ColorAsIntSerializer::class)` for `Color` fields.
- Mapper extension functions `toXxxUi()` on domain models.

### Previews
Every screen must have at least:
- `@LocalizedPreview` (RTL + multiple locales: en, es, ja, ar)
- `@AdaptivePreview` (multiple screen sizes: phone, foldable, tablet, desktop)

### Opt-ins
`@file:OptIn` at file level, never per-usage:
```kotlin
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
```

### Compose Resources
Access via generated `Res` class with namespaced imports to disambiguate modules:
```kotlin
import feature_foo.generated.resources.Res as FooRes
Text(stringResource(FooRes.string.my_string))
```

## Dispatcher Injection (Reiteration)

Never hardcode. Always inject:
```kotlin
get<CoroutineDispatcher>(named("ioDispatcher"))     // DataStore, file, network I/O
get<CoroutineDispatcher>(named("defaultDispatcher"))  // Room DB, CPU work
get<CoroutineDispatcher>(named("mainDispatcher"))     // UI updates
```

For fire-and-forget tasks, use injected coroutine scopes with `SupervisorJob()`:
```kotlin
factory(named("defaultCoroutineScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
factory(named("ioCoroutineScope")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
```
