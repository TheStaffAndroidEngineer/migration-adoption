plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
tasks.withType<Test>().configureEach { useJUnitPlatform(); testLogging { events("passed", "failed", "skipped") } }

val stage = providers.gradleProperty("migrationStage").orElse("retired").get()
val comparison = providers.gradleProperty("comparisonTests").orElse("false").get().toBooleanStrict()
kotlin.sourceSets.named("test") { kotlin.srcDir(if (stage == "retired") "src/native/kotlin" else "src/adapter/kotlin"); if (comparison) kotlin.srcDir("src/comparison/kotlin") }
dependencies {
    testImplementation(project(":cart-api")); testImplementation(project(":cart-storage"))
    testImplementation(project(":feature-saved")); testImplementation(project(":feature-cart"))
    if (stage != "retired" || comparison) { testImplementation(project(":legacy-cart")); testImplementation(project(":cart-adapter")) }
    if (stage == "retired" || comparison) testImplementation(project(":cart-engine"))
    testImplementation(libs.junitJupiter); testRuntimeOnly(libs.junitPlatformLauncher)
}
