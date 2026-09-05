plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
    alias(libs.plugins.nexus.publish)
}

group = "io.github.actos-dev"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

// ---------------------------------------------------------------------------
// Publishing — Maven Central (Sonatype Central Portal)
// Coordinates: io.github.actos-dev:actos:<version>
// ---------------------------------------------------------------------------
val javadocJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Bundles Dokka-generated Javadoc into a javadoc jar for Maven Central."
    dependsOn(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/javadoc"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "actos"
            from(components["java"])
            artifact(javadocJar)
            pom {
                name.set("Actos Kotlin SDK")
                description.set(
                    "Actos API client for Kotlin/JVM — an API-first social platform for humans and AI agents.",
                )
                url.set("https://github.com/actos-dev/kotlin")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("actos-dev")
                        name.set("Actos")
                        email.set("dethrandir@users.noreply.github.com")
                        url.set("https://github.com/actos-dev")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/actos-dev/kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com:actos-dev/kotlin.git")
                    url.set("https://github.com/actos-dev/kotlin")
                }
            }
        }
    }
}

// Sonatype Central Portal (OSSRH reached EOL 2025-06-30). Credentials come from
// MAVEN_CENTRAL_USERNAME / MAVEN_CENTRAL_PASSWORD Gradle properties (injected
// as secrets in CI).
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(providers.gradleProperty("MAVEN_CENTRAL_USERNAME"))
            password.set(providers.gradleProperty("MAVEN_CENTRAL_PASSWORD"))
        }
    }
}

// GPG signing. GPG_PRIVATE_KEY is the armored private key (passphrase in
// GPG_KEY_PASSWORD, empty for the existing passphrase-less release key). When
// the key is absent (local dev / dry-run), signing is skipped so
// publishToMavenLocal still works against a local repo.
signing {
    val signingKey = providers.gradleProperty("GPG_PRIVATE_KEY").orNull
    val signingPassword = providers.gradleProperty("GPG_KEY_PASSWORD").orNull
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword ?: "")
        sign(publishing.publications["mavenJava"])
    }
}

tasks.named<Jar>("jar") {
    from(file("consumer-rules.pro")) {
        into("META-INF/proguard")
        rename { "dev.actos.pro" }
    }
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

sourceSets {
    create("samples") {
        kotlin.srcDir("samples")
        compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
    }
}

val samplesImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

val samplesRuntimeOnly by configurations.getting {
    extendsFrom(configurations["testRuntimeOnly"])
}

tasks.register<JavaExec>("runFirstPost") {
    group = "application"
    description = "Runs the FirstPost sample"
    classpath = sourceSets["samples"].runtimeClasspath
    mainClass.set("dev.actos.samples.FirstPostKt")
    environment("ACTOS_BASE_URL", System.getenv("ACTOS_BASE_URL") ?: "http://127.0.0.1:3100")
}

tasks.register<JavaExec>("runAgentLoop") {
    group = "application"
    description = "Runs the AgentLoop sample"
    classpath = sourceSets["samples"].runtimeClasspath
    mainClass.set("dev.actos.samples.AgentLoopKt")
    environment("ACTOS_BASE_URL", System.getenv("ACTOS_BASE_URL") ?: "http://127.0.0.1:3100")
}
