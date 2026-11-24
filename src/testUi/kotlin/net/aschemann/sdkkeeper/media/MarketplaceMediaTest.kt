// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.media

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.notificationsToolWindow
import com.intellij.driver.sdk.ui.components.elements.balloon
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Takes the Marketplace screenshots: a real IDE with the plugin, a fake SDKMAN and a cluttered SDK table.
 *
 * Runs only in the container of `scripts/marketplace-media.sh`: it grabs the Xvfb display and writes to `/home/dev`.
 */
@OptIn(ExperimentalPathApi::class)
class MarketplaceMediaTest {

    private companion object {
        /** Logical size of the IDE frame; the virtual screen has twice as many pixels (`sun.java2d.uiScale=2`). */
        const val WIDTH = 1440
        const val HEIGHT = 900
    }

    private val mediaDir = Path(System.getProperty("media.dir")).createDirectories()
    private val home = Path(System.getProperty("media.home"))
    private val sdkman = FakeSdkman(home.resolve(".sdkman"))
    private val project = home.resolve("projects/shop-backend")

    @Test
    fun screenshots() {
        val lines = sdkman.temurinLines(17, 21, 25)
        // An up-to-date 17 and 25, and a 21 one patch behind: one update to offer, one .sdkmanrc to fix.
        val old17 = sdkman.install(sdkman.previousPatch(lines.getValue(17)))
        val jdk17 = sdkman.install(lines.getValue(17))
        val jdk21 = sdkman.install(sdkman.previousPatch(lines.getValue(21)))
        sdkman.install(lines.getValue(25))
        val zulu21 = sdkman.install("21.0.9-zulu", implementor = "Azul Systems, Inc.")
        project.createDirectories()
        project.resolve(".sdkmanrc").writeText("# Enable auto-env through the sdkman_auto_env config\njava=$jdk21\n")
        // The project still names one of the old duplicates; SDK Keeper moves it to the managed SDK of the same version.
        project.resolve(".idea").createDirectories().resolve("misc.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ProjectRootManager" version="2" languageLevel="JDK_21" project-jdk-name="temurin-21 (3)" project-jdk-type="JavaSDK" />
            </project>
            """.trimIndent(),
        )

        val context = Starter.newContext(
            "marketplaceMedia",
            TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(project)).withVersion(System.getProperty("ide.test.version")),
        )
        context.pluginConfigurator.installPluginFromPath(Path(System.getProperty("path.to.build.plugin")))
        // Its promotion would fill the editor area.
        context.pluginConfigurator.disablePlugins("com.intellij.air")
        context.configure(
            "temurin-17" to old17,
            "17 (2)" to jdk17,
            "temurin-21" to jdk21,
            "temurin-21 (3)" to jdk21,
            "zulu-21" to zulu21,
        )

        context.runIdeWithDriver().useDriverAndCloseIde {
            ideFrame {
                // Without a window manager the frame keeps its default size; make it fill the virtual screen.
                cast(component, Window::class).setBounds(0, 0, WIDTH, HEIGHT)
                waitForIndicators(5.minutes)
                // The last notice of the sync; the IDE shows the newest three as balloons and collapses the rest.
                balloon("Java 21 update available").shouldBe("SDK Keeper offered the update", { present() }, 2.minutes)
            }
            pause()
            screenshot("01-notifications")

            invokeAction("ActivateNotificationsToolWindow")
            ideFrame {
                notificationsToolWindow {
                    for (title in listOf("Java SDKs updated", "Other Java SDKs removed", "Java 21 update available", "Outdated Java in .sdkmanrc")) {
                        waitOneText(title, timeout = 30.seconds)
                    }
                }
            }
            pause()
            screenshot("02-notification-center")
            invokeAction("HideAllWindows")

            invokeAction("ShowProjectStructureSettings", now = false)
            dialog("Project Structure") {
                list("//div[@class='SidePanel']//div[@class='JBList']").clickItem("Platform Settings SDKs")
                pause()
                screenshot("03-sdk-list")
                x { byVisibleText("Cancel") }.click()
            }

            invokeAction("ShowSettings", now = false)
            ideFrame {
                settingsDialog {
                    center()
                    openTreeSettingsSection("Tools", "SDK Keeper")
                    pause()
                    screenshot("04-settings")
                    closeDialog()
                }
            }
        }
        home.deleteRecursively()
    }

    /** Settings and an SDK table as a long-time user would have them, written before the IDE starts. */
    private fun IDETestContext.configure(vararg sdks: Pair<String, String>) {
        val options = paths.configDir.resolve("options").createDirectories()
        options.resolve("sdk-keeper.xml").writeText(
            """
            <application>
              <component name="SdkKeeper">
                <option name="sdkmanDir" value="${sdkman.root}" />
                <option name="majors" value="17, 21, 25" />
                <option name="linksDir" value="${home.resolve(".jdks-lts")}" />
                <option name="pruneUnmanaged" value="true" />
              </component>
            </application>
            """.trimIndent(),
        )
        // openjdk-22 is long gone from disk.
        val table = (sdks.toList() + ("openjdk-22" to "22.0.2-open")).map { (name, id) -> name to sdkman.home(id) }
        options.resolve("jdk.table.xml").writeText(
            """
            <application>
              <component name="ProjectJdkTable">
            ${table.joinToString("\n") { (name, path) -> jdkEntry(name, path) }}
              </component>
            </application>
            """.trimIndent(),
        )
        // Installed from a ZIP, the plugin has no update source; the IDE would say so in a notice.
        options.resolve("notifications.xml").writeText(
            """
            <application>
              <component name="NotificationConfiguration">
                <notification groupId="Missing Plugin Update Source" displayType="NONE" shouldLog="false" />
              </component>
            </application>
            """.trimIndent(),
        )
        // No automatic trial: it reloads all plugins and shows its own notices.
        removeMigrateConfigAndCreateStubFile()
        applyVMOptionsPatch {
            addSystemProperty("ide.show.tips.on.startup.default.value", false)
            addSystemProperty("idea.trust.all.projects", true)
            addSystemProperty("user.home", home.toString())
            addSystemProperty("sun.java2d.uiScale", 2)
        }
    }

    private fun jdkEntry(name: String, home: String) = """
        <jdk version="2">
          <name value="$name" />
          <type value="JavaSDK" />
          <homePath value="$home" />
          <roots />
        </jdk>
    """.trimIndent()

    private fun Driver.dialog(title: String, action: UiComponent.() -> Unit) =
        ui.x { and(byType("com.intellij.openapi.ui.impl.DialogWrapperPeerImpl\$MyDialog"), byAccessibleName(title)) }
            .apply { center() }
            .apply(action)

    /** Dialogs open at their own size in the upper left corner without a window manager. */
    private fun UiComponent.center(width: Int = 1100, height: Int = 720) {
        driver.cast(component, Window::class).setBounds((WIDTH - width) / 2, (HEIGHT - height) / 2, width, height)
    }

    /** Lets animations and repaints finish. */
    private fun pause() = Thread.sleep(1_500)

    /** Grabs the virtual screen at full resolution; Java's own screenshots are scaled down to the logical size. */
    @Suppress("UnusedReceiverParameter")
    private fun Driver.screenshot(name: String) {
        val process = ProcessBuilder(
            "ffmpeg", "-loglevel", "error", "-f", "x11grab", "-draw_mouse", "0",
            "-video_size", System.getenv("SCREEN"), "-i", System.getenv("DISPLAY"),
            "-frames:v", "1", "-y", mediaDir.resolve("$name.png").toString(),
        ).inheritIO().start()
        check(process.waitFor() == 0) { "ffmpeg could not grab the screen for $name" }
    }
}
