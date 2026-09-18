package com.olivia.companion

import android.content.Context

internal object DatPermissionCache {
    private const val PREFS = "olivia_dat_permission"
    private const val CAMERA_CONFIRMED = "camera_permission_confirmed"

    fun isCameraConfirmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CAMERA_CONFIRMED, false)

    fun setCameraConfirmed(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CAMERA_CONFIRMED, granted)
            .apply()
    }
}
