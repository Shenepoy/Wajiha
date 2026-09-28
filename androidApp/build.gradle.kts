import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val wajihaVersionName =
    providers.gradleProperty("wajiha.versionName").orNull?.takeIf { it.isNotBlank() } ?: "0.3.0"
val wajihaVersionCode =
    providers.gradleProperty("wajiha.versionCode").orNull?.toIntOrNull() ?: 3

fun gradleOrEnv(
    property: String,
    env: String,
): String? =
    providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = gradleOrEnv("wajiha.keystore.path", "WAJIHA_KEYSTORE_PATH")
val releaseKeystorePassword = gradleOrEnv("wajiha.keystore.password", "WAJIHA_KEYSTORE_PASSWORD")
val releaseKeyAlias = gradleOrEnv("wajiha.key.alias", "WAJIHA_KEY_ALIAS")
val releaseKeyPassword = gradleOrEnv("wajiha.key.password", "WAJIHA_KEY_PASSWORD")
val releaseSigningConfigured =
    !releaseKeystorePath.isNullOrBlank() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank() &&
        File(releaseKeystorePath).isFile

val abiSplitsEnabled = providers.gradleProperty("wajiha.abiSplits").orNull == "true"

val abiVersionOffsets =
    linkedMapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86" to 3,
        "x86_64" to 4,
    )

android {
    namespace = "com.wajiha.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.wajiha"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = wajihaVersionCode
        versionName = wajihaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += abiVersionOffsets.keys
        }

        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_ID",
            "\"${providers.gradleProperty("wajiha.screenscraper.devid").orNull ?: ""}\"",
        )
        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_PASSWORD",
            "\"${providers.gradleProperty("wajiha.screenscraper.devpassword").orNull ?: ""}\"",
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = abiSplitsEnabled
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == ABI }?.identifier
            val offset = abiVersionOffsets[abi]
            if (offset != null) {
                output.versionCode.set(wajihaVersionCode * 1000 + offset)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
