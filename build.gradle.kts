plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "org.example"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile and test against the locally installed Android Studio.
        // On macOS the path is the .app bundle; on Linux/Windows adjust accordingly.
        local("/Applications/Android Studio.app")

        // Android-specific APIs — needed at runtime for rename of Android resources.
        // AS Narwhal bundles Kotlin 2.3.0 while our compiler is 2.1.20, so we suppress
        // the metadata-version mismatch warnings (classes are still byte-compatible).
        bundledPlugin("org.jetbrains.android")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Compatible with Android Studio Hedgehog (AI-233) and above.
            sinceBuild = "233"
        }

        changeNotes = """
            Initial version — Rename Screen (All Files) action.
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // Android Studio Narwhal bundles Kotlin 2.3.0 while we compile with 2.1.20.
        // The class files are byte-compatible; only the Kotlin metadata version differs.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
