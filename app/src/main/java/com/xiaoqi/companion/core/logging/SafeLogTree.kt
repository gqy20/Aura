package com.xiaoqi.companion.core.logging

import android.util.Log
import timber.log.Timber

class SafeDebugTree : Timber.DebugTree()

class SafeReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return
        Log.println(priority, tag ?: LogTags.App, message)
        t?.let { Log.println(priority, tag ?: LogTags.App, Log.getStackTraceString(it)) }
    }
}
