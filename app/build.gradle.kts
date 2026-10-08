import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val hasGoogleServicesConfig = file("google-services.json").exists()

// The commit this APK is built from, and whether the working tree held anything git does not
// (edits or untracked files). Every APK names the exact source it corresponds to: GPL-3.0
// section 6 asks for that source, and a build from a dirty tree matches no commit.
// See LICENSE_COMPLIANCE.md, "Exact APK/source matching".
fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()
val gitCommit: String = gitOutput("rev-parse", "HEAD")?.takeIf { it.matches(Regex("[0-9a-f]{40}")) } ?: "unknown"
val gitTreeDirty: Boolean = gitOutput("status", "--porcelain")?.isNotEmpty() ?: true

// The public source location (gradle.properties `shiny.sourceCodeUrl`). Anything that is not an
// https:// URL counts as unset, and the app then says the link is not published yet.
val sourceCodeUrl: String = (project.findProperty("shiny.sourceCodeUrl") as String?)
    .orEmpty().trim().takeIf { it.startsWith("https://") }.orEmpty()

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.shiny.music"
    compileSdk = 36
    ndkVersion = "27.0.12077973"


    defaultConfig {
        applicationId = "com.shiny.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 154
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // SHINY's own Discord application: public client, PKCE, no secret.
        val discordApplicationId = "1549351860807270513"
        val discordApplicationIdLong = 1549351860807270513L
        val discordRedirectScheme = "discord-$discordApplicationId"

        buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
        buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationIdLong}L")
        buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")
        manifestPlaceholders["discordRedirectScheme"] = discordRedirectScheme

        // Shiny Together's room server (server/together). Set `shiny.together.url` in
        // gradle.properties once it is deployed; users can also override it in Settings.
        val togetherUrl = (project.findProperty("shiny.together.url") as String?).orEmpty().trimEnd('/')
        buildConfigField("String", "TOGETHER_SERVER_URL", "\"$togetherUrl\"")

        // Shown in Settings > About, so a user can find the source of exactly this build.
        buildConfigField("String", "SOURCE_CODE_URL", "\"$sourceCodeUrl\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("boolean", "GIT_TREE_DIRTY", "$gitTreeDirty")
    }


    flavorDimensions += listOf("abi", "variant")
    productFlavors {
        // FOSS variant (default) - F-Droid compatible, no Google Play Services
        create("foss") {
            dimension = "variant"
            isDefault = true
        }

        // GMS variant - Google sign-in, Firebase and Cronet (requires Google Play Services)
        create("gms") {
            dimension = "variant"
        }

        create("universal") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
            ndk { abiFilters.add("arm64-v8a") }
        }
        create("armeabi") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
            ndk { abiFilters.add("armeabi-v7a") }
        }
        create("x86") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
            ndk { abiFilters.add("x86") }
        }
        create("x86_64") {
            dimension = "abi"
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
            ndk { abiFilters.add("x86_64") }
        }
    }

    signingConfigs {
        // Shiny's own release key. CI decodes it from the SHINY_KEYSTORE_BASE64 secret; locally, set
        // SHINY_KEYSTORE_PATH and SHINY_KEYSTORE_PASSWORD. Without them, release builds stop at signing.
        create("release") {
            System.getenv("SHINY_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { storeFile = file(it) }
            storePassword = System.getenv("SHINY_KEYSTORE_PASSWORD")
            keyAlias = "shiny-release"
            // PKCS12 keystores have one password for the store and the key.
            keyPassword = System.getenv("SHINY_KEYSTORE_PASSWORD")
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ARCHITECTURE", "\"release\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "ARCHITECTURE", "\"debug\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
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
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

baselineProfile {
    // One profile for every flavor, kept in src/main/generated/baselineProfiles. It is produced
    // on purpose with a generate…BaselineProfile task, never as a side effect of a release build.
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

// benchmarkRelease and nonMinifiedRelease are created by the Baseline Profile plugin as copies of
// release. Release is signed with a keystore only CI has, so these two measurement-only build
// types are debug-signed; release itself is unchanged.
// `-Pshiny.measure.suffix=.debug` installs them under that suffix instead of over the real app:
// release-speed code on a phone whose com.shiny.music must not be replaced (debuggable builds
// run largely interpreted, which inflates every UI-thread measurement).
val measureSuffix = providers.gradleProperty("shiny.measure.suffix").orNull
androidComponents {
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
        variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
        measureSuffix?.let { variant.applicationId.set("com.shiny.music$it") }
    }
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
        measureSuffix?.let { variant.applicationId.set("com.shiny.music$it") }
    }
}

// Benchmark builds must never upload their R8 mappings to the production Crashlytics project.
tasks.configureEach {
    if (name.startsWith("uploadCrashlyticsMappingFile") &&
        (name.endsWith("BenchmarkRelease") || name.endsWith("NonMinifiedRelease"))
    ) {
        enabled = false
    }
}

ksp {
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(false)
    }
}

// The licence files travel inside every APK: the repository's own LICENSE, NOTICE and the licence
// inventory in licenses/ (bundled.json, dependencies.json, the licence texts and the verbatim
// third-party notices) are copied into the assets at build time, so each copy of the app carries
// the notices its components require. The app shows none of it; About links to the repository.
// overrides.json is input to the inventory script only, so it stays out of the APK.
abstract class CopyLegalDocs : DefaultTask() {
    @get:InputFile
    abstract val license: RegularFileProperty

    @get:InputFile
    abstract val notice: RegularFileProperty

    @get:InputDirectory
    abstract val licenses: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val legal = outputDir.get().asFile.resolve("legal")
        legal.deleteRecursively()
        legal.mkdirs()
        license.get().asFile.copyTo(legal.resolve("LICENSE"), overwrite = true)
        notice.get().asFile.copyTo(legal.resolve("NOTICE"), overwrite = true)
        licenses.get().asFile.copyRecursively(legal.resolve("licenses"), overwrite = true)
        legal.resolve("licenses/overrides.json").delete()
    }
}

val copyLegalDocs = tasks.register<CopyLegalDocs>("copyLegalDocs") {
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
    notice.set(rootProject.layout.projectDirectory.file("NOTICE"))
    licenses.set(rootProject.layout.projectDirectory.dir("licenses"))
}

// Run before an APK is published (LICENSE_COMPLIANCE.md, "Source-code distribution"): the build
// must name a public source URL and come from a committed tree, so the published commit is exactly
// the source of the APK. It checks only; it never builds, tags or publishes anything.
tasks.register("verifyReleaseSource") {
    val url = sourceCodeUrl
    val commit = gitCommit
    val dirty = gitTreeDirty
    doLast {
        val problems = buildList {
            if (url.isEmpty()) add("shiny.sourceCodeUrl in gradle.properties is not a public https:// URL")
            if (commit == "unknown") add("the git commit could not be read")
            if (dirty) add("the working tree has uncommitted or untracked changes, so no commit matches this build")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Not ready to publish an APK:\n - " + problems.joinToString("\n - "))
        }
        logger.lifecycle("Source for this build: $url at commit $commit")
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLegalDocs, CopyLegalDocs::outputDir)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":playback"))



    // Firebase - GMS flavor only (excluded from F-Droid / FOSS builds)
    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.analytics)
    "gmsImplementation"(libs.firebase.crashlytics)

    // Sign in with Google for Shiny social (social/GoogleSignIn.kt in the gms source set)
    "gmsImplementation"(libs.credentials)
    "gmsImplementation"(libs.credentials.play.services.auth)
    "gmsImplementation"(libs.googleid)


    implementation(libs.guava)
    implementation(libs.coroutines.guava)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.material3)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)

    // HTTP/3 (QUIC) for stream traffic, through the Cronet that Play Services ships
    // (playback/CronetTransport.kt in the gms source set). FOSS builds stay on OkHttp.
    "gmsImplementation"(libs.media3.cronet)
    "gmsImplementation"(libs.play.services.cronet)

    implementation(libs.room.runtime)

    implementation(libs.room.ktx)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":lyrics"))
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":youlyplus"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    implementation(project(":artistvideo"))
    implementation(project(":applecanvas"))
    implementation(project(":paxsenixlyrics"))
    implementation(project(":unison"))


    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // QR codes for Listen Together invites
    implementation(libs.zxing.core)

    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
    implementation(libs.timber)
    implementation(libs.smoothCorner)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.ffmpeg.kit.audio)

    // Installs the bundled baseline profile on sideloaded installs; Play installs it itself.
    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

}
