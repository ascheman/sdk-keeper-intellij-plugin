// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.ide

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.writeText

/** Runs real syncs against a fake SDKMAN directory and the IDE's real SDK table. */
@OptIn(ExperimentalPathApi::class)
@TestApplication
class JdkSyncServiceTest {

    @TempDir
    lateinit var tmp: Path

    private lateinit var sdkman: Path
    private val notifications = mutableListOf<Notification>()
    private val disposable = Disposer.newDisposable()
    private val projectFixture = projectFixture(openAfterCreation = true)

    @BeforeEach
    fun setUp() {
        sdkman = tmp.resolve("sdkman")
        sdkman.resolve("candidates/java").createDirectories()
        SyncSettings.get().loadState(
            SyncSettings.Options().apply {
                sdkmanDir = sdkman.toString()
                majors = "11, 17"
                linksDir = tmp.resolve("links").toString()
                checkUpdates = false
                checkSdkmanrc = false
            },
        )
        ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == Notifier.GROUP) notifications += notification
                }
            },
        )
        removeJavaSdks()
    }

    @AfterEach
    fun tearDown() {
        removeJavaSdks()
        Disposer.dispose(disposable)
    }

    @Test
    fun `adds one SDK per managed major and then has nothing to do`() {
        jdk("11.0.30-tem", "11.0.30")
        jdk("17.0.18-tem", "17.0.18")
        jdk("17.0.21-tem", "17.0.21")

        sync()
        assertEquals(mapOf("11" to home("11.0.30-tem"), "17" to home("17.0.21-tem")), javaSdks())
        val links = tmp.resolve("links")
        assertEquals(
            listOf(
                "$links/11 -> ${home("11.0.30-tem")}",
                "$links/17 -> ${home("17.0.21-tem")}",
                "Java 11 added (11.0.30-tem)",
                "Java 17 added (17.0.21-tem)",
            ),
            contents().single().split("<br>"),
        )
        assertEquals(home("17.0.21-tem"), links.resolve("17").readSymbolicLink().toString())

        notifications.clear()
        sync()
        assertEquals(emptyList<String>(), contents())
    }

    @Test
    fun `points an SDK with an old home to the newest release`() {
        jdk("17.0.18-tem", "17.0.18")
        jdk("17.0.21-tem", "17.0.21")
        addSdk("17", home("17.0.18-tem"))

        sync()
        assertEquals(home("17.0.21-tem"), javaSdks()["17"])
        assertTrue(contents().single().contains("Java 17 → 17.0.21-tem"), contents().toString())
    }

    @Test
    fun `keeps other SDKs while the cleanup is off`() {
        jdk("11.0.30-tem", "11.0.30")
        addSdk("11-test", home("11.0.30-tem"))

        sync()
        assertEquals(mapOf("11" to home("11.0.30-tem"), "11-test" to home("11.0.30-tem")), javaSdks())
    }

    @Test
    fun `removes other SDKs under SDKMAN, except those to keep`() {
        jdk("11.0.30-tem", "11.0.30")
        addSdk("11-test", home("11.0.30-tem"))
        addSdk("temurin-11", home("11.0.30-tem"))
        SyncSettings.get().state.pruneUnmanaged = true
        SyncSettings.get().keepSdks(listOf("temurin-11"))

        sync()
        assertEquals(mapOf("11" to home("11.0.30-tem"), "temurin-11" to home("11.0.30-tem")), javaSdks())
        val removed = notifications.single { it.title == "Other Java SDKs removed" }
        assertEquals("11-test", removed.content)
        assertEquals(listOf("Restore and keep"), removed.actions.map { it.templateText })
    }

    @Test
    fun `never chooses a JDK built for another architecture`() {
        jdk("17.0.18-tem", "17.0.18")
        jdk("17.0.21-tem", "17.0.21", arch = if (nativeArch() == "aarch64") "x86_64" else "aarch64")

        sync()
        assertEquals(home("17.0.18-tem"), javaSdks()["17"])
    }

    @Test
    fun `restores removed SDKs on request and keeps them`() {
        jdk("11.0.30-tem", "11.0.30")
        addSdk("11-test", home("11.0.30-tem"))
        SyncSettings.get().state.pruneUnmanaged = true

        sync()
        val removed = notifications.single { it.title == "Other Java SDKs removed" }
        runBlocking { withContext(Dispatchers.EDT) { Notification.fire(removed, removed.actions.single(), null) } }
        waitFor { "11-test" in javaSdks() }
        assertTrue("11-test" in SyncSettings.get().keptSdks)

        notifications.clear()
        sync()
        assertTrue("11-test" in javaSdks())
        assertTrue(notifications.none { it.title == "Other Java SDKs removed" })
    }

    @Test
    fun `moves a project to the managed SDK of the same version before removing its SDK`() {
        jdk("17.0.21-tem", "17.0.21")
        addSdk("temurin-17", home("17.0.21-tem"))
        val project = projectFixture.get()
        setProjectSdk(project, "temurin-17")
        SyncSettings.get().state.pruneUnmanaged = true

        sync()
        assertEquals("17", ProjectRootManager.getInstance(project).projectSdkName)
        assertFalse("temurin-17" in javaSdks())
        assertTrue(contents().single().contains("${project.name}: temurin-17 → 17"), contents().toString())
    }

    @Test
    fun `reads the version of an SDK without one from its release file`() {
        jdk("17.0.21-tem", "17.0.21")
        addSdk("temurin-17", home("17.0.21-tem"))
        // An SDK entry written by hand or by another tool may lack the version.
        onEdtWrite {
            ProjectJdkTable.getInstance().findJdk("temurin-17")!!.sdkModificator.apply {
                versionString = null
                commitChanges()
            }
        }
        val project = projectFixture.get()
        setProjectSdk(project, "temurin-17")
        SyncSettings.get().state.pruneUnmanaged = true

        sync()
        assertEquals("17", ProjectRootManager.getInstance(project).projectSdkName)
        assertFalse("temurin-17" in javaSdks())
    }

    @Test
    fun `keeps a dead SDK a project still uses and warns about it`() {
        jdk("17.0.21-tem", "17.0.21")
        jdk("21.0.10-tem", "21.0.10")
        addSdk("temurin-21", home("21.0.10-tem"))
        sdkman.resolve("candidates/java/21.0.10-tem").deleteRecursively()
        val project = projectFixture.get()
        setProjectSdk(project, "temurin-21")
        SyncSettings.get().state.pruneUnmanaged = true

        sync()
        assertTrue("temurin-21" in javaSdks())
        assertEquals("temurin-21", ProjectRootManager.getInstance(project).projectSdkName)
        val warning = notifications.single { it.title == "Java SDK temurin-21 no longer exists" }
        assertTrue(warning.content.contains(project.name), warning.content)
    }

    private fun setProjectSdk(project: com.intellij.openapi.project.Project, name: String) = onEdtWrite {
        ProjectRootManager.getInstance(project).projectSdk = ProjectJdkTable.getInstance().findJdk(name)
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within 10 s" }
            Thread.sleep(50)
        }
    }

    private fun sync() = runBlocking { service<JdkSyncService>().sync() }

    private fun nativeArch(): String = when (val arch = System.getProperty("os.arch")) {
        "amd64", "x64" -> "x86_64"
        "arm64" -> "aarch64"
        else -> arch
    }

    /** A minimal JDK: SDK Keeper and the IDE only need the `release` file. */
    private fun jdk(identifier: String, version: String, arch: String = nativeArch()) {
        val home = sdkman.resolve("candidates/java/$identifier").createDirectories()
        home.resolve("release").writeText(
            """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_VERSION="$version"
            OS_ARCH="$arch"
            """.trimIndent(),
        )
    }

    private fun home(identifier: String): String = sdkman.resolve("candidates/java/$identifier").toString()

    private fun contents(): List<String> = notifications.filter { it.title == "Java SDKs updated" }.map { it.content }

    private fun javaSdks(): Map<String, String?> =
        ProjectJdkTable.getInstance().allJdks.filter { it.sdkType is JavaSdk }.associate { it.name to it.homePath }

    private fun addSdk(name: String, home: String) = onEdtWrite {
        ProjectJdkTable.getInstance().addJdk(JavaSdk.getInstance().createJdk(name, home, false))
    }

    private fun removeJavaSdks() = onEdtWrite {
        val table = ProjectJdkTable.getInstance()
        table.allJdks.filter { it.sdkType is JavaSdk }.forEach { sdk: Sdk -> table.removeJdk(sdk) }
    }

    private fun onEdtWrite(block: () -> Unit) = runBlocking {
        withContext(Dispatchers.EDT) { WriteAction.run<RuntimeException> { block() } }
    }
}
