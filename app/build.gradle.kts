import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.calendar"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.calendar"

        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    val configuredServerUrl = providers.gradleProperty("secureCalendarServerUrl")
        .orElse("")
        .get()

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("secureCalendarIntegrationTests").orElse("false").get().toBoolean()) {
                applicationIdSuffix = ".integrationtest"
            }
            manifestPlaceholders["cleartextTraffic"] = "true"
            buildConfigField("String", "SERVER_BASE_URL", "\"${providers.gradleProperty("secureCalendarDebugServerUrl").orElse("https://10.0.2.2:8443").get()}\"")
        }
        release {
            manifestPlaceholders["cleartextTraffic"] = "false"
            buildConfigField("String", "SERVER_BASE_URL", "\"$configuredServerUrl\"")
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        isCoreLibraryDesugaringEnabled = true
    }

}

dependencies {
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )
    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )
    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )
    implementation("androidx.compose.material:material-icons-extended")
    implementation(files("libs/opaque_ke_uniffi-release.aar"))
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}

// Reject misplaced ELF artifacts before they can ship in an APK.
val verifyBee2Abi = tasks.register("verifyBee2Abi") {
    inputs.files(fileTree("src/main/jniLibs") { include("**/libbee2bridge.so", "**/libbee2mapping.so") })
    doLast {
        inputs.files.files.forEach { library ->
            val header = library.inputStream().use { it.readNBytes(20) }
            check(header.size == 20) { "Truncated Bee2 ELF header" }
            val machine = (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
            val valid = when (library.parentFile.name) {
                "arm64-v8a" -> header[4].toInt() == 2 && machine == 183
                "armeabi-v7a" -> header[4].toInt() == 1 && machine == 40
                else -> false
            }
            check(header.take(4) == listOf<Byte>(0x7f, 0x45, 0x4c, 0x46) && valid) {
                "Bee2 ELF ABI does not match its jniLibs directory: ${library.name}"
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyBee2Abi) }

val releaseEndpoint = providers.gradleProperty("secureCalendarServerUrl").orElse("").get()
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val uri = URI(releaseEndpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.host !in setOf("localhost", "127.0.0.1", "::1")) {
            "Release requires -PsecureCalendarServerUrl=https://your-real-server"
        }
    }
}
