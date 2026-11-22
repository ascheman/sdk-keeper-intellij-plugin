// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.core

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * The `release` file every JDK carries (`JAVA_VERSION="21.0.13"`, `IMPLEMENTOR="Eclipse Adoptium"`, ...).
 * It identifies a JDK independent of the directory it was installed to.
 */
data class JdkRelease(val properties: Map<String, String>) {

    val javaVersion: String? get() = properties["JAVA_VERSION"]

    val implementor: String? get() = properties["IMPLEMENTOR"]

    val major: Int? get() = javaMajorFromVersionString(javaVersion)

    /** Early access builds carry `-ea` (OpenJDK) or `-beta` (Temurin) in their runtime version, e.g. `28-ea+23`. */
    val earlyAccess: Boolean
        get() = properties["JAVA_RUNTIME_VERSION"]?.let { EARLY_ACCESS.containsMatchIn(it) } == true

    /**
     * Whether this JDK runs natively on [osArch] (`os.arch` of the IDE). An Intel JDK on Apple silicon
     * only runs under Rosetta, which macOS 28 drops. JDKs without `OS_ARCH` are assumed to fit.
     */
    fun runsNativelyOn(osArch: String): Boolean =
        properties["OS_ARCH"]?.let { normalizedArch(it) == normalizedArch(osArch) } ?: true

    companion object {
        private val EARLY_ACCESS = Regex("""-(ea|beta)\b""")

        private fun normalizedArch(arch: String): String =
            when (val a = arch.trim().lowercase()) {
                "amd64", "x64" -> "x86_64"
                "arm64" -> "aarch64"
                else -> a
            }

        fun parse(text: String): JdkRelease =
            JdkRelease(
                text.lines()
                    .map { it.split('=', limit = 2) }
                    .filter { it.size == 2 && it[0].isNotBlank() }
                    .associate { (key, value) -> key.trim() to value.trim().removeSurrounding("\"") },
            )

        /** The release file of the JDK at [path], see [homeOf]; null if there is none. */
        fun of(path: Path): JdkRelease? =
            homeOf(path)?.let { home -> runCatching { parse(home.resolve("release").readText()) }.getOrNull() }

        /** The JDK home at or directly below [root] (macOS bundles keep it in `Contents/Home`, also one `*.jdk` level down). */
        fun homeOf(root: Path): Path? {
            val bundled = runCatching { root.listDirectoryEntries("*.jdk").map { it.resolve("Contents/Home") } }.getOrDefault(emptyList())
            return (listOf(root, root.resolve("Contents/Home")) + bundled).firstOrNull { it.resolve("release").isRegularFile() }
        }
    }
}
