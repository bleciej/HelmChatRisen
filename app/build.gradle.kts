plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "com.example.helmchatreborn"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.helmchatreborn"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
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
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val mergedBundle = layout.buildDirectory
            .file("intermediates/assets/${variant.name}/merge${variantName}Assets/index.android.bundle")
        val patchTask = tasks.register("patch${variantName}JitsiReconnect") {
            description = "Shortens Jitsi's conference/websocket reconnect delays."
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
                    "e({reconnecting:!0,pcId:p})}),3e4)" to "e({reconnecting:!0,pcId:p})}),5e3)",
                )
                var patched = text
                replacements.forEach { (from, to) ->
                    if (patched.contains(from)) {
                        patched = patched.replace(from, to)
                    } else if (!patched.contains(to)) {
                        throw GradleException(
                            "Jitsi reconnect patch target not found in ${bundleFile.name}: $from. " +
                                "Update app/build.gradle.kts for the installed Jitsi SDK version."
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
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
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
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.jitsi.meet.sdk)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}