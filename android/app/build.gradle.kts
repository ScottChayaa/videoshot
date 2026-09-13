plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xenyaa.videoshot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.xenyaa.videoshot"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric 要讀 app 的資源（主題、字串），沒有這一行 Compose 起不來
            isIncludeAndroidResources = true
        }
    }
}

/**
 * 單元測試（Robolectric）跑在 JDK 21，不跟著 daemon 的 JDK 25 走。
 * Robolectric 的 FileDescriptor 攔截器在 JDK 25 上會丟
 * `Failed to interact with raw FileDescriptor internals`；官方支援到 JDK 21；而 Robolectric 模擬 Android SDK 37 又要求至少 Java 21，所以只有 21 這個選擇。
 * JDK 由 settings.gradle.kts 的 foojay resolver 自動下載。
 */
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // collectAsStateWithLifecycle 在 runtime-compose，不在 viewmodel-compose —— 兩個都要
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    // Compose UI 測試改跑在 JVM（Robolectric）—— 這台實機的儀器化 UI 測試會無限卡住，
    // 最小的 Text("嗨") 測試也一樣，對照組的非 UI 儀器測試則正常。
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}