plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.magicgesture.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.magicgesture.app"; minSdk = 26; targetSdk = 36; versionCode = 9; versionName = "0.9.0-beta" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies { implementation("com.google.mediapipe:tasks-vision:0.10.21") }
