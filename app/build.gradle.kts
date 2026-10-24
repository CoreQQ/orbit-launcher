plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.orbit.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.orbit.launcher"
        minSdk = 24
        targetSdk = 34
        // CI передаёт номер сборки, чтобы каждая новая APK ставилась поверх старой
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    signingConfigs {
        create("orbit") {
            // Один и тот же ключ для debug/release → обновления встают поверх без удаления.
            // Для своего ключа задай ORBIT_KEYSTORE(+пароли) в env / GitHub Secrets.
            val ks = System.getenv("ORBIT_KEYSTORE")
            storeFile = if (ks.isNullOrBlank()) rootProject.file("keystore/orbit.jks") else file(ks)
            storePassword = System.getenv("ORBIT_KEYSTORE_PASSWORD") ?: "orbitlauncher"
            keyAlias = System.getenv("ORBIT_KEY_ALIAS") ?: "orbit"
            keyPassword = System.getenv("ORBIT_KEY_PASSWORD") ?: "orbitlauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("orbit")
        }
        debug {
            signingConfig = signingConfigs.getByName("orbit")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
