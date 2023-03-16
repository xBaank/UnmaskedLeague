plugins {
    kotlin("multiplatform") version "1.7.20"
}

repositories {
    mavenCentral()
}

val ktor_version: String by project

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:$ktor_version")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("io.arrow-kt:suspendapp:0.4.0")
                implementation("io.github.xbaank:simpleJson-core:1.0.1")
                implementation("io.arrow-kt:arrow-core:1.1.5")
                implementation(project(":Rtmp"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
