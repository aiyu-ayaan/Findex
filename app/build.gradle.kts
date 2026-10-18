import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.aiyu.findex"

    packaging {
        jniLibs {
            // core/src/main/jniLibs ships a 16 KB-aligned rebuild (development/scripts/build-7zip-jbinding.sh);
            // project libs merge before the AAR's 4 KB-aligned copies, so pickFirst keeps ours.
            pickFirsts += "**/lib7-Zip-JBinding.so"
            // Compress .so in the APK: ~40% smaller download, extracted once at install.
            useLegacyPackaging = true
        }
        resources {
            // BouncyCastle post-quantum Picnic tables (~1.2 MB); smbj never uses Picnic.
            excludes += "org/bouncycastle/pqc/crypto/picnic/**"
            excludes += "META-INF/**/LICENSE*"
        }
    }
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiyu.findex"
        minSdk = 26
        targetSdk = 35
        // Written by scripts/set-version.mjs in the release PR.
        val version = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }
        versionCode = version.getProperty("build").toInt()
        versionName = version.getProperty("version")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ANDROID_KEYSTORE_FILE")
                ?: keystoreProperties.getProperty("storeFile")
                ?: "${System.getProperty("user.home")}/Keys/aiyu-soft-android-keys.jsk"
            val storePass = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
                ?: "akaAiyu@03"
            val alias = System.getenv("ANDROID_KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
                ?: "findex"
            val keyPass = System.getenv("ANDROID_KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
                ?: "akaAiyu@03"

            val storeF = file(storeFilePath)
            if (storeF.exists()) {
                storeFile = storeF
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 32-bit x86 has no real devices left; x86_64 stays for emulators and baseline profiles.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)
    // Installs the generated baseline profile on sideloaded/non-Play installs too.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
