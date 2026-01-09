plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.roadsensorrecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.roadsensorrecorder"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core & compatibility
    implementation("androidx.core:core:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity:1.12.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Play Services Location for FusedLocationProviderClient
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Lifecycle service for LifecycleService base class
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}