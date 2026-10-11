plugins { alias(libs.plugins.androidApplication); alias(libs.plugins.kotlinCompose) }
val stage = providers.gradleProperty("migrationStage").orElse("retired").get()
val stages = listOf("bridge", "saved", "complete", "retired")
android {
    namespace = "staff.android.adoption"
    compileSdk { version = release(37) { minorApiLevel = 1 } }; buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "staff.android.adoption"; minSdk = 26; targetSdk = 37
        versionCode = 2901 + stages.indexOf(stage); versionName = "29.$stage"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").kotlin.srcDir("src/$stage/kotlin")
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release {
        optimization { enable = true }; signingConfig = signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
dependencies {
    implementation(project(":cart-storage")); implementation(project(":feature-saved")); implementation(project(":feature-cart"))
    if (stage == "retired") { implementation(project(":cart-engine")); implementation(project(":cart-api")) }
    else { implementation(project(":legacy-cart")); implementation(project(":cart-adapter")) }
    implementation(libs.androidxLifecycleRuntimeCompose); implementation(libs.coroutinesAndroid)
    implementation(libs.androidxLifecycleViewModel); implementation(libs.androidxActivityCompose)
    implementation(platform(libs.composeBom)); implementation(libs.composeUi); implementation(libs.composeFoundation); implementation(libs.composeMaterial3)
    androidTestImplementation(platform(libs.composeBom)); androidTestImplementation(libs.composeTestJunit4)
    androidTestImplementation(libs.androidxTestJunit); androidTestImplementation(libs.androidxTestRunner); androidTestImplementation(libs.espressoCore)
}
