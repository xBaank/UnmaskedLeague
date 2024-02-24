plugins {
    kotlin("multiplatform") version "1.9.22"
}
val ktor_version: String by project

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:$ktor_version")
                implementation("io.github.xbaank:simpleJson-core:3.0.0")
                implementation("io.arrow-kt:arrow-core:1.2.1")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("com.squareup.okio:okio:3.3.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
