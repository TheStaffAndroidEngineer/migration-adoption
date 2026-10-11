plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
tasks.withType<Test>().configureEach { useJUnitPlatform(); testLogging { events("passed", "failed", "skipped") } }

val stage = providers.gradleProperty("migrationStage").orElse("retired").get()
val usePort = stage in setOf("complete", "retired")
kotlin.sourceSets.named("main") { kotlin.srcDir(if (usePort) "src/port/kotlin" else "src/legacy/kotlin") }
dependencies {
    if (usePort) implementation(project(":cart-api"))
    else { implementation(project(":legacy-cart")); implementation(project(":cart-storage")) }
}
