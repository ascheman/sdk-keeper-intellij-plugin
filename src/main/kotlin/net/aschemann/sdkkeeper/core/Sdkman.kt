// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.core

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** A local SDKMAN installation (default `$SDKMAN_DIR` or `~/.sdkman`). */
class SdkmanInstallation(val dir: Path) {

    val javaCandidates: Path get() = dir.resolve("candidates/java")

    val exists: Boolean get() = javaCandidates.isDirectory()

    /** SDKMAN platform id such as `darwinarm64`, as detected by SDKMAN itself. */
    val platform: String?
        get() = runCatching { dir.resolve("var/platform").readText().trim() }.getOrNull()?.ifEmpty { null }

    /** Installed versions; directories without a JDK (e.g. aborted installs) are skipped. */
    fun installedJava(): List<JavaVersionId> =
        if (!exists) {
            emptyList()
        } else {
            javaCandidates.listDirectoryEntries()
                .filter { it.isDirectory() && !it.isSymbolicLink() && JdkRelease.homeOf(it) != null }
                .mapNotNull { JavaVersionId.parse(it.name) }
        }

    fun isInstalled(id: JavaVersionId): Boolean = javaCandidates.resolve(id.identifier).isDirectory()

    /** The JDK home of an installed version (handles the `zulu-8.jdk/Contents/Home` layout). */
    fun javaHome(id: JavaVersionId): Path? = JdkRelease.homeOf(javaCandidates.resolve(id.identifier))

    companion object {
        fun default(): SdkmanInstallation =
            SdkmanInstallation(System.getenv("SDKMAN_DIR")?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"), ".sdkman"))

        fun of(configured: String): SdkmanInstallation =
            if (configured.isBlank()) default() else SdkmanInstallation(Paths.get(configured.replaceFirst("~", System.getProperty("user.home"))))
    }
}

/** Remote SDKMAN catalog of Java versions. */
object SdkmanCatalog {
    const val API = "https://api.sdkman.io/2"

    fun url(platform: String): String = "$API/candidates/java/$platform/versions/all"

    fun parse(response: String): List<JavaVersionId> =
        response.split(',', '\n').mapNotNull { JavaVersionId.parse(it) }

    fun fetch(platform: String, http: (String) -> String): List<JavaVersionId> = parse(http(url(platform)))
}

/** Runs `sdk` commands through bash; installing never makes the new version the default. */
class SdkmanCli(private val installation: SdkmanInstallation) {

    data class Result(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /** Installs [id]; only succeeds if SDKMAN reports a [home] for it afterwards. */
    fun install(id: JavaVersionId, timeoutMinutes: Long = 30): Result {
        val result = run("printf 'n\\n' | sdk install java \"\$1\"", id.identifier, timeoutMinutes)
        return if (result.ok && home(id) != null) result else Result(result.exitCode.takeIf { it != 0 } ?: 1, result.output)
    }

    /** Where SDKMAN keeps [id], as answered by `sdk home`; null if it is not installed. */
    fun home(id: JavaVersionId): Path? {
        val result = run("sdk home java \"\$1\"", id.identifier, 1)
        if (!result.ok) return null
        val line = result.output.lines().lastOrNull { it.isNotBlank() }?.trim() ?: return null
        return runCatching { Paths.get(line) }.getOrNull()?.takeIf { it.isDirectory() }
    }

    private fun run(command: String, argument: String, timeoutMinutes: Long): Result {
        val script = "source \"\$SDKMAN_DIR/bin/sdkman-init.sh\" && $command"
        val process = ProcessBuilder("/bin/bash", "-c", script, "sdk", argument)
            .redirectErrorStream(true)
            .also { it.environment()["SDKMAN_DIR"] = installation.dir.toString() }
            .start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return Result(-1, output.append("timeout after $timeoutMinutes minutes").toString())
        }
        reader.join()
        return Result(process.exitValue(), output.toString())
    }
}
