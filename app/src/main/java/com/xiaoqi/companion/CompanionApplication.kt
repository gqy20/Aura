package com.xiaoqi.companion

import android.app.Application
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CompanionApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        SystemPersona.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
