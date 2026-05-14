import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.scanpang.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scanpang.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val kakaoKey = localProperties.getProperty("KAKAO_REST_API_KEY") ?: ""
        val tmapKey = localProperties.getProperty("TMAP_APP_KEY") ?: ""
        val googleMapsKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""
        val serverUrl = localProperties.getProperty("SERVER_URL") ?: "http://localhost:8000/"
        val supabaseUrl = localProperties.getProperty("SUPABASE_URL") ?: ""
        val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""
        // OAuth redirect deep link — Supabase 콘솔의 Authentication > URL Configuration > Redirect URLs 에 동일 값 등록 필요.
        // 형식: <scheme>://<host> (path 없음). applicationId 와 분리해 brand 패키지 형태로 짧게 잡는다.
        val oauthRedirectScheme = "com.scanpang.app"
        val oauthRedirectHost = "login-callback"

        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "TMAP_APP_KEY", "\"$tmapKey\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsKey\"")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "OAUTH_REDIRECT_SCHEME", "\"$oauthRedirectScheme\"")
        buildConfigField("String", "OAUTH_REDIRECT_HOST", "\"$oauthRedirectHost\"")

        manifestPlaceholders["googleMapsApiKey"] = googleMapsKey
        manifestPlaceholders["oauthRedirectScheme"] = oauthRedirectScheme
        manifestPlaceholders["oauthRedirectHost"] = oauthRedirectHost
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

// Kotlin 2.0+ 의 새 DSL. 이전 `kotlinOptions { jvmTarget = "17" }` 는 deprecated → error.
// android {} 블록 밖에서 kotlin {} 블록으로 분리해 설정한다.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ARCore + SceneView (AR Navigation + AR Explore)
    implementation("io.github.sceneview:arsceneview:2.3.3")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.ar:core:1.53.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.4.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Network (Backend API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Supabase Auth (OAuth + session/JWT 관리) — supabase-kt 공식 Kotlin SDK.
    // BOM 이 모든 supabase 모듈 버전을 정렬. supabase-kt 3.x 는 ktor 3.x 를 요구한다.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.0")
    // 토큰을 EncryptedSharedPreferences 로 보관 (평문 SharedPrefs 대비 보안 강화)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
