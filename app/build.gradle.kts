plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.chaquopy.python)
    alias(libs.plugins.kotlin.android)
}

import java.io.File
import java.util.Properties

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

val buildPythonCandidates = listOfNotNull(
    System.getenv("CHAQUOPY_BUILD_PYTHON"),
    "D:/Users/${System.getProperty("user.name")}/anaconda3/python.exe",
    "C:/Users/${System.getProperty("user.name")}/AppData/Local/Programs/Python/Python312/python.exe",
    "C:/Users/${System.getProperty("user.name")}/AppData/Local/Programs/Python/Python311/python.exe",
)

// 优先寻找本机已有的 3.12 解释器，避免把构建链路绑定到其他开发者机器的绝对路径。
val resolvedBuildPython = buildPythonCandidates.firstOrNull { candidate ->
    File(candidate).exists()
} ?: error(
    "未找到可用的 Python 解释器，请设置 CHAQUOPY_BUILD_PYTHON 指向 Python 3.12。",
)

android {
    namespace = "com.yokonex.bililive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yokonex.bililive"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

chaquopy {
    defaultConfig {
        version = libs.versions.python.get()
        buildPython(resolvedBuildPython)
        pip {
            install("aiohttp")
            install("PyJWT")
            install("python-requirements/qrcode-terminal-0.8.tar.gz")
            install("bilibili-api-python>=17.4.1")
        }
        pyc {
            src = false
            pip = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.brotli.dec)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
