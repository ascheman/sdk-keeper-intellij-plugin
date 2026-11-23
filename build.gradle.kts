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
    useJUnitPlatform {
        excludeTags("contract")
    }
}

val contractTest by tasks.registering(Test::class) {
    description = "Checks the real SDKMAN CLI and APIs; installs a JDK, so it needs SDKMAN_CONTRACT=true."
    group = "verification"
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("contract")
    }
    outputs.upToDateWhen { false }
}

tasks.jar {
    from(files("LICENSE", "NOTICE")) { into("META-INF") }
}
