plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.f776.composeMultiplatform)
}

// Domain-free by construction: no dependency on any other project module, so every client tier
// (desktop till, baker mobile, shared features) can take it. Compose, Material 3, the icon set
// and Compose Resources come from the convention plugin.
