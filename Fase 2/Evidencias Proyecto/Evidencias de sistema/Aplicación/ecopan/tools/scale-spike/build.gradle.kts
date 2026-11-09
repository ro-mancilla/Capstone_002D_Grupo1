plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.f776.ecopan.tools.scalespike.MainKt")
}

dependencies {
    implementation(libs.jserialcomm)
}
