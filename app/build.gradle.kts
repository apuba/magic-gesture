import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) signingPropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.magicgesture.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.magicgesture.app"; minSdk = 26; targetSdk = 36; versionCode = 10; versionName = "1.0.0" }
    // BuildConfig.DEBUG is used to keep the internal "unlock everything" test switch out of release.
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (signingPropertiesFile.exists()) create("release") {
            storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingPropertiesFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    // MediaPipe tasks-vision 0.10.21 only ships native libraries for arm64-v8a / armeabi-v7a / x86.
    // Google Play requires a 64-bit counterpart for every 32-bit ABI we ship, and no x86_64 build
    // exists in this dependency, so the 32-bit x86 slice is dropped instead of being paired.
    packaging {
        jniLibs {
            excludes += setOf("**/x86/**")
        }
    }
}

dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
    // Reads the orientation tag of captured JPEGs so selfies are stored upright on every device.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}
