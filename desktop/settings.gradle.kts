pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://dl.google.com/dl/android/maven2")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Some Compose Desktop transitives are androidx artifacts hosted on Google
        // Maven, which is declared here as a plain Maven URL.
        maven("https://dl.google.com/dl/android/maven2")
        // Mirror of Google Maven, used only when the URL above cannot serve a file
        // (some networks answer its HEAD requests with 404).
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

rootProject.name = "rython-authenticator"
