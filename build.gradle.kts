import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        snapshots()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath")
        if (localPath.isPresent) {
            local(localPath)
        } else {
            create {
                type = IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get())
                version = providers.gradleProperty("platformVersion")
                // EAP snapshots (e.g. LATEST-EAP-SNAPSHOT) only exist as Maven artifacts, not as installers.
                useInstaller = providers.gradleProperty("platformUseInstaller").map { it.toBoolean() }.orElse(true)
            }
        }
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The platform test framework still references JUnit 4 classes.
    testRuntimeOnly("junit:junit:4.13.2")
    // Starter brings the JUnit Vintage engine, which needs JUnit 4.
    "testUiRuntimeOnly"("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            if (providers.gradleProperty("verifyEap").isPresent) {
                // the newest EAP of every branch since sinceBuild, to see breaking changes before they ship
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                    channels = listOf(ProductRelease.Channel.EAP)
                    sinceBuild = providers.gradleProperty("pluginSinceBuild")
                }
            } else {
                recommended()
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// The contract tests check SDKMAN and the APIs behind `core`; they need no IDE, so they get a source set of their own,
// away from the platform test framework.
val contractTestSourceSet = sourceSets.create("contractTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
dependencies {
    "contractTestImplementation"(kotlin("stdlib"))
    "contractTestImplementation"(platform("org.junit:junit-bom:6.1.3"))
    "contractTestImplementation"("org.junit.jupiter:junit-jupiter")
    "contractTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
tasks.register<Test>("contractTest") {
    description = "Checks the real SDKMAN CLI and APIs; installs a JDK, so it needs SDKMAN_CONTRACT=true."
    group = "verification"
    testClassesDirs = contractTestSourceSet.output.classesDirs
    classpath = contractTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

tasks.jar {
    from(files("LICENSE", "NOTICE")) { into("META-INF") }
}

// Marketplace screenshots and videos: ./scripts/marketplace-media.sh runs testUi in a Linux container.
// Starter and Driver are built for Java 25, so this source set is, too.
kotlin.target.compilations.named("testUi") {
    compileTaskProvider.configure {
        (this as org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile).compilerOptions.jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
    }
}
tasks.named<JavaCompile>("compileTestUiJava") {
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileTestUiKotlin") {
    (this as org.jetbrains.kotlin.gradle.tasks.UsesKotlinJavaToolchain).kotlinJavaToolchain.toolchain.use(
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    )
}
tasks.named<Test>("testUi") {
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    // Starter uses the IDE's own file system classes, which extend JDK internals.
    jvmArgs("--add-exports=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED")
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    systemProperty("media.dir", providers.gradleProperty("mediaDir").orElse(layout.buildDirectory.dir("marketplace-media").map { it.asFile.path }).get())
    systemProperty("media.home", providers.gradleProperty("mediaHome").orElse(layout.buildDirectory.dir("marketplace-media-home").map { it.asFile.path }).get())
    outputs.upToDateWhen { false }
}
