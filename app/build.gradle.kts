import java.util.Properties

plugins { id("com.android.application") }

// Release signing comes from keystore.properties (never committed). Without it, builds use the debug key. That is
// fine for trying the launcher, but `adb install -r` over an existing Yeti TV install then fails (key mismatch).
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "tv.yeti.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "tv.yeti.launcher"
        minSdk = 21
        // targetSdk 28 on purpose: the built-in Wi-Fi picker needs WifiManager.addNetwork/enableNetwork/setWifiEnabled,
        // which Android refuses for apps targeting 29+. Never raise it above 28 without replacing the picker.
        targetSdk = 28
        versionCode = 8
        versionName = "1.0"
    }

    signingConfigs {
        if (!signing.isEmpty) create("yeti") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        val key = if (signing.isEmpty) signingConfigs.getByName("debug") else signingConfigs.getByName("yeti")
        getByName("debug") { signingConfig = key }
        getByName("release") { isMinifyEnabled = false; signingConfig = key }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = false }
}
