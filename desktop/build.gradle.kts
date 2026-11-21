import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.rython"
version = "1.0.0"

// Compile for the Java 17 bytecode level but run on whatever JDK 17+ is installed;
// this machine (and most Windows machines) ships a newer JDK than 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.rython.desktop.MainKt"

        nativeDistributions {
            // Built on Linux: deb/rpm and the plain app-image.
            // Built on Windows: msi and exe.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Rython-Authenticator"
            packageVersion = "1.0.0"
            description = "Two-password offline authenticator (TOTP/HOTP/Steam)"
            vendor = "Rython"
            modules("java.naming", "jdk.unsupported", "java.sql")

            // Same launcher artwork as the Android app (mipmap-xxxhdpi/ic_launcher.png).
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }
        }
    }
}

