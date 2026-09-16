package com.olivia.companion.session

import android.content.Context
import android.net.Uri
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitConfig
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

/** Local developer switch for the DAT emulator. It is not the Olivia offline mock flavor. */
internal object DatMockDeviceBootstrap {
    fun isEnabled(context: Context): Boolean =
        MockDeviceKit.getInstance(context.applicationContext).isEnabled

    fun enablePhoneCamera(context: Context) = enable(context, null)

    fun enable(context: Context, videoFeed: Uri?) {
        val kit = MockDeviceKit.getInstance(context.applicationContext)
        if (!kit.isEnabled) {
            kit.enable(MockDeviceKitConfig(initiallyRegistered = true, initialPermissionsGranted = true))
        }
        kit.permissions.set(Permission.CAMERA, PermissionStatus.Granted)
        val glasses = kit.pairedDevices.filterIsInstance<com.meta.wearable.dat.mockdevice.api.MockGlasses>()
            .firstOrNull()
            ?: kit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
        glasses.powerOn()
        glasses.don()
        glasses.unfold()
        if (videoFeed == null) glasses.services.camera.setCameraFeed(CameraFacing.BACK)
        else glasses.services.camera.setCameraFeed(videoFeed)
    }
}
