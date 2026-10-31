plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val qlotsApiUrl = providers.gradleProperty("qlotsApiBaseUrl")
    .orElse(providers.environmentVariable("QLOTS_API_URL"))
    .orNull
    ?.trimEnd('/')
    ?: error("qlotsApiBaseUrl must be set in gradle.properties or via -PqlotsApiBaseUrl or QLOTS_API_URL env var")
val uploadStoreFile = providers.environmentVariable("QLOTS_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("QLOTS_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("QLOTS_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("QLOTS_UPLOAD_KEY_PASSWORD").orNull

android {
    namespace = "com.qlots.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qlots.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "API_BASE_URL", "\"${qlotsApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    signingConfigs {
        create("release") {
            if (!uploadStoreFile.isNullOrBlank()) {
                require(!uploadStorePassword.isNullOrBlank() && !uploadKeyAlias.isNullOrBlank() && !uploadKeyPassword.isNullOrBlank()) {
                    "Set all QLOTS_UPLOAD_* signing environment variables together."
                }
                storeFile = file(uploadStoreFile)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            if (!uploadStoreFile.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.github.chuckerteam.chucker:library:4.1.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
