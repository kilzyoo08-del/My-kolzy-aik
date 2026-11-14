plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    val videoBackendUrl = providers.gradleProperty("VIDEO_BACKEND_URL").orNull ?: ""
    val centerionSupabaseUrl = providers.gradleProperty("CENTERION_SUPABASE_URL")
        .orElse(providers.environmentVariable("CENTERION_SUPABASE_URL"))
        .getOrElse("https://tdqyhptkrmxoohfpfwvs.supabase.co")
    val centerionPublishableKey = providers.gradleProperty("CENTERION_SUPABASE_PUBLISHABLE_KEY")
        .orElse(providers.environmentVariable("CENTERION_SUPABASE_PUBLISHABLE_KEY"))
        .getOrElse("")
    fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    namespace = "com.mykolzyai.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mykolzyai.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "VIDEO_BACKEND_URL", "\"$videoBackendUrl\"")
        buildConfigField("String", "CENTERION_SUPABASE_URL", buildConfigString(centerionSupabaseUrl))
        buildConfigField("String", "CENTERION_SUPABASE_PUBLISHABLE_KEY", buildConfigString(centerionPublishableKey))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
