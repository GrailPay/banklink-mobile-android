import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Kotlin 2.x compilerOptions DSL (replaces the removed android.kotlinOptions block).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// GrailPay-internal QA/staging host for the sample app's debug builds. Read from local.properties
// (gitignored) so the internal URL never lands in the public repo. Empty when unset, so cloning
// without it still builds — the SDK then just uses its production default.
val qaBaseUrl: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("QA_BASE_URL", "")

// Release signing config, read from keystore.properties (gitignored) or env vars for CI.
// Keys: storeFile, storePassword, keyAlias, keyPassword. When the file/env aren't present the
// release build falls back to the debug keystore below, so `assembleRelease` still produces an
// installable APK (and App Links keep working — assetlinks pins the debug cert).
val releaseKeystoreProps: Properties? = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.takeIf { it.getProperty("storeFile") != null || System.getenv("RELEASE_STORE_FILE") != null }

fun keystoreValue(key: String, env: String): String? =
    releaseKeystoreProps?.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.grailpay.banklink.sample"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.grailpay.banklink.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injected from local.properties (gitignored). The sample app passes this to the SDK's
        // testEnvironmentUrl() in debug builds to exercise QA/staging without a separate SDK build.
        buildConfigField("String", "QA_BASE_URL", "\"$qaBaseUrl\"")
    }

    // Shared debug keystore so every developer's debug APK is signed with the same
    // cert. The SHA-256 of this cert is pinned in
    // https://banklink-oauth.grailpay.com/.well-known/assetlinks.json — without a shared keystore
    // each developer would need their personal fingerprint added to that file.
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Only register a real release config when credentials are actually provided. Paths in
        // keystore.properties are resolved relative to the module dir.
        if (releaseKeystoreProps != null || System.getenv("RELEASE_STORE_FILE") != null) {
            create("release") {
                storeFile = file(keystoreValue("storeFile", "RELEASE_STORE_FILE") ?: "")
                storePassword = keystoreValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = keystoreValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = keystoreValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the real release cert when configured, otherwise sign with debug so the APK is
            // still installable for local testing.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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

dependencies {
    implementation(project(":bank-link"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}