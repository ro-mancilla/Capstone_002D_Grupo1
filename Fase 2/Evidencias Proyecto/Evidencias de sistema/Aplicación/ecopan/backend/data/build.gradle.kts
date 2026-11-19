plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.f776"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Exposed's uuid()/UuidTable use kotlin.uuid.Uuid, which is still opt-in on Kotlin 2.4.
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(libs.bundles.exposed)
    implementation(libs.koin.core)
    implementation(libs.kourier)
    // The Azure Service Bus broker family. Both broker backends are always on the classpath; which
    // one is instantiated is a runtime decision (see MessageBrokerModule), not a build-time one.
    implementation(libs.azure.messaging.servicebus)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.postgres)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junitJupiter)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}