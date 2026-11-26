import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun resolveExternalConfig(vararg keys: String): String? =
    keys.asSequence()
        .mapNotNull { key ->
            localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        }
        .firstOrNull()

// Derive versionCode from version.properties so it stays in sync with versionName.
val versionProps = Properties().apply {
    load(rootProject.file("version.properties").inputStream())
}
val appVersionMajor = versionProps.getProperty("VERSION_MAJOR").toInt()
val appVersionMinor = versionProps.getProperty("VERSION_MINOR").toInt()
val appVersionPatch = versionProps.getProperty("VERSION_PATCH").toInt()
require(appVersionMajor >= 0 && appVersionMinor in 0..99 && appVersionPatch in 0..99) {
    "Invalid version.properties ($appVersionMajor.$appVersionMinor.$appVersionPatch): minor and patch must be in 0..99."
}
val versionCodeOffset = (project.findProperty("versionCodeOffset") as String?)?.toIntOrNull() ?: 0
// 2.0.4 -> 20004. Room for MINOR/PATCH up to 99 each before a scheme change is needed.
val appVersionCode = versionCodeOffset + appVersionMajor * 10_000 + appVersionMinor * 100 + appVersionPatch
val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.convx.music"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.apprise.music"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = providers.gradleProperty("appriseTestRunner")
            .orElse(providers.gradleProperty("convxTestRunner"))
            .orElse("androidx.test.runner.AndroidJUnitRunner")
            .get()
        vectorDrawables.useSupportLibrary = true

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_static")
            }
        }

        // Product identity metadata exposed to diagnostics and about surfaces
        buildConfigField("String", "PRODUCT_NAME", "\"MUZIKQUE\"")
        buildConfigField("String", "DEVELOPER_NAME", "\"Apprise Apps\"")

        // LastFM API keys from local.properties or environment variables (never committed)
        val lastFmKey = resolveExternalConfig("LASTFM_API_KEY") ?: ""
        val lastFmSecret = resolveExternalConfig("LASTFM_SECRET") ?: ""

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastFmSecret\"")

        // Play Billing / AdMob / Privacy configuration sourced externally (never commit production secrets).
        val playLicensePublicKey = resolveExternalConfig("PLAY_LICENSE_PUBLIC_KEY") ?: ""
        buildConfigField("String", "PLAY_LICENSE_PUBLIC_KEY", "\"$playLicensePublicKey\"")
        val admobAppId = resolveExternalConfig("ADMOB_APP_ID")
            ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "TERMS_URL", "\"https://appriseapps.com/music/terms\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://appriseapps.com/music/privacy\"")

        val isNightly = project.hasProperty("nightly") && project.property("nightly") == "true"
        buildConfigField("Boolean", "IS_NIGHTLY", isNightly.toString())
        // CI stamps the workflow run number so a nightly build knows which one it is.
        // 0 for local/stable builds, which makes any published nightly look newer.
        val nightlyRun = (project.findProperty("nightlyRun") as String?)?.toIntOrNull() ?: 0
        buildConfigField("int", "NIGHTLY_RUN", nightlyRun.toString())
    }
    

    flavorDimensions += listOf("abi")
    productFlavors {
        create("universal") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
            // 64-bit only: MMKV (native KV store) ships no 32-bit binaries, so a
            // 32-bit slice would crash at load time. armeabi/x86 flavors removed.
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        create("arm64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
            ndk { abiFilters.add("arm64-v8a") }
        }
        // 32-bit-only ARM userland (e.g. Galaxy A13). MMKV 2.x has no 32-bit library,
        // so this flavor swaps in the 1.3.x line below.
        create("arm32") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"arm32\"")
            ndk { abiFilters.add("armeabi-v7a") }
        }
        create("x86_64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
            ndk { abiFilters.add("x86_64") }
        }
    }

    val releaseStorePath = resolveExternalConfig("RELEASE_STORE_FILE", "STORE_FILE")
    val releaseStoreFile = releaseStorePath?.let { file(it) } ?: file("keystore/release.keystore")
    val releaseStorePassword = resolveExternalConfig("RELEASE_STORE_PASSWORD", "STORE_PASSWORD")
    val releaseKeyAlias = resolveExternalConfig("RELEASE_KEY_ALIAS", "KEY_ALIAS")
    val releaseKeyPassword = resolveExternalConfig("RELEASE_KEY_PASSWORD", "KEY_PASSWORD")
    val hasReleaseSigning = releaseStoreFile.exists() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            // Signing material must come from external properties or CI secrets only.
            // Never commit keystores or hardcode fallback passwords in build files.
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
        getByName("debug") {
            // Optional external debug keystore override; otherwise AGP manages the default debug keystore.
            resolveExternalConfig("DEBUG_STORE_FILE")?.let { path ->
                val customDebugStore = file(path)
                if (customDebugStore.exists()) {
                    storeFile = customDebugStore
                    resolveExternalConfig("DEBUG_STORE_PASSWORD")?.let { storePassword = it }
                    resolveExternalConfig("DEBUG_KEY_ALIAS")?.let { keyAlias = it }
                    resolveExternalConfig("DEBUG_KEY_PASSWORD")?.let { keyPassword = it }
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            // Production builds must inject real AdMob unit IDs via local.properties or CI env.
            buildConfigField(
                "String",
                "ADMOB_BANNER_HOME_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_HOME_ID") ?: ""}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_LIBRARY_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_LIBRARY_ID") ?: ""}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_PLAYLIST_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_PLAYLIST_ID") ?: ""}\""
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // Debug defaults to Google's official sample adaptive/banner test ad unit ID.
            buildConfigField(
                "String",
                "ADMOB_BANNER_HOME_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_HOME_ID") ?: "ca-app-pub-3940256099942544/9214589741"}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_LIBRARY_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_LIBRARY_ID") ?: "ca-app-pub-3940256099942544/9214589741"}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_PLAYLIST_ID",
                "\"${resolveExternalConfig("ADMOB_BANNER_PLAYLIST_ID") ?: "ca-app-pub-3940256099942544/9214589741"}\""
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // JVM unit tests touch framework stubs (ColorUtils in tint math). Return
    // defaults instead of throwing — Robolectric would be heavier than these
    // token tests warrant.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/analyzer/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("compose_stability.conf")
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(false)
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    "universalImplementation"(libs.mmkv)
    "arm64Implementation"(libs.mmkv)
    "x86_64Implementation"(libs.mmkv)
    "arm32Implementation"(libs.mmkv.legacy)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)
    implementation(libs.exifinterface)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Apple-Music-style karaoke lyrics: per-syllable timing, background/accompaniment
    // lines, and left/right alignment for duets. Same renderer the reference app uses --
    // writing this by hand is ~1400 lines of text layout and interruptible scroll.
    implementation(libs.accompanist.lyrics.ui)
    implementation(libs.accompanist.lyrics.core)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    // FFmpeg audio decoder fallback for local files the platform MediaCodec can't
    // decode (e.g. ALAC — no AOSP device ships a decoder for it). Only engages when
    // the platform has no decoder for a format; every existing decode path is untouched.
    implementation(libs.nextlib.media3ext)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    // Vendored: JitPack serves an empty 295-byte jar for com.github.promeG:tinypinyin:2.0.3
    // (upstream build broke), so the real jar (compiled from v2.0.3 sources) + its
    // aho-corasick dep live in app/libs. Do not switch back to libs.tinypinyin.
    implementation(files("libs/tinypinyin-2.0.3.jar", "libs/ahocorasick-0.3.0.jar"))
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":lyrics"))
    implementation(project(":artwork"))
    implementation(project(":network-storage"))
    implementation(project(":hig"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)

    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.timber)
    implementation(libs.smoothCorner)
    implementation(libs.androidx.material.icons.extended.v178)
    implementation(libs.androidx.core.splashscreen)


    // Smart analysis ONNX models (Beat This + Open-Unmix vocal separator)
    implementation(libs.onnxruntime)

    // M4 Commercial Monetization: Google Play Billing, Mobile Ads SDK, and UMP Consent
    implementation(libs.play.billing)
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
    testImplementation("org.json:json:20251224")
}



/**
 * `./gradlew bumpVersion -Ppart=patch|minor|major` — the whole release-versioning
 * mechanism. Increments the requested part of version.properties (patch/minor reset
 * the parts below them to 0, same as semver), leaves everything else untouched, and
 * prints the tag to push. Nothing auto-commits or auto-tags: git stays a manual,
 * reviewable step.
 *
 * `part` defaults to "patch" since that's what almost every release here has been —
 * bugfix passes, not new features.
 */
tasks.register("bumpVersion") {
    group = "versioning"
    description = "Bumps version.properties (patch/minor/major) — the single source for versionCode/versionName."
    doLast {
        val part = (project.findProperty("part") as String?)?.lowercase() ?: "patch"
        var major = appVersionMajor
        var minor = appVersionMinor
        var patch = appVersionPatch
        when (part) {
            "major" -> { major += 1; minor = 0; patch = 0 }
            "minor" -> { minor += 1; patch = 0 }
            "patch" -> { patch += 1 }
            else -> throw GradleException("Unknown part '$part' — use major, minor, or patch.")
        }

        val versionFile = rootProject.file("version.properties")
        versionFile.writeText(
            "VERSION_MAJOR=$major\nVERSION_MINOR=$minor\nVERSION_PATCH=$patch\n"
        )

        val newVersionName = "$major.$minor.$patch"
        val newVersionCode = major * 10_000 + minor * 100 + patch
        println("Bumped $part: $appVersionName ($appVersionCode) -> $newVersionName ($newVersionCode)")
        println()
        println("Next steps:")
        println("  git add version.properties")
        println("  git commit -m \"chore: bump version to $newVersionName\"")
        println("  git tag v$newVersionName")
        println("  git push && git push --tags   # tag push triggers release.yml")
    }
}
