package com.xiaoqi.companion.core.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ContextPermissionReader {
    fun hasCoarseLocation(): Boolean
    fun hasFineLocation(): Boolean
    fun hasPostNotifications(): Boolean
}

class AndroidContextPermissionReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ContextPermissionReader {

    override fun hasCoarseLocation(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun hasFineLocation(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun hasPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
