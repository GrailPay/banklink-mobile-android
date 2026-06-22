plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    `maven-publish`
}

// Published Maven coordinates: com.grailpay:bank-link:<version>
// Version is driven by the `versionName` Gradle property — set by the release workflow from
// the GitHub release tag (./gradlew :bank-link:publish -PversionName=$TAG). A leading "v" is
// stripped so v1.2.3 and 1.2.3 both publish as 1.2.3. Falls back to a SNAPSHOT for local
// builds with no property set.
group = "com.grailpay"
version = (findProperty("versionName") as String?)?.removePrefix("v") ?: "0.1.0-SNAPSHOT"

// Environment URLs are no longer baked in at build time. The SDK ships ONE artifact and picks
// its host at runtime: BankLinkConfig.Builder defaults to PRODUCTION and exposes SANDBOX; the
// Quiltt connector id is returned per-merchant by the session response. No env vars are required
// to build or publish.

// Kotlin 2.x compilerOptions DSL (replaces the removed android.kotlinOptions block).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjvm-default=all-compatibility")
    }
}

android {
    namespace = "com.grailpay.banklink"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug { /* build optimization only — no env URLs here */ }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Tell AGP to build a publishable "release" component with sources + javadoc jars.
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// ---------------------------------------------------------------------------
// GitHub Packages publishing (Apache Maven registry — maven.pkg.github.com)
//
// No GPG signing required — auth is a GitHub Personal Access Token (classic)
// with the `write:packages` scope (publish) / `read:packages` (consume).
// Credentials are read from Gradle properties / env vars, never committed:
//   gpr.user  / gpr.key                    (local — ~/.gradle/gradle.properties)
//   GITHUB_ACTOR / GITHUB_TOKEN            (CI — GitHub Actions provides these)
//
// Publish coordinates: com.grailpay:bank-link:<version>
// Publish with:  ./gradlew :bank-link:publish
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.grailpay"
            artifactId = "bank-link"
            version = project.version.toString()

            // AGP creates the "release" software component once the library is
            // evaluated; wire it up so the AAR + sources + javadoc are published.
            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("GrailPay BankLink Android SDK")
                description.set(
                    "Native Kotlin SDK for the GrailPay BankLink bank-connection flow on Android."
                )
                url.set("https://github.com/GrailPay/banklink-mobile-android")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("grailpay")
                        name.set("GrailPay")
                        url.set("https://grailpay.com")
                    }
                }
                scm {
                    url.set("https://github.com/GrailPay/banklink-mobile-android")
                    connection.set("scm:git:git://github.com/GrailPay/banklink-mobile-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/GrailPay/banklink-mobile-android.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GrailPay/banklink-mobile-android")
            credentials {
                username = (findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR"))?.toString()
                password = (findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN"))?.toString()
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.quiltt.connector)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.test.parameter.injector)
    testImplementation(libs.robolectric)
}