plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

val appVersionName = "1.4"

android {
    namespace = "com.example.helmChatRisen"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.helmChatRisen"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // Release signing is supplied out-of-band and is never committed.
        // Provide via Gradle properties (e.g. ~/.gradle/gradle.properties) or -P:
        //   releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
        val signingPropertyNames = listOf(
            "releaseStoreFile",
            "releaseStorePassword",
            "releaseKeyAlias",
            "releaseKeyPassword",
        )
        val signingValues = signingPropertyNames.associateWith { name ->
            project.findProperty(name) as String?
        }
        if (signingValues.values.any { !it.isNullOrBlank() }) {
            val missing = signingValues.filterValues { it.isNullOrBlank() }.keys
            check(missing.isEmpty()) {
                "Incomplete release signing configuration; missing: ${missing.joinToString()}"
            }
            create("release") {
                storeFile = file(signingValues.getValue("releaseStoreFile")!!)
                storePassword = signingValues.getValue("releaseStorePassword")
                keyAlias = signingValues.getValue("releaseKeyAlias")
                keyPassword = signingValues.getValue("releaseKeyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // Always produce per-ABI release APKs for the two supported ABIs, plus a universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

base {
    archivesName.set("HelmChatRisen-$appVersionName")
}

/**
 * Jitsi's bundled JS hard-codes a 10–30 s conference reload delay and a 30 s websocket retry.
 * Both make a transient motorcycle-network drop feel like a dropped call. Patch the merged
 * asset in place so the SDK reloads the conference after ~5 s instead.
 *
 * The bundle is treated as ISO-8859-1 so every byte round-trips unchanged while the ASCII
 * patterns are replaced.
 */
androidComponents {
    onVariants { variant ->
        // Give release APKs the requested names, including the ABI for split outputs.
        if (variant.name == "release") {
            variant.outputs.forEach { output ->
                val abi = output.filters.firstOrNull {
                    it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }?.identifier
                val abiSuffix = if (abi != null) "-$abi" else ""
                @Suppress("UnstableApiUsage")
                output.outputFileName.set(
                    "HelmChatRisen-$appVersionName-release$abiSuffix.apk",
                )
            }
        }
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val mergedBundle = layout.buildDirectory
            .file("intermediates/assets/${variant.name}/merge${variantName}Assets/index.android.bundle")
        val patchTask = tasks.register("patch${variantName}JitsiReconnect") {
            description = "Shortens Jitsi's conference/websocket reconnect delays."
            group = "build"
            dependsOn("merge${variantName}Assets")
            val bundleFile = mergedBundle.get().asFile
            inputs.file(bundleFile)
            doLast {
                if (!bundleFile.exists()) {
                    logger.warn("Jitsi bundle not found at ${bundleFile.path}; skipping reconnect patch")
                    return@doLast
                }
                val text = bundleFile.readText(Charsets.ISO_8859_1)
                val replacements = mapOf(
                    "_timeoutSeconds=10+(0,_r(d[10]).randomInt)(0,20)" to "_timeoutSeconds=5",
                    "F({reconnecting:!0,pcId:h})}),3e4)" to "F({reconnecting:!0,pcId:h})}),5e3)",
                )
                var patched = text
                replacements.forEach { (from, to) ->
                    if (patched.contains(from)) {
                        patched = patched.replace(from, to)
                    } else if (!patched.contains(to)) {
                        throw GradleException(
                            "Jitsi reconnect patch target not found in ${bundleFile.name}: $from. " +
                                "Update app/build.gradle.kts for the installed Jitsi SDK version.",
                        )
                    }
                }
                if (patched != text) {
                    bundleFile.writeText(patched, Charsets.ISO_8859_1)
                    logger.lifecycle("Patched Jitsi reconnect delays in ${bundleFile.name}")
                }
            }
        }
        tasks.configureEach {
            if (name == "compress${variantName}Assets") {
                dependsOn(patchTask)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.jitsi.meet.sdk)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
}