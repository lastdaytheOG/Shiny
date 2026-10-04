plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.shiny.music.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Baseline Profile collection needs API 28+ (33+ on a device without root).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Mirrors :app, so every app variant has a matching benchmark variant to run against.
    flavorDimensions += listOf("abi", "variant")
    productFlavors {
        create("foss") { dimension = "variant" }
        create("gms") { dimension = "variant" }
        listOf("universal", "arm64", "armeabi", "x86", "x86_64").forEach { abi ->
            create(abi) { dimension = "abi" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

baselineProfile {
    // Runs on whatever device adb has attached rather than a Gradle-managed emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// Benchmarks run against one device at a time, so only one abi/variant pair is built and run:
// -Pshiny.benchmark.abi (default x86_64, the emulator) and -Pshiny.benchmark.variant (default gms,
// the flavor CI ships). Without this, :app:generateBaselineProfile builds and runs every flavor.
val benchmarkAbi = providers.gradleProperty("shiny.benchmark.abi").getOrElse("x86_64")
val benchmarkVariant = providers.gradleProperty("shiny.benchmark.variant").getOrElse("gms")

androidComponents {
    beforeVariants { variant ->
        variant.enable = ("abi" to benchmarkAbi) in variant.productFlavors &&
            ("variant" to benchmarkVariant) in variant.productFlavors
    }
    onVariants { variant ->
        // The tests address the app by the id of the APK actually under test.
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId ?: "" },
        )
    }
}
