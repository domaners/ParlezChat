package com.langtutor.android

import android.app.Application
import com.langtutor.android.di.appModule
import com.langtutor.di.androidModule
import com.langtutor.di.commonModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LangTutorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LangTutorApp)
            modules(androidModule, commonModule, appModule)
        }
    }
}
