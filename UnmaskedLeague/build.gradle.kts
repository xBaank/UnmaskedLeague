import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("multiplatform") version "2.1.21"
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
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_11.toString()
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
                implementation("io.ktor:ktor-server-core:${ktor_version}")
                implementation("io.ktor:ktor-server-cio:${ktor_version}")
                implementation("io.ktor:ktor-client-core:${ktor_version}")
                implementation("io.ktor:ktor-client-cio:${ktor_version}")
                implementation("io.ktor:ktor-client-encoding:${ktor_version}")
                implementation("io.ktor:ktor-network:$ktor_version")
                implementation("io.ktor:ktor-network-tls:$ktor_version")
                implementation("io.github.xbaank:simpleJson-core:4.0.0")
                implementation("io.arrow-kt:arrow-core:2.1.2")
                // https://mvnrepository.com/artifact/org.yaml/snakeyaml
                implementation("org.yaml:snakeyaml:2.5")
                implementation("com.squareup.okio:okio:3.16.0")
                // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
                implementation("com.squareup.okhttp3:okhttp:5.2.0")
                implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
                // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
                implementation("ch.qos.logback:logback-classic:1.5.19")
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
