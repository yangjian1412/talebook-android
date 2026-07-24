package com.talebook.app

import android.app.Application
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.talebook.app.data.api.RetrofitClient

class TalebookApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.initialize(this)
        applicationScope.launch {
            val repo = SettingsRepository(this@TalebookApp)
            val url = repo.serverUrl.first()
            RetrofitClient.updateBaseUrl(url)
        }
    }
}
