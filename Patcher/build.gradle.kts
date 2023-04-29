plugins {
    kotlin("multiplatform") version "1.8.20"
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
                // https://mvnrepository.com/artifact/com.google.flatbuffers/flatbuffers-java
                implementation("com.google.flatbuffers:flatbuffers-java:23.3.3")
                implementation("com.github.luben:zstd-jni:1.5.5-2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
