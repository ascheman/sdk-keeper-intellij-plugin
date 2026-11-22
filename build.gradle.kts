import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath")
        if (localPath.isPresent) {
            local(localPath)
        } else {
            create(
                IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get()),
                providers.gradleProperty("platformVersion").get(),
            )
        }
        bundledPlugin("com.intellij.java")
    }

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
