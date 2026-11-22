// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.ide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class SyncSettingsTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `reads settings stored before the rename`() {
        val legacy = tmp.resolve("sdkman-jdk-sync.xml")
        legacy.writeText(
            """
            <application>
              <component name="SdkmanJdkSync">
                <option name="vendorPreference" value="zulu,tem" />
                <option name="notifiedUpdates" value="21.0.13.0+9-tem,25.0.5.0+8-tem" />
              </component>
            </application>
            """.trimIndent(),
        )

        val options = SyncSettings.readLegacy(legacy)!!
        assertEquals("zulu,tem", options.vendorPreference)
        assertEquals("21.0.13.0+9-tem,25.0.5.0+8-tem", options.notifiedUpdates)
        assertEquals(false, options.pruneUnmanaged)
    }
}
