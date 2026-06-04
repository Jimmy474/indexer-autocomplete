import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.changelog)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":LibraryIndexerLib"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
    testImplementation(libs.junit.jupiter)

    intellijPlatform {
        intellijIdea(libs.versions.intellij.idea.get())
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}
