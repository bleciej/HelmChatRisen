plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

/** Version name shared by the artifact names and the in-app Application info dialog. */
val appVersionName = "1.5"

/** Version code for the 1.5 release line. */
val appVersionCode = 5

/** Release tag published for this build (kept in sync with RELEASE_NOTES.md). */
val appReleaseTag = "V$appVersionName"

/** Release date published for this build (kept in sync with RELEASE_NOTES.md). */
val appReleaseDate = "2026-11-17"

/** Minimum supported API level, also shown in the Application info dialog. */
val appMinSdk = 26

/** API level the app targets and is optimized for. */
val appTargetSdk = 37

/** SDK level used to compile the app. */
val appCompileSdk = 37

/** Human-readable Android version for [appMinSdk]. */
val minAndroidDisplay = "8.0 (API $appMinSdk)"

/** Collapses to one number when target and compile levels match, otherwise shows both. */
val sdkDisplay = if (appTargetSdk == appCompileSdk) {
    appTargetSdk.toString()
} else {
    "$appTargetSdk / $appCompileSdk"
}

/**
 * Packing an Android App Bundle needs a single shrunk-resources output, so the per-ABI APK splits
 * must be switched off whenever a `bundle*` task is requested; otherwise the bundle packaging
 * fails with "Multiple shrunk-resources files found". APK-only builds keep the splits, so they
 * still produce one APK per ABI plus a universal APK.
 *
 * See https://issuetracker.google.com/402800800.
 */
val isBundleBuild = gradle.startParameter.taskNames.any { requestedTask ->
    requestedTask.substringAfterLast(':').startsWith("bundle", ignoreCase = true)
}

android {
    namespace = "dev.communicator.helmchatrisen"
    compileSdk {
        version = release(appCompileSdk)
    }

    defaultConfig {
        applicationId = "dev.communicator.helmchatrisen"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = appVersionCode
        versionName = appVersionName

        // Release metadata exposed to the in-app Application info dialog so its values
        // always come from the same source as the published artifacts.
        buildConfigField("String", "RELEASE_TAG", "\"$appReleaseTag\"")
        buildConfigField("String", "RELEASE_DATE", "\"$appReleaseDate\"")
        buildConfigField("int", "MIN_SDK", "$appMinSdk")
        buildConfigField("int", "TARGET_SDK", "$appTargetSdk")
        buildConfigField("int", "COMPILE_SDK", "$appCompileSdk")
        buildConfigField("String", "MIN_ANDROID", "\"$minAndroidDisplay\"")
        buildConfigField("String", "SDK_LEVELS", "\"$sdkDisplay\"")

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
    // Per-ABI release APKs for the two supported ABIs, plus a universal APK. Disabled for app
    // bundle builds (see isBundleBuild above), which need a single, universal output.
    splits {
        abi {
            isEnable = !isBundleBuild
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
 * patterns are replaced. The patterns match the surrounding structure instead of minified
 * identifier names, so an SDK upgrade only needs a new pattern when the JS logic itself changes.
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
                // Entries hold (unpatched pattern, already-patched pattern, replacement). The
                // second pattern keeps repeated builds idempotent after the merged asset is reused.
                val replacements: List<Triple<Regex, Regex, (MatchResult) -> String>> = listOf(
                    Triple(
                        Regex("_timeoutSeconds=\\d+\\+\\(0,\\w+\\(\\w+\\[\\d+\\]\\)\\.randomInt\\)\\(0,\\d+\\)"),
                        Regex("_timeoutSeconds=5"),
                    ) { "_timeoutSeconds=5" },
                    Triple(
                        Regex("(\\w+\\(\\{reconnecting:!0,pcId:\\w+\\}\\)\\}\\),)3e4"),
                        Regex("(\\w+\\(\\{reconnecting:!0,pcId:\\w+\\}\\)\\}\\),)5e3"),
                    ) { match -> "${match.groupValues[1]}5e3" },
                )
                var patched = text
                replacements.forEach { (pattern, patchedPattern, replacement) ->
                    if (pattern.containsMatchIn(patched)) {
                        patched = pattern.replace(patched, replacement)
                    } else if (!patchedPattern.containsMatchIn(patched)) {
                        throw GradleException(
                            "Jitsi reconnect patch target not found in ${bundleFile.name}: ${pattern.pattern}. " +
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