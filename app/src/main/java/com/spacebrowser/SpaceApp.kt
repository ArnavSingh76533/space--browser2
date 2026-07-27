package com.spacebrowser

import android.app.Application
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.media.MediaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpaceApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.tabManager.start()

        // Housekeeping off the critical path: apply the history retention rule.
        appScope.launch {
            val s = container.settingsRepository.snapshot()
            container.browsingRepository.trimHistory(s.historyRetentionDays)
            if (s.ytDlpEnabled) MediaDownloader.ensureReady(this@SpaceApp)
        }
    }
}
