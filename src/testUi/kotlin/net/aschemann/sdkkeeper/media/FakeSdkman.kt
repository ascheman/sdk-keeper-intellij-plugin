// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.media

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * An SDKMAN directory whose JDKs only have what SDK Keeper and the IDE read: the `release` file.
 *
 * Its versions come from SDKMAN's live catalog, so the plugin's update checks see a real SDKMAN.
 */
class FakeSdkman(val root: Path) {

    private val platform = if (System.getProperty("os.arch") == "aarch64") "linuxarm64" else "linuxx64"

    init {
        root.resolve("var").createDirectories().resolve("platform").writeText(platform)
    }

    fun home(identifier: String): String = root.resolve("candidates/java/$identifier").toString()

    /** The newest Temurin release per major, e.g. `21.0.13.0+9-tem`. */
    fun temurinLines(vararg majors: Int): Map<Int, String> {
        val catalog = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("https://api.sdkman.io/2/candidates/java/$platform/versions/all")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body().split(',').map { it.trim() }
        return majors.associateWith { major ->
            catalog.last { it.startsWith("$major.") && it.endsWith("-tem") }
        }
    }

    /** The release before [identifier], as an outdated installation would have it: `21.0.13.0+9-tem` → `21.0.12-tem`. */
    fun previousPatch(identifier: String): String {
        val (major, minor, patch) = identifier.substringBefore('-').split('.', '+')
        return "$major.$minor.${patch.toInt() - 1}-tem"
    }

    /** Creates the JDK and returns its identifier. */
    fun install(identifier: String, implementor: String = "Eclipse Adoptium"): String {
        val home = Path.of(home(identifier)).createDirectories()
        val version = identifier.substringBefore('-').split('.', '+').take(3).joinToString(".")
        home.resolve("release").writeText(
            """
            IMPLEMENTOR="$implementor"
            JAVA_VERSION="$version"
            OS_ARCH="${if (System.getProperty("os.arch") == "aarch64") "aarch64" else "x86_64"}"
            """.trimIndent() + "\n",
        )
        home.resolve("bin").createDirectories()
        return identifier
    }
}
