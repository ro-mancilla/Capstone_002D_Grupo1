plugins {
    alias(libs.plugins.f776.kotlinMultiplatform)
    alias(libs.plugins.f776.androidKmpLibrary)
}

kotlin {
    sourceSets {
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }

        jvmMain {
            dependsOn(jvmAndAndroidMain)
        }

        androidMain {
            dependsOn(jvmAndAndroidMain)
        }

        jvmAndAndroidMain.dependencies {
            implementation(libs.bcrypt)
        }

        commonMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
