package com.xiaoqi.companion

import android.app.Application
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CompanionApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AppLogger.initialize(BuildConfig.DEBUG)
        SystemPersona.init(this)
    }
}
