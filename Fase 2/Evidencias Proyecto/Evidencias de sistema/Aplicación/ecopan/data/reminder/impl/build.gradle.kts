plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    sourceSets {
        val iosAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }

        androidMain {
            dependsOn(iosAndAndroidMain)
        }

        iosMain {
            dependsOn(iosAndAndroidMain)
        }

        iosAndAndroidMain.dependencies {
            implementation(libs.moko.notifications)
            api(libs.moko.permissions)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
            implementation(libs.permission.flow.android)
        }

        androidHostTest.dependencies {
            implementation(libs.androidx.work.testing)
        }

        commonMain.dependencies {
            api(projects.data.reminder.api)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.resources)
            implementation(libs.compose.runtime)
        }
    }
}

// commonTest here only holds a reusable FakeReminderScheduler for consumers, with no local
// @Test cases of its own -- the jvm test task would otherwise fail the build for finding none.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}