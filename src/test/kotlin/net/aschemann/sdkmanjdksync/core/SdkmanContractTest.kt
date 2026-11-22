// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkmanjdksync.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

/**
 * Checks the real SDKMAN CLI and the remote APIs the plugin relies on, so that changes on their
 * side show up as a failing build (`./gradlew contractTest`, run weekly in CI).
 * It installs a JDK into `$SDKMAN_DIR`, hence the explicit opt-in. What it saw is written to
 * `$CONTRACT_OBSERVATIONS` (if set), which CI records in `CONTRACT.md`.
 */
@Tag("contract")
@EnabledIfEnvironmentVariable(named = "SDKMAN_CONTRACT", matches = "true")
class SdkmanContractTest {

    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()

    private fun get(url: String): String {
        val response = http.send(HttpRequest.newBuilder(URI(url)).header("User-Agent", "sdk-keeper").build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), url)
        return response.body()
    }

    private val sdkman = SdkmanInstallation.default()

    @Test
    fun `foojay still lists the known LTS majors`() {
        val lts = Lts.parseFoojay(get(Lts.FOOJAY_URL))
        observed["lts"] = lts.joinToString(", ")
        assertTrue(lts.containsAll(setOf(8, 11, 17, 21, 25)))
    }

    @Test
    fun `the catalog offers Temurin for every LTS line since 11`() {
        val platform = checkNotNull(sdkman.platform) { "no var/platform in ${sdkman.dir}" }
        val catalog = SdkmanCatalog.fetch(platform, ::get)
        observed["platform"] = platform
        for (major in listOf(8, 11, 17, 21, 25)) {
            observed["java $major"] = VENDORS.joinToString(", ") { vendor ->
                catalog.filter { it.major == major && it.vendor == vendor && it.variant == null && !it.earlyAccess }.maxOrNull()?.identifier ?: "-"
            }
        }
        for (major in listOf(11, 17, 21, 25)) {
            assertTrue(catalog.any { it.major == major && it.vendor == "tem" && !it.earlyAccess }, "no $major-tem for $platform")
        }
    }

    @Test
    fun `installs the newest LTS and finds it again`() {
        val catalog = SdkmanCatalog.fetch(sdkman.platform!!, ::get)
        val id = catalog.filter { it.major == 25 && it.vendor == "tem" && it.variant == null && !it.earlyAccess }.max()
        val cli = SdkmanCli(sdkman)
        if (!sdkman.isInstalled(id)) {
            val result = cli.install(id)
            assertTrue(result.ok, result.output)
        }

        assertTrue(id in sdkman.installedJava(), "${sdkman.javaCandidates} does not list $id")
        val home = checkNotNull(sdkman.javaHome(id)) { "no JDK home for $id" }
        assertEquals(25, JdkRelease.of(home)?.major)
        assertEquals(sdkman.javaCandidates.resolve(id.identifier).toRealPath(), cli.home(id)?.toRealPath())
    }

    companion object {
        /** Newest release per LTS line for these vendors, as recorded in `CONTRACT.md`. */
        private val VENDORS = listOf("tem", "zulu", "amzn")

        private val observed = sortedMapOf<String, String>()

        @JvmStatic
        @AfterAll
        fun writeObservations() {
            val target = System.getenv("CONTRACT_OBSERVATIONS")?.ifBlank { null } ?: return
            Path.of(target).writeText(observed.entries.joinToString("") { (key, value) -> "$key: $value\n" })
        }
    }
}
