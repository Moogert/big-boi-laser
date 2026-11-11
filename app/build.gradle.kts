plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.safelaser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.safelaser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-simulator"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "transport"
    productFlavors {
        create("simulator") {
            dimension = "transport"
            isDefault = true
            applicationIdSuffix = ".simulator"
            buildConfigField("String", "TRANSPORT_MODE", "\"SIMULATOR\"")
            buildConfigField("boolean", "REAL_BLE_ENABLED", "false")
        }
        create("hardwareValidation") {
            dimension = "transport"
            applicationIdSuffix = ".validation"
            buildConfigField("String", "TRANSPORT_MODE", "\"HARDWARE_VALIDATION\"")
            versionNameSuffix = "-hardware-validation"
            buildConfigField("boolean", "REAL_BLE_ENABLED", "true")
        }
        create("releaseGate") {
            dimension = "transport"
            applicationIdSuffix = ".blocked"
            buildConfigField("String", "TRANSPORT_MODE", "\"RELEASE_BLOCKED\"")
            buildConfigField("boolean", "REAL_BLE_ENABLED", "false")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
