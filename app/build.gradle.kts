import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.doptsw.dongdiary"
    compileSdk {
        version = release(36)
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties().apply {
                val localFile = rootProject.file("local.properties")
                if (localFile.exists()) {
                    localFile.inputStream().use { load(it) }
                }
            }

            fun getProp(key: String): String? {
                return (rootProject.findProperty(key) as? String) ?: localProperties.getProperty(key)
            }

            val storeFileProp = getProp("RELEASE_STORE_FILE")
            if (storeFileProp != null) {
                val keystoreFile = rootProject.file(storeFileProp)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = getProp("RELEASE_STORE_PASSWORD")
                    keyAlias = getProp("RELEASE_KEY_ALIAS")
                    keyPassword = getProp("RELEASE_KEY_PASSWORD")
                }
            }
        }
    }


    defaultConfig {
        applicationId = "com.doptsw.dongdiary"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "v1.0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"

            // 추가된 부분: INDEX.LIST 및 중복 파일 처리
            excludes += "META-INF/INDEX.LIST"
            // 또는 특정 파일이 꼭 필요하다면 pickFirst를 사용합니다.
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.foundation:foundation")

    // JSON 직렬화를 위한 kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 설정 저장을 위한 DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google 로그인
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Drive REST API & Auth
    implementation("com.google.auth:google-auth-library-oauth2-http:1.24.0")

    implementation("com.google.api-client:google-api-client:2.4.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    implementation("com.google.api-client:google-api-client-android:2.4.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")

    // Jetpack Navigation (Compose)
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Image picker and handling
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Coil for image rendering in Compose
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Exif support for reading image orientation
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
