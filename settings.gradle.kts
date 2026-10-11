pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "migration-adoption"
val stage = providers.gradleProperty("migrationStage").orElse("retired").get()
require(stage in setOf("bridge", "saved", "complete", "retired")) { "Unknown migrationStage: $stage" }
val comparison = providers.gradleProperty("comparisonTests").orElse("false").get().toBooleanStrict()
include(":app", ":cart-storage", ":cart-api", ":feature-saved", ":feature-cart", ":contract-tests")
if (stage != "retired" || comparison) include(":legacy-cart", ":cart-adapter")
if (stage == "retired" || comparison) include(":cart-engine")
