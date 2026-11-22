// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class OtherJdksTest {

    @TempDir
    lateinit var tmp: Path

    private fun jdk(home: String, version: String, implementor: String, runtime: String = version): Path =
        tmp.resolve(home).createDirectories().also {
            it.resolve("release").writeText("IMPLEMENTOR=\"$implementor\"\nJAVA_VERSION=\"$version\"\nJAVA_RUNTIME_VERSION=\"$runtime\"\n")
        }

    @Test
    fun `maps vendors and versions`() {
        assertEquals("amzn", OtherJdks.vendorOf("Amazon.com Inc."))
        assertEquals("brew", OtherJdks.vendorOf("Homebrew"))
        assertEquals("acme", OtherJdks.vendorOf("ACME Corp."))
        assertNull(OtherJdks.vendorOf(null))
        assertEquals("8.0.301", OtherJdks.versionOf("1.8.0_301"))
        assertEquals("27.0.1.0.3", OtherJdks.versionOf("27.0.1.0.3"))
        assertNull(OtherJdks.versionOf("unknown"))
    }

    @Test
    fun `finds JDKs once, without early access and without SDKMAN`() {
        val sdkman = tmp.resolve("sdkman/candidates/java").createDirectories()
        jdk("sdkman/candidates/java/21.0.13.0+9-tem", "21.0.13", "Eclipse Adoptium")
        val system = jdk("jvms/jdk1.8.0_301.jdk/Contents/Home", "1.8.0_301", "Oracle Corporation")
        val brew = jdk("brew/Cellar/openjdk@21/21.0.13/libexec/openjdk.jdk/Contents/Home", "21.0.13", "Homebrew")
        val opt = tmp.resolve("brew/opt").createDirectories()
        Files.createSymbolicLink(opt.resolve("openjdk@21"), tmp.resolve("brew/Cellar/openjdk@21/21.0.13"))
        Files.createSymbolicLink(opt.resolve("java21"), tmp.resolve("brew/Cellar/openjdk@21/21.0.13"))
        jdk("jvms/jdk-28.jdk/Contents/Home", "28", "Oracle Corporation", runtime = "28-ea+23")

        val found = OtherJdks.find(
            listOf(
                tmp.resolve("jvms/jdk1.8.0_301.jdk"),
                tmp.resolve("jvms/jdk-28.jdk"),
                opt.resolve("openjdk@21/libexec"),
                opt.resolve("java21/libexec"),
                sdkman.resolve("21.0.13.0+9-tem"),
                tmp.resolve("missing"),
            ),
            sdkman,
        )

        assertEquals(
            listOf("8.0.301-oracle" to system.toRealPath(), "21.0.13-brew" to brew.toRealPath()),
            found.map { it.id.identifier to it.home },
        )
    }

    @Test
    fun `default locations cover JDKs downloaded by the IDE and Homebrew libexec`() {
        val home = tmp.resolve("home")
        val mac = home.resolve("Library/Java/JavaVirtualMachines/corretto-21.0.6").createDirectories()
        val linux = home.resolve(".jdks/temurin-21.0.6").createDirectories()

        val locations = OtherJdks.defaultLocations(home)
        assertEquals(true, mac in locations && linux in locations, locations.toString())
        locations.filter { "/opt/" in it.toString() }.forEach { assertEquals("libexec", it.fileName.toString()) }
    }
}
