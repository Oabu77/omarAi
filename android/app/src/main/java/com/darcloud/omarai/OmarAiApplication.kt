package com.darcloud.omarai

import android.app.Application
import com.darcloud.omarai.data.api.InMemorySessionTokenProvider
import com.darcloud.omarai.data.api.OmarApiClient
import com.darcloud.omarai.data.billing.BillingManager
import com.darcloud.omarai.data.local.OmarDatabase
import com.darcloud.omarai.data.local.OmarRepository
import com.darcloud.omarai.data.local.UserPreferences

class OmarAiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.billingManager.start()
    }
}

class AppContainer(application: Application) {
    val preferences = UserPreferences(application)
    val sessionTokens = InMemorySessionTokenProvider()
    val apiClient = OmarApiClient(sessionTokens)
    val database = OmarDatabase.get(application)
    val repository = OmarRepository(application, database, apiClient)
    val billingManager = BillingManager(application, apiClient)
}
