// SPDX-License-Identifier: Apache-2.0
package net.aschemann.sdkkeeper.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlin.coroutines.cancellation.CancellationException

class SdkKeeperStartup : ProjectActivity {
    /** A failure here must never get in the way of opening the project, so it is only logged. */
    override suspend fun execute(project: Project) {
        try {
            service<JdkSyncService>().ensureStarted()
            if (SyncSettings.get().state.checkSdkmanrc) SdkmanrcChecker.check(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("SDK Keeper startup failed for ${project.name}", e)
        }
    }
}

class SyncNowAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        service<JdkSyncService>().apply {
            ensureStarted()
            requestSync()
        }
    }
}
