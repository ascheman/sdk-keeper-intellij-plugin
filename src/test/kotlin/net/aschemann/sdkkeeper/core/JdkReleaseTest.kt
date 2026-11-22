// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class JdkReleaseTest {

    @Test
    fun `reads version, major and implementor`() {
        val temurin = JdkRelease.parse(
            """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_RUNTIME_VERSION="21.0.13+9-LTS"
            JAVA_VERSION="21.0.13"
            MODULES="java.base java.logging"
            """.trimIndent(),
        )
        assertEquals("21.0.13", temurin.javaVersion)
        assertEquals("Eclipse Adoptium", temurin.implementor)
        assertEquals(21, temurin.major)
        assertFalse(temurin.earlyAccess)

        val zulu8 = JdkRelease.parse("IMPLEMENTOR=\"Azul Systems, Inc.\"\nJAVA_VERSION=\"1.8.0_512\"\n")
        assertEquals(8, zulu8.major)
        assertFalse(zulu8.earlyAccess)
    }

    @Test
    fun `recognizes early access builds`() {
        assertTrue(JdkRelease.parse("JAVA_VERSION=\"28\"\nJAVA_RUNTIME_VERSION=\"28-ea+23-2104\"").earlyAccess)
        assertTrue(JdkRelease.parse("JAVA_VERSION=\"28\"\nJAVA_RUNTIME_VERSION=\"28-beta+23-ea\"").earlyAccess)
        assertFalse(JdkRelease.parse("JAVA_VERSION=\"27.0.1.0.3\"\nJAVA_RUNTIME_VERSION=\"27.0.1.0.3\"").earlyAccess)
    }

    @Test
    fun `finds the home of flat and macOS bundle layouts`(@TempDir tmp: Path) {
        val flat = tmp.resolve("8.0.512.0+9-zulu").createDirectories()
        flat.resolve("release").writeText("JAVA_VERSION=\"1.8.0_512\"")
        val bundle = tmp.resolve("8.0.462-zulu/zulu-8.jdk/Contents/Home").createDirectories()
        bundle.resolve("release").writeText("JAVA_VERSION=\"1.8.0_462\"")
        val system = tmp.resolve("jdk1.8.0_301.jdk/Contents/Home").createDirectories()
        system.resolve("release").writeText("JAVA_VERSION=\"1.8.0_301\"")
        val empty = tmp.resolve("21.0.13.0+9-tem").createDirectories()

        assertEquals(flat, JdkRelease.homeOf(flat))
        assertEquals(bundle, JdkRelease.homeOf(tmp.resolve("8.0.462-zulu")))
        assertEquals(system, JdkRelease.homeOf(tmp.resolve("jdk1.8.0_301.jdk")))
        assertEquals(8, JdkRelease.of(tmp.resolve("8.0.462-zulu"))?.major)
        assertNull(JdkRelease.homeOf(empty))
        assertNull(JdkRelease.of(tmp.resolve("missing")))
    }
}
