import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidLibrary {
        namespace = "org.opendnevnik.shared"
        compileSdk = 36
        minSdk = 26
    }
    jvm()
    if (providers.gradleProperty("enableIos").orNull == "true") {
        val xcFramework = XCFramework("DnevnikShared")
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
            it.binaries.framework { baseName = "DnevnikShared"; isStatic = true; xcFramework.add(this) }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
