plugins {
    alias(libs.plugins.f776.desktopApplication)
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.ui)
    implementation(projects.data.preferences.api)
    implementation(libs.koin.compose.viewmodel)
}

compose.resources {
    packageOfResClass = "ecopan.jvmapp.generated.resources"
}