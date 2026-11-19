package com.f776.convention

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.util.Properties

fun Project.getVersionCodeAndNameProperty(): Pair<Int, String> {
    val versionProperties = readVersionProperties(this)
    val versionCodeProperty = versionProperties.getProperty("versionCode").toInt()
    val versionMajorProperty = versionProperties.getProperty("versionMajor").toInt()
    val versionMinorProperty = versionProperties.getProperty("versionMinor").toInt()
    val versionPatchProperty = versionProperties.getProperty("versionPatch").toInt()
    val versionNameProperty = "$versionMajorProperty.$versionMinorProperty.$versionPatchProperty"

    return versionCodeProperty to versionNameProperty
}

private fun readVersionProperties(project: Project): Properties {
    val versionFile: RegularFile = project.rootProject.layout.projectDirectory.file("version.properties")
    return Properties().apply {
        versionFile.asFile.inputStream().use { load(it) }
    }
}
