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

    // direct: the APK on harfix.uz and Telegram. Adds the photos permission ("Skrinshotlar
    //         buferda") and the Yandex ad banner (src/direct). No accessibility service, so
    //         Google Play Protect does not block it when it is installed from a file.
    // play:   the Google Play bundle, kept plain for review: no photos permission, no ad SDK,
    //         no accessibility service (android/PLAY.md, section 0). Bringing ads back to Play
    //         means an update plus new Ads / Data safety / trader answers in Play Console.
    flavorDimensions += "store"
    productFlavors {
        create("direct") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    signingConfigs {
        // Test builds are signed with this fixed key (not secret, like Android's own debug key),
        // so a newer test APK from CI installs over the previous one instead of being refused.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        // The test build installs next to the real app, with its own id and names, so its
        // keyboard and service are not mistaken for the real app's in Android's lists.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Harfix (test)")
            resValue("string", "keyboard_name", "Harfix klaviaturasi (test)")
            resValue("string", "words_name", "Harfix (test): odatiy klaviaturada")
        }
        release {
            resValue("string", "app_name", "Harfix")
            resValue("string", "keyboard_name", "Harfix klaviaturasi")
            resValue("string", "words_name", "Harfix: odatiy klaviaturada")
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
    // Website APK only; the Play build's BannerAds (src/play) shows nothing.
    "directImplementation"("com.yandex.android:mobileads:8.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
