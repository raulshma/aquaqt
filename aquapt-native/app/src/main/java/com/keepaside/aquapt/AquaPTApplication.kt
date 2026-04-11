package com.keepaside.aquapt

import android.app.Application
import com.keepaside.aquapt.core.di.databaseModule
import com.keepaside.aquapt.core.di.repositoryModule
import com.keepaside.aquapt.core.notifications.ReminderNotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent

class AquaPTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AquaPTApplication)
            modules(databaseModule, repositoryModule)
        }

        val reminderScheduler: ReminderNotificationScheduler =
            KoinJavaComponent.get(ReminderNotificationScheduler::class.java)
        reminderScheduler.start()
    }
}
