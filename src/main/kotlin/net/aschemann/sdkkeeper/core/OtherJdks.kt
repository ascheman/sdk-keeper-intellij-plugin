// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.core

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * JDKs installed outside SDKMAN in well-known places (experimental). They are identified by their
 * `release` file and get a SDKMAN-style id such as `21.0.6-amzn`, so they compete with the SDKMAN
 * JDKs under the same vendor preference.
 */
object OtherJdks {

    data class Found(val id: JavaVersionId, val home: Path)

    /** `IMPLEMENTOR` values mapped to SDKMAN vendor suffixes; Homebrew builds get `brew`. */
    private val VENDORS = mapOf(
        "Eclipse Adoptium" to "tem",
        "Azul Systems, Inc." to "zulu",
        "Amazon.com Inc." to "amzn",
        "BellSoft" to "librca",
        "Microsoft" to "ms",
        "SAP SE" to "sapmchn",
        "Oracle Corporation" to "oracle",
        "GraalVM Community" to "graalce",
        "JetBrains s.r.o." to "jbr",
        "Homebrew" to "brew",
    )

    fun vendorOf(implementor: String?): String? =
        implementor?.let { VENDORS[it] ?: it.lowercase().takeWhile { c -> c in 'a'..'z' }.ifEmpty { null } }

    /** `1.8.0_301` → `8.0.301`, `21.0.6` → `21.0.6`; null for anything without a leading number. */
    fun versionOf(javaVersion: String?): String? {
        val plain = javaVersion?.trim()?.let { if (it.startsWith("1.")) it.removePrefix("1.").replace('_', '.') else it } ?: return null
        return Regex("""^\d+(\.\d+)*""").find(plain)?.value
    }

    /**
     * Directories that may hold a JDK: system and IDE-downloaded JDKs on macOS and Linux, and
     * Homebrew's `openjdk*` formulae (their JDK lives below `libexec`).
     */
    fun defaultLocations(home: Path = Paths.get(System.getProperty("user.home"))): List<Path> {
        val parents = listOf(
            Paths.get("/Library/Java/JavaVirtualMachines"),
            home.resolve("Library/Java/JavaVirtualMachines"),
            Paths.get("/usr/lib/jvm"),
            home.resolve(".jdks"),
        )
        val brew = listOf(Paths.get("/opt/homebrew/opt"), Paths.get("/usr/local/opt"), Paths.get("/home/linuxbrew/.linuxbrew/opt"))
        return parents.flatMap { children(it) } +
            brew.flatMap { children(it) }.filter { it.name.startsWith("openjdk") }.map { it.resolve("libexec") }
    }

    /**
     * The JDKs found in [locations], once per real directory, without early access builds and
     * without anything under [sdkmanJava]. JDKs whose `release` file names no vendor (e.g. Oracle
     * JDK 8) are skipped.
     */
    fun find(locations: List<Path>, sdkmanJava: Path): List<Found> {
        val sdkmanReal = runCatching { sdkmanJava.toRealPath() }.getOrNull()
        return locations.mapNotNull { location ->
            val home = JdkRelease.homeOf(location) ?: return@mapNotNull null
            val real = runCatching { home.toRealPath() }.getOrNull() ?: return@mapNotNull null
            if (sdkmanReal != null && real.startsWith(sdkmanReal)) return@mapNotNull null
            val release = JdkRelease.of(home)?.takeUnless { it.earlyAccess } ?: return@mapNotNull null
            val version = versionOf(release.javaVersion) ?: return@mapNotNull null
            val vendor = vendorOf(release.implementor) ?: return@mapNotNull null
            JavaVersionId.parse("$version-$vendor")?.let { Found(it, real) }
        }.distinctBy { it.home }
    }

    private fun children(dir: Path): List<Path> =
        if (dir.isDirectory()) runCatching { dir.listDirectoryEntries().sorted() }.getOrDefault(emptyList()) else emptyList()
}
