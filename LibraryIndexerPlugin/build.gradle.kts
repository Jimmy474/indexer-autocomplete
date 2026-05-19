import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    kotlin("plugin.serialization") version "2.3.21"
}

dependencies {
    implementation(project(":LibraryIndexerLib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.2")
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}
