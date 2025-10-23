plugins {
    kotlin("multiplatform") version "2.2.21"
}
val ktor_version: String by project

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:$ktor_version")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("com.squareup.okio:okio:3.16.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
