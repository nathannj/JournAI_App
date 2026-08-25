import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

val appVersionFile = rootProject.file("version.properties")
val appVersionProperties = Properties().apply { appVersionFile.inputStream().use { load(it) } }
val appVersionCode = appVersionProperties.getProperty("VERSION_CODE").toInt()
val appVersionName = appVersionProperties.getProperty("VERSION_NAME")

abstract class IncrementAppVersionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun increment() {
        val file = versionFile.get().asFile
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        val currentCode = properties.getProperty("VERSION_CODE").toInt()
        val currentName = properties.getProperty("VERSION_NAME")
        val nameParts = currentName.split('.')
        val nextPatch = nameParts.last().toIntOrNull()?.plus(1) ?: (currentCode + 1)
        val nextName = if (nameParts.size >= 3) {
            nameParts.dropLast(1).joinToString(".") + "." + nextPatch
        } else {
            "0.0.$nextPatch"
        }
        properties.setProperty("VERSION_CODE", (currentCode + 1).toString())
        properties.setProperty("VERSION_NAME", nextName)
        file.outputStream().use { properties.store(it, "JournAI app version") }
    }
}

android {
    namespace = "com.journai.journai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.journai.journai"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("boolean", "DEBUG", "true")
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema location
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }

        // Native (JNI/C++)
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Hook up CMake for whisper.cpp JNI
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

val incrementVersionTask = tasks.register<IncrementAppVersionTask>("incrementAppVersion") {
    versionFile.set(appVersionFile)
}
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }
    .configureEach { finalizedBy(incrementVersionTask) }

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // Model download
    implementation(libs.okhttp)

    // Serialization
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    kapt(libs.moshi.codegen)
    implementation(libs.kotlinx.serialization.json)

    // Media
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
        implementation(libs.androidx.lifecycle.runtime.compose)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher.android)
    implementation(libs.play.integrity)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.splashscreen)
    
    // Speech Recognition
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
