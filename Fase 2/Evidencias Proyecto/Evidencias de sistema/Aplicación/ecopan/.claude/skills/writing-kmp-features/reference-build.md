# Build System — Convention Plugins

## Composite build

The `build-logic/` directory contains a Gradle composite build with a `:convention` module that registers custom plugins at `build-logic/convention/src/main/kotlin/`. Referenced via `alias(libs.plugins.f776.xxx)` — never by bare string ID.

## Convention Plugin Catalog

### `com.f776.kotlinMultiplatform` — KotlinMultiplatformConventionPlugin

Applied to **every KMP library module**. Configures:
- Applies `org.jetbrains.kotlin.multiplatform`
- Creates targets: `jvm()`, `iosArm64()`, `iosSimulatorArm64()` (static frameworks)
- `jvmToolchain(17)`, `-Xexpect-actual-classes` on all source sets
- `@ExperimentalUuidApi` opt-in, `kermit` logging to `commonMain`
- `kotlin.test` + `project(":core:test")` to `commonTest`

### `com.f776.androidKmpLibrary` — AndroidLibraryConventionPlugin

Applied to KMP modules that target Android. Configures:
- Applies `com.android.kotlin.multiplatform.library`
- Derives `namespace` from module path (e.g., `:data:foo:impl` → `com.f776.ecopan.data.foo.impl`)
- Sets `compileSdk`, `minSdk` from version catalog
- Host-side unit tests (Robolectric, coroutines, Compose UI test)
- Device-side instrumented tests (AndroidJUnitRunner)

### `com.f776.composeMultiplatform` — ComposeConventionPlugin

Applied to KMP modules with Compose UI. Configures:
- Applies `org.jetbrains.compose` + Compose compiler plugin
- `generateResClass = always`
- Adds all common Compose deps to `commonMain`: runtime, foundation, material3, ui, resources, ui-tooling-preview, adaptive-navigation, material-icons-extended, lifecycle-viewmodel-compose, lifecycle-runtime-compose, adaptive-layout
- Compose UI test to `commonTest`
- `ui-tooling` (debug) + `activity-compose` to `androidMain`
- Desktop compose + coroutines-swing to `jvmMain`
- Compose compiler stability config from root `compose_compiler_config.conf`

### `com.f776.roomMultiplatform` — RoomDatabaseConventionPlugin

Applied to the module containing Room database. Configures:
- Applies `com.google.devtools.ksp` + `androidx.room`
- Schema directory: `$projectDir/schemas`
- `room-runtime` to `commonMain`, `sqlite-bundled` to `jvmMain`
- KSP processors for all targets: `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`, `kspJvm`

### `com.f776.androidApplication` — AndroidApplicationConventionPlugin

Applied to `:android:app`. Configures:
- Applies `com.android.application` + Compose compiler
- `namespace`, `applicationId`, `compileSdk`, `minSdk`, `targetSdk`, version from `version.properties`
- ProGuard/R8 release builds, benchmark build type, Java 17

### `com.f776.desktopApplication` — DesktopApplicationConventionPlugin

Applied to `:jvm:app`. Configures:
- Applies `org.jetbrains.kotlin.jvm` + `org.jetbrains.compose` + Compose compiler
- Native distributions: DMG, MSI, DEB
- ProGuard for release

### Other plugins

- **`com.f776.glanceWidget`** — for Glance app widget modules
- **`com.f776.wearApplication`** — for Wear OS entry points

## Which Plugins Per Module Type

| Module type | Required plugins | Extra common |
|------------|-----------------|-------------|
| **data:api** (interfaces) | `kotlinMultiplatform` | — |
| **data:impl** (no Compose) | `kotlinMultiplatform` | — |
| **core:database** (Room) | `kotlinMultiplatform`, `androidKmpLibrary`, `roomMultiplatform` | `kotlinSerialization` |
| **core:datastore** | `kotlinMultiplatform`, `androidKmpLibrary` | — |
| **core:ui** (Compose) | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` |
| **core:common** (utilities) | `kotlinMultiplatform` | — |
| **feature:api** (routes) | `kotlinMultiplatform`, `androidKmpLibrary` | `kotlinSerialization` |
| **feature:impl** (screens) | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` |
| **Android app** | `androidApplication`, `composeCompiler` (standalone) | `kotlinSerialization` |
| **JVM desktop** | `desktopApplication` | — |
| **Root compose app** | `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform` | `kotlinSerialization` |

**Rules:**
- Every KMP module starts with `kotlinMultiplatform`. Add `androidKmpLibrary` if it targets Android.
- Add `composeMultiplatform` if it has Compose UI. This brings all Compose deps automatically — don't re-add them.
- Add `roomMultiplatform` if it uses Room. This brings KSP + Room runtime — don't add KSP manually.
- Never manually apply `kotlin("multiplatform")`, `kotlin("plugin.compose")`, or `kotlinx-serialization` by base IDs.
- The root `:composeApp` module applies `kotlinMultiplatform` + `androidKmpLibrary` + `composeMultiplatform`.

## Build Script Examples

### data:api module

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

### data:impl module

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

### feature:api module

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

### feature:impl module

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
            api(projects.feature.foo.api)
            implementation(projects.data.foo.impl)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.bundles.navigation3)
        }
    }
}
```

### core:database module

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

## Version catalog

All deps in `gradle/libs.versions.toml`. Custom convention plugins have `version = "unspecified"`:

```toml
[plugins]
f776-kotlinMultiplatform = { id = "com.f776.kotlinMultiplatform", version = "unspecified" }
f776-androidKmpLibrary = { id = "com.f776.androidKmpLibrary", version = "unspecified" }
f776-composeMultiplatform = { id = "com.f776.composeMultiplatform", version = "unspecified" }
f776-roomMultiplatform = { id = "com.f776.roomMultiplatform", version = "unspecified" }
f776-androidApplication = { id = "com.f776.androidApplication", version = "unspecified" }
f776-desktopApplication = { id = "com.f776.desktopApplication", version = "unspecified" }
```

Use `api()` for transitive deps, `implementation()` for internal deps, `implementation(projects.xxx)` for project deps.

## Module naming

- Lowercase with hyphens: `:feature:schedule:api`, `:data:preferences:impl`
- `:core:*` for shared infrastructure, `:data:*` for data layer, `:feature:*` for features
- Android `namespace` derived automatically from module path by `androidKmpLibrary`
