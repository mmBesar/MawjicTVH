import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

val appVersion = providers.gradleProperty("versionName").orElse("0.1.2").get()
val appVersionCode = providers.gradleProperty("versionCode").orElse("3").get().toInt()

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
            implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.ui:ui:1.10.3")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.10.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.4")
            implementation("androidx.media3:media3-exoplayer:1.11.1")
            implementation("androidx.media3:media3-ui:1.11.1")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

android {
    namespace = "app.mawjic.tvh"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.mawjic.tvh"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }
    sourceSets.getByName("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

compose.desktop {
    application {
        mainClass = "app.mawjic.tvh.desktop.MainKt"
        nativeDistributions {
            packageName = "MawjicTVH"
            packageVersion = appVersion
            description = "Native Tvheadend client using HTSP"
            vendor = "Mohammed Besar"
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            linux {
                packageName = "mawjictvh"
                appCategory = "AudioVideo"
                rpmLicenseType = "MIT"
                debMaintainer = "Mohammed Besar <mbesar@proton.me>"
                iconFile.set(rootProject.file("assets/icons/mawjictvh.png"))
            }
        }
    }
}


tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}
