package com.langtutor.android

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.langtutor.android.di.appModule
import com.langtutor.android.notification.NotificationHelper
import com.langtutor.android.worker.ReminderWorker
import com.langtutor.di.androidModule
import com.langtutor.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class LangTutorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LangTutorApp)
            modules(androidModule, commonModule, appModule)
        }
        NotificationHelper.createNotificationChannel(this)
        scheduleReEngagementWorker()
    }

    private fun scheduleReEngagementWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(4, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // KEEP: don't reset the timer if the worker is already scheduled
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
