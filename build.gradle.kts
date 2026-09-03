plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.openapi.generator)
}

group = "dev.actos"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

apply(from = "gradle/openapi.gradle.kts")

dependencies {
    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization & Coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude("**/dev/actos/model/**")
    }
}

detekt {
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/dev/actos/model/**")
}

val checkNoAndroidImports =
    tasks.register("checkNoAndroidImports") {
        group = "verification"
        description = "Ensures zero android.* imports are present in core library sources"
        val mainSrcDir = layout.projectDirectory.dir("src/main/kotlin")
        inputs.dir(mainSrcDir).optional()

        doLast {
            val srcDir = mainSrcDir.asFile
            if (!srcDir.exists()) return@doLast

            val violations = mutableListOf<String>()
            srcDir
                .walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("import android.") || trimmed.startsWith("import static android.")) {
                                violations.add("${file.relativeTo(projectDir)}:${index + 1}: $trimmed")
                            }
                        }
                    }
                }

            if (violations.isNotEmpty()) {
                val header = "Forbidden android.* imports found in core library (Zero Android dependencies guarantee):"
                val details = violations.joinToString("\n")
                throw GradleException("$header\n$details")
            }
        }
    }

tasks.named("check") {
    dependsOn(checkNoAndroidImports)
}

sourceSets {
    create("contractTest") {
        compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
    }
}

val contractTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

val contractTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations["testRuntimeOnly"])
}

tasks.register<Test>("contractTest") {
    description = "Runs contract tests against live backend."
    group = "verification"
    testClassesDirs = sourceSets["contractTest"].output.classesDirs
    classpath = sourceSets["contractTest"].runtimeClasspath
    useJUnitPlatform()
    environment("ACTOS_BASE_URL", System.getenv("ACTOS_BASE_URL") ?: "http://127.0.0.1:3100")
}
