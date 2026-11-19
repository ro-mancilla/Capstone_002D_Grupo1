plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.data.hardware.api)
            implementation(projects.core.datastore)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }

        // Real hardware drivers exist only on JVM (01 §1) -- jSerialComm (SerialScale) and
        // escpos-coffee (EscPosPrinter) are jvmMain-only dependencies, never leaked to
        // Android/iOS compilations.
        jvmMain.dependencies {
            implementation(libs.jserialcomm)
            implementation(libs.escpos.coffee)
        }
    }
}
