plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-kapt")
}

android {
    namespace   = "com.aamer.resourcemonitor"
    compileSdk  = 34

    defaultConfig {
        applicationId  = "com.aamer.resourcemonitor"
        minSdk         = 26
        targetSdk      = 34
        versionCode    = 1
        versionName    = "1.0.0"

        // Default server connection — change these to match your Oracle server
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://192.168.1.100:8080\"")
        buildConfigField("String", "DEFAULT_API_KEY",    "\"8cadaa7f5465e3ad7fcbfb9ca751a005c5f2ef3c3b35e9e4\"")
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose      = true
        buildConfig  = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.core)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
