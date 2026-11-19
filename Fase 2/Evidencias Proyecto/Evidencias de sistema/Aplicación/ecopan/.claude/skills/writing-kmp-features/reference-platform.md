# Platform Abstraction — expect/actual Patterns

## When to Use Each Pattern

| Use case | Pattern | Visibility |
|----------|---------|------------|
| Platform-specific DI | `expect val PlatformModule: Module` | `internal` |
| Platform-specific service with interface | `expect class XxxImpl : SomeInterface` | `internal` |
| Platform-specific component (e.g., Toaster) | `expect class Xxx` | `internal` |
| Platform-specific composable | `expect fun XxxScreen()` | `internal` |
| Room DB constructor | `expect object DbConstructor : RoomDatabaseConstructor<...>` | `public` |
| Platform locale | `expect class PlatformLocale` (via `actual typealias`) | `public` |

## Non-Composable expect/actual

```kotlin
// commonMain
internal expect class FooServiceImpl : FooService {
    override suspend fun doSomething()
}

// androidMain
internal actual class FooServiceImpl(private val context: Context) : FooService {
    actual override suspend fun doSomething() { /* Android */ }
}

// iosMain
internal actual class FooServiceImpl : FooService {
    actual override suspend fun doSomething() { /* iOS */ }
}
```

Constructors may differ by platform — Android may need `Context`, iOS/JVM do not.

## Composable expect/actual

```kotlin
// commonMain
@Composable
internal expect fun FooPlatformScreen(onBack: () -> Unit)

// androidMain
@Composable
internal actual fun FooPlatformScreen(onBack: () -> Unit) { /* full UI */ }

// iosMain / jvmMain — stub
@Composable
internal actual fun FooPlatformScreen(onBack: () -> Unit) = Unit
```

## PlatformModule DI Pattern

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

## Shared Source Sets

| Source set | Targets | Usage |
|-----------|---------|-------|
| `commonMain` | All | Interfaces, domain models, common logic |
| `androidMain` | Android | Android-specific actuals |
| `iosMain` | iOS | iOS-specific actuals |
| `jvmMain` | Desktop | Desktop-specific actuals |
| `jvmAndAndroidMain` | JVM + Android | `java.time` implementations |
| `jvmAndIosMain` | JVM + iOS | Skia implementations (`ImageBitmap`) |

## iOS Platform Code

- Import from `platform.*`: `platform.UIKit.*`, `platform.UserNotifications.*`, `platform.Foundation.*`
- Use `@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)` for cinterop
- Prefer `NSData`/`NSUrl`/`NSDateComponents` over raw `kotlinx.cinterop`
- Cache-heavy iOS APIs in mutable maps (e.g., `mutableMapOf<NSLocale, NSCalendar>`)
- Fall back to English when platform APIs return unexpected values
