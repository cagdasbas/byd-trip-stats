import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.byd.tripstats"
    compileSdk = 34

    // Read keystore details from local.properties (gitignored — never commit these)
    val localProps = Properties().also { props ->
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
    }

    signingConfigs {
        create("release") {
            storeFile = (System.getenv("KEYSTORE_PATH") 
                ?: localProps.getProperty("storeFile"))
                ?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") 
                ?: localProps.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") 
                ?: localProps.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") 
                ?: localProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.byd.tripstats"
        minSdk = 29
        targetSdk = 29
        versionCode = 14
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Run instrumented tests as a separate package with its own isolated data
        // directory (/data/data/com.byd.tripstats.test/). Test code cannot touch
        // the real app's DB at /data/data/com.byd.tripstats/ even if reflection fails.
        testApplicationId = "com.byd.tripstats.test"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "SENSITIVE_DIAGNOSTICS_ENABLED", "true")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "SENSITIVE_DIAGNOSTICS_ENABLED", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    // Custom APK naming: byd-trip-stats-VERSION.apk
    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "byd-trip-stats-${versionName}-${name}.apk"
            }
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
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    // Use `adb install -r` (replace, keep data) instead of uninstall+install.
    // Must be a top-level android {} block — placing this inside defaultConfig {} is
    // invalid DSL and silently ignored, which is why the DB was still getting wiped.
    installation {
        installOptions.add("-r")
    }

    testOptions {
        animationsDisabled = true
    }
}

// Keep app/src/main/assets/changelog.md in sync with the root CHANGELOG.md at every build
tasks.register<Copy>("syncChangelog") {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "changelog.md" }
}
tasks.named("preBuild") { dependsOn("syncChangelog") }

dependencies {
    // Core Android
    implementation(kotlin("stdlib"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hivemq.mqtt.client)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // osmdroid for offline maps
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // dadb — JVM ADB client for wireless ADB self-permission setup
    implementation("dev.mobile:dadb:1.2.7")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Window size classes for responsive design
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")

    rootProject.findProject(":private-telemetry")?.let {
        implementation(it)
    }

    // ── Unit tests (src/test) ─────────────────────────────────────────────────
    // JUnit 4 is already present via libs.junit — no change needed there.

    // Coroutines test support — runTest, advanceUntilIdle, advanceTimeBy
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Turbine — concise Flow assertion helper (optional but recommended)
    testImplementation("app.cash.turbine:turbine:1.1.0")

    // ── Instrumented tests (src/androidTest) ─────────────────────────────────
    // Room in-memory database support — required for repository integration tests
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // Coroutines test support in androidTest
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // AndroidX test core — ApplicationProvider, etc.
    androidTestImplementation("androidx.test:core-ktx:1.5.0")

    // AndroidX test runner (if not already added via BOM)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}