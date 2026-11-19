plugins {
    alias(libs.plugins.android.test)
    // Gives this module the app's benchmarkRelease variant: minified with the release rules,
    // profileable, debug-signed. The app does not take a profile from this module.
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.f776.benchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":android:app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.ui.automator)
    implementation(libs.benchmark.junit)
}

androidComponents {
    // Only the benchmarkRelease variant measures anything.
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmarkRelease"
    }
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}
