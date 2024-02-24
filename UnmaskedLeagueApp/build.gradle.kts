import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
}

val ktor_version: String by project
val kotlinProcessVersion: String by project

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation("io.ktor:ktor-network:$ktor_version")
            implementation("io.ktor:ktor-network-tls:$ktor_version")
            implementation("io.arrow-kt:arrow-core:1.2.1")
            implementation("io.github.xbaank:simpleJson-core:3.0.0")
            // https://mvnrepository.com/artifact/org.yaml/snakeyaml
            implementation("org.yaml:snakeyaml:2.0")
            implementation("com.squareup.okio:okio:3.3.0")
            // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
            implementation("com.squareup.okhttp3:okhttp:4.11.0")
            // Check the 🔝 maven central badge 🔝 for the latest $kotlinProcessVersion
            implementation("com.github.pgreze:kotlin-process:$kotlinProcessVersion")
            implementation(project(":Rtmp"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}


compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.unmaskedLeague"
            packageVersion = "1.0.0"
        }
    }
}