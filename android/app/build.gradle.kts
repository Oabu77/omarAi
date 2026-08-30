import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun configuredValue(name: String, fallback: String): String =
    providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: fallback

val omarApiBaseUrl = configuredValue("OMAR_API_BASE_URL", "https://example.invalid/")
    .let { if (it.endsWith('/')) it else "$it/" }
val privacyPolicyUrl = configuredValue("OMAR_PRIVACY_POLICY_URL", "https://example.invalid/privacy")
val deletionUrl = configuredValue("OMAR_ACCOUNT_DELETION_URL", "https://example.invalid/delete-account")
val apiConfigured = !omarApiBaseUrl.contains("example.invalid")

fun isPublicHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        !uri.host.endsWith(".invalid", ignoreCase = true)
}.getOrDefault(false)

android {
    namespace = "com.darcloud.omarai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.darcloud.omarai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "OMAR_API_BASE_URL", "\"${omarApiBaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "OMAR_API_CONFIGURED", apiConfigured.toString())
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ACCOUNT_DELETION_URL", "\"${deletionUrl.replace("\"", "\\\"")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.android.billingclient:billing-ktx:9.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val validateReleaseConfiguration = tasks.register("validateReleaseConfiguration") {
    group = "verification"
    description = "Rejects placeholder or non-HTTPS legal URLs before a release build."
    doLast {
        check(isPublicHttpsUrl(privacyPolicyUrl)) {
            "OMAR_PRIVACY_POLICY_URL must be a public HTTPS URL for release builds."
        }
        check(isPublicHttpsUrl(deletionUrl)) {
            "OMAR_ACCOUNT_DELETION_URL must be a public HTTPS URL for release builds."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "lintRelease") {
        dependsOn(validateReleaseConfiguration)
    }
}
