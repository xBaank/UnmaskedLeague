import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("multiplatform") version "2.1.0"
}

repositories {
    mavenCentral()
}

val ktor_version: String by project
val kotlinProcessVersion: String by project
kotlin {
    jvm {
        withJava()
        java {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        val jvmJar by tasks.getting(Jar::class) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            doFirst {
                manifest {
                    attributes["Main-Class"] = "unmaskedLeague.MainKt"
                }
                from(configurations.getByName("runtimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
            }
        }
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:$ktor_version")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("io.github.xbaank:simpleJson-core:3.0.3")
                implementation("io.arrow-kt:arrow-core:2.0.0")
                // https://mvnrepository.com/artifact/org.yaml/snakeyaml
                implementation("org.yaml:snakeyaml:2.3")
                implementation("com.squareup.okio:okio:3.3.0")
                // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                // Check the 🔝 maven central badge 🔝 for the latest $kotlinProcessVersion
                implementation("com.github.pgreze:kotlin-process:$kotlinProcessVersion")
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
