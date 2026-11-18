import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: put keystore.properties next to this project's settings.gradle.kts
// (never commit it):
//   storeFile=/path/to/harfix.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "uz.harfix.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.harfix.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.5.0"
    }

    // direct: the APK on harfix.uz and Telegram. No accessibility service, so Google Play Protect
    //         does not block it when it is installed from a file.
    // play:   the Google Play bundle. Adds "Odatiy klaviaturada" (WordsService + WordsTile,
    //         declared in src/play/AndroidManifest.xml).
    flavorDimensions += "store"
    productFlavors {
        create("direct") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // The test build installs next to the real app (its own id and name).
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Harfix (test)")
        }
        release {
            resValue("string", "app_name", "Harfix")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

// Without keystore.properties AGP quietly makes an unsigned release APK/AAB, which Google Play
// rejects; stop at the start instead. Android Studio's "Generate Signed Bundle / APK" passes its
// own key (android.injected.signing.*), so it still works without the file.
gradle.taskGraph.whenReady {
    val packaging = Regex("""^(assemble|bundle|package)\w*Release(Bundle)?$""")
    val wantsRelease = allTasks.any { it.project == project && packaging.matches(it.name) }
    val hasKey = signing.getProperty("storeFile") != null ||
        project.hasProperty("android.injected.signing.store.file")
    if (wantsRelease && !hasKey) {
        throw GradleException(
            "Release build needs a signing key: create android/keystore.properties (see android/README.md, \"Imzolash\").",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.yandex.android:mobileads:8.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
