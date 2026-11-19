rootProject.name = "ecopan"
includeBuild("build-logic")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.5.1")
    }
}

include(":composeApp")
include(":core:navigation")
include(":android:app")
include(":jvm:app")
include(":core:common")
include(":core:domain")
include(":core:crypto")
include(":core:designsystem")
include(":core:ui")
include(":core:locale")
include(":core:test")
include(":core:capturable")
include(":core:shareable")
include(":core:datastore")
include(":core:database")
include(":data:preferences:api")
include(":data:preferences:impl")
include(":data:reminder:api")
include(":data:reminder:impl")
include(":data:example:api")
include(":data:example:impl")
include(":data:tenant:api")
include(":data:tenant:impl")
include(":data:catalog:api")
include(":data:catalog:impl")
include(":data:sync:api")
include(":data:sync:impl")
include(":data:sales:api")
include(":data:sales:impl")
include(":data:inventory:api")
include(":data:inventory:impl")
include(":data:production:api")
include(":data:production:impl")
include(":data:hardware:api")
include(":data:hardware:impl")
include(":feature:auth:impl")
include(":feature:home:api")
include(":feature:home:impl")
// Shared by every client: which branch, which bakery, how is sync doing. A feature-tier module
// so it can see both `:core:ui`'s indicator type and `:data:sync:api`'s status (see ShellContext).
include(":feature:shell:api")
include(":feature:shell:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
// Baker-mobile-only (SPECS.md Client 2). Android + iOS source sets, no JVM target at all, so the
// desktop till physically cannot depend on batch logging -- see `com.f776.kotlinMultiplatformMobile`.
include(":mobile:feature:production:api")
include(":mobile:feature:production:impl")

// Desktop-till-only (SPECS.md Client 1): a phone has no scale, drawer or printer.
include(":jvm:feature:pos:api")
include(":jvm:feature:pos:impl")
include(":benchmark")
include(":baselineProfile")

include(":tools:scale-spike")

include(":backend:app")
include(":backend:worker")
include(":backend:notifier")
include(":backend:data")
// The baker app's e2e journeys, written once in commonTest: they run on the JVM (Android host, Robolectric), on an
// Android emulator and on the iOS simulator. See docs/e2e/README.md.
include(":mobile:e2e")
