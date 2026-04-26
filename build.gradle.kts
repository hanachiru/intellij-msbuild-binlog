import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.15.0"
}

group = property("pluginGroup").toString()
version = property("pluginVersion").toString()

val localRiderCandidates = listOf(
    file("${System.getProperty("user.home")}/Applications/Rider.app"),
    file("${System.getProperty("user.home")}/Applications/Rider 2026.1 EAP2.app"),
)
val localRider = localRiderCandidates.firstOrNull { it.exists() }

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

val helperProjectFile = layout.projectDirectory.file("tools/BinlogJsonExporter/BinlogJsonExporter.csproj")
val helperPublishDir = layout.buildDirectory.dir("binlog-helper/publish")
val helperResourcesDir = layout.buildDirectory.dir("generated-resources/main/binlog-helper")

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated-resources/main"))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    intellijPlatform {
        if (localRider != null) {
            local(localRider.absolutePath)
        } else {
            rider(providers.gradleProperty("platformVersion")) {
                useInstaller = false
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }
}

val publishBinlogHelper by tasks.registering(Exec::class) {
    inputs.files(fileTree("tools/BinlogJsonExporter") {
        exclude("**/bin/**")
        exclude("**/obj/**")
    })
    outputs.dir(helperPublishDir)

    commandLine(
        "dotnet",
        "publish",
        helperProjectFile.asFile.absolutePath,
        "-c",
        "Release",
        "-o",
        helperPublishDir.get().asFile.absolutePath,
        "--nologo",
    )
}

val stageBinlogHelper by tasks.registering(Sync::class) {
    dependsOn(publishBinlogHelper)
    from(helperPublishDir)
    into(helperResourcesDir)

    doLast {
        val root = helperResourcesDir.get().asFile
        root.mkdirs()

        val entries = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

        root.resolve("manifest.txt").writeText(entries.joinToString(separator = "\n", postfix = "\n"))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.processResources {
    dependsOn(stageBinlogHelper)
}

tasks.named("buildSearchableOptions") {
    enabled = false
}

tasks.clean {
    delete(helperPublishDir, helperResourcesDir)
}

tasks.wrapper {
    gradleVersion = "9.4.1"
}
