import org.gradle.api.artifacts.ProjectDependency
buildscript { dependencies { classpath(libs.kotlinGradlePlugin) } }
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinCompose) apply false
}
// Intentionally not configuration-cache compatible: this small teaching inspector reads
// the evaluated project graph. Build-performance investigations stay in Chapter 18.
tasks.register("migrationGraph") {
    val output = layout.buildDirectory.file("reports/migration-graph.tsv")
    outputs.file(output); outputs.upToDateWhen { false }
    doLast {
        val rows = mutableListOf("stage\t" + providers.gradleProperty("migrationStage").orElse("retired").get())
        subprojects.sortedBy { it.path }.forEach { owner ->
            rows += "module\t${owner.path}"
            owner.configurations.filter { it.isCanBeDeclared }.forEach { config ->
                config.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                    if (dependency.path != owner.path) rows += "edge\t${owner.path}\t${config.name}\t${dependency.path}"
                }
            }
        }
        output.get().asFile.apply { parentFile.mkdirs(); writeText(rows.sorted().joinToString("\n") + "\n") }
    }
}
