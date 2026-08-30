import java.util.Properties
import java.net.URI
import java.util.Locale

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
val firebaseApiKey = configuredValue("OMAR_FIREBASE_API_KEY", "")
val firebaseProjectId = configuredValue("OMAR_FIREBASE_PROJECT_ID", "")
val firebaseAppId = configuredValue("OMAR_FIREBASE_APP_ID", "")
val uploadKeystorePath = configuredValue("OMAR_UPLOAD_KEYSTORE_PATH", "")
val uploadKeystorePassword = configuredValue("OMAR_UPLOAD_KEYSTORE_PASSWORD", "")
val uploadKeyAlias = configuredValue("OMAR_UPLOAD_KEY_ALIAS", "")
val uploadKeyPassword = configuredValue("OMAR_UPLOAD_KEY_PASSWORD", "")
val requireReleaseSigning = configuredValue("OMAR_REQUIRE_RELEASE_SIGNING", "false").toBooleanStrictOrNull()
    ?: error("OMAR_REQUIRE_RELEASE_SIGNING must be true or false.")
val releaseSigningConfigured = listOf(
    uploadKeystorePath,
    uploadKeystorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all(String::isNotBlank)

fun normalizedUrlHost(value: String): String? = runCatching {
    URI(value).host?.trimEnd('.')?.lowercase(Locale.ROOT)
}.getOrNull()

fun hasReservedInvalidHost(value: String): Boolean = normalizedUrlHost(value)?.let {
    it == "invalid" || it.endsWith(".invalid")
} == true

val apiConfigured = !hasReservedInvalidHost(omarApiBaseUrl)

val ipv4Literal = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
val documentationExampleHosts = listOf("example.com", "example.net", "example.org")

fun isPublicHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return@runCatching false
    val literal = host.removePrefix("[").removeSuffix("]")
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.rawFragment == null &&
        host.contains('.') &&
        host != "localhost" &&
        !host.endsWith(".localhost") &&
        !host.endsWith(".local") &&
        !host.endsWith(".example") &&
        !host.endsWith(".test") &&
        documentationExampleHosts.none { reserved -> host == reserved || host.endsWith(".$reserved") } &&
        host != "invalid" &&
        !host.endsWith(".invalid") &&
        !ipv4Literal.matches(literal) &&
        !literal.contains(':')
}.getOrDefault(false)

android {
    namespace = "com.darcloud.omarai"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.darcloud.omarai"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "OMAR_API_BASE_URL", "\"${omarApiBaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "OMAR_API_CONFIGURED", apiConfigured.toString())
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ACCOUNT_DELETION_URL", "\"${deletionUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseApiKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProjectId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseAppId.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("upload") {
                storeFile = file(uploadKeystorePath)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("upload")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")

    implementation("com.android.billingclient:billing-ktx:9.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val verifyUrlValidationPolicy = tasks.register("verifyUrlValidationPolicy") {
    group = "verification"
    description = "Regression-checks release URL validation without DNS or network access."
    doLast {
        check(isPublicHttpsUrl("https://api.omarai.com/"))
        check(isPublicHttpsUrl("https://api.omarai.com/example.invalid/"))
        check(isPublicHttpsUrl("https://example.invalid.omarai.com/"))
        check(hasReservedInvalidHost("https://EXAMPLE.INVALID./"))
        check(!hasReservedInvalidHost("https://api.omarai.com/example.invalid/"))
        check(!hasReservedInvalidHost("https://example.invalid.omarai.com/"))
        listOf(
            "http://api.omarai.com/",
            "not a url",
            "https://localhost/",
            "https://service.local/",
            "https://policy.example/privacy",
            "https://service.test/privacy",
            "https://policy.omarai.test.example/privacy",
            "https://example.com/privacy",
            "https://policy.example.net/privacy",
            "https://www.example.org/privacy",
            "https://127.0.0.1/",
            "https://10.0.2.2/",
            "https://[::1]/",
            "https://[fc00::1]/",
            "https://intranet/",
            "https://user:pass@api.omarai.com/",
            "https://api.omarai.com/#fragment",
        ).forEach { unsafe -> check(!isPublicHttpsUrl(unsafe)) { "Unsafe URL passed validation: $unsafe" } }
    }
}

val validateReleaseConfiguration = tasks.register("validateReleaseConfiguration") {
    group = "verification"
    description = "Rejects placeholder or non-HTTPS legal URLs before a release build."
    dependsOn(verifyUrlValidationPolicy)
    doLast {
        check(!requireReleaseSigning || releaseSigningConfigured) {
            "Approved release signing was required, but the upload-key configuration is incomplete."
        }
        if (releaseSigningConfigured) {
            check(file(uploadKeystorePath).isFile) {
                "OMAR_UPLOAD_KEYSTORE_PATH must identify an existing upload keystore."
            }
        }
        check(!apiConfigured || isPublicHttpsUrl(omarApiBaseUrl)) {
            "OMAR_API_BASE_URL must be a public HTTPS URL when configured for release builds."
        }
        check(!apiConfigured || listOf(firebaseApiKey, firebaseProjectId, firebaseAppId).all(String::isNotBlank)) {
            "Firebase authentication configuration is required when the Omar AI API is enabled."
        }
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
