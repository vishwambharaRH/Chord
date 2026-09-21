plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Personal sideloaded build: the data-repo token comes from the repo's
// git-ignored .env so it never has to be typed on the phone (or committed).
val dotenv: Map<String, String> = rootProject.projectDir.parentFile.resolve(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.filter { "=" in it && !it.trimStart().startsWith("#") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"') }
    ?: emptyMap()

android {
    namespace = "com.chordtracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chordtracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GITHUB_TOKEN", "\"${dotenv["CHORD_DATA_REPO_PAT"] ?: ""}\"")
        buildConfigField("String", "DATA_REPO", "\"${dotenv["CHORD_DATA_REPO"] ?: "vishwambharaRH/chord-data"}\"")
    }

    // The dashboard is the same web/ site used on the laptop; data.json is
    // excluded because the app downloads a fresh copy at runtime.
    sourceSets["main"].assets.srcDir(rootProject.projectDir.parentFile.resolve("web"))
    androidResources.ignoreAssetsPattern = "data.json"

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
