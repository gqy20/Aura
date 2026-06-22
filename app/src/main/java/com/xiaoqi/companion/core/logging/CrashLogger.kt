package com.xiaoqi.companion.core.logging

import android.os.Build
import android.util.Log
import java.io.IOException

/**
 * 捕获未处理异常,把 crash 当时的 ring buffer 快照 + stack trace 落盘到 crash dump 文件。
 *
 * 落盘完成后链式调用系统默认 handler,保留"应用已停止"对话框和进程终止行为;
 * 任何 I/O 失败都吞掉,确保不阻塞 crash 流程、不递归触发新异常。
 *
 * dump 文件含 crash 前的全部 ring buffer 日志(已脱敏)—— 这是排查"三天前那个崩溃"的唯一线索。
 */
class CrashLogger private constructor(
    private val provider: LogFileProvider,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        saveDump(t, e)
        // 必须交还系统默认 handler,否则不会弹 crash 对话框、进程也不会正确终止
        previousHandler?.uncaughtException(t, e)
    }

    private fun saveDump(thread: Thread, error: Throwable) {
        try {
            val stackTrace = buildString {
                append("Thread: ").append(thread.name).append('\n')
                append("Device: ").append(Build.MANUFACTURER).append(' ')
                append(Build.MODEL).append(" (").append(Build.VERSION.RELEASE).append(")\n\n")
                append(Log.getStackTraceString(error))
            }
            provider.snapshotForCrash(System.currentTimeMillis(), stackTrace)
        } catch (_: IOException) {
            // crash dump 落盘失败只能放弃,不能让日志系统拖垮 crash 流程
        } catch (_: Throwable) {
            // 防御:getStackTraceString 在某些异常上可能递归
        }
    }

    companion object {
        /**
         * 安装 crash 捕获。返回值仅用于测试,业务代码通常忽略。
         * 重复调用安全:每次都基于"当前 default handler"安装,不会嵌套。
         */
        fun install(provider: LogFileProvider): CrashLogger {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = CrashLogger(provider, previous)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            return handler
        }
    }
}
