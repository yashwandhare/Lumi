plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lumi"
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.lumi"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The LiteRT-LM native runtime ships arm64 only. Restricting ABIs keeps the
        // APK from carrying stubs it can never load.
        ndk { abiFilters.add("arm64-v8a") }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 rules live in proguard-rules.pro. The native AI layer is JNI-reached
            // and must be kept explicitly once Phase 1 wires it in.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room's MigrationTestHelper loads the exported schema JSON from the androidTest assets, not from
    // app/schemas/. Without this the migration test cannot see 1.json and fails with a
    // FileNotFoundException that looks like a broken migration rather than a missing wiring.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    testOptions {
        // Pure-JVM tests that touch android.util.Log get no-op defaults instead of
        // "not mocked" failures.
        unitTests.isReturnDefaultValues = true
    }
}

// Room writes its schema JSON here on every build. These files are committed —
// they are the record every future migration is validated against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    constraints {
        // Room 2.8.4's migration bundle is compiled against kotlinx-serialization 1.8.1, but
        // lifecycle-viewmodel-savedstate drags in 1.7.3 and AGP's consistent resolution pins the
        // androidTest classpath to whatever the main one resolved. The mismatch is invisible at compile
        // time and surfaces only when MigrationTestHelper deserialises a schema, as an
        // AbstractMethodError on a generated serializer — which reads as a broken migration rather than
        // a dependency conflict. A constraint raises the version without adding the dependency.
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    }

    // Streaming ASR runtime. Fetched by tools/fetch-sherpa.sh into libs/sherpa/, pinned there by
    // size and SHA-256, and never committed — a 49MB binary, kept out of git like the models.
    // Checked at configuration time so a missing artefact fails with instructions, not with a
    // cascade of unresolved-symbol errors at the end of a long compile.
    val sherpaAar = rootProject.file("libs/sherpa/sherpa-onnx-1.13.5.aar")
    require(sherpaAar.isFile) {
        "Missing ${sherpaAar.path}. Run tools/fetch-sherpa.sh to fetch the pinned sherpa-onnx AAR."
    }
    implementation(files(sherpaAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui.material3)

    implementation(libs.mediapipe.tasks.text)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background execution for reminders: exact alarms carry the firing, and a periodic worker
    // sweeps for anything missed (reboot edge cases, Doze). Must fire with the app closed.
    implementation(libs.androidx.work.runtime)

    // The home-screen widget, Phase 3's headline surface. Owner-approved dependency.
    implementation(libs.androidx.glance.appwidget)

    // On-device inference. Pinned in the version catalogue, never latest.release — see decisions.md.
    implementation(libs.litertlm)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
