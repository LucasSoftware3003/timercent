plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
base { archivesName.set("timercent") }
android {
    namespace = "it.timercent"
    compileSdk = 34
    defaultConfig { applicationId = "it.timercent"; minSdk = 26; targetSdk = 34; versionCode = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1; versionName = "1.22" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
