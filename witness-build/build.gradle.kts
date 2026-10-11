buildscript { repositories { mavenCentral() }; dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") } }
plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "staff.android.adoption.witness"
    compileSdk { version = release(37) { minorApiLevel = 1 } }; buildToolsVersion = "37.0.0"
    defaultConfig { applicationId = "staff.android.adoption.witness"; minSdk = 26; targetSdk = 37; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}

kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
