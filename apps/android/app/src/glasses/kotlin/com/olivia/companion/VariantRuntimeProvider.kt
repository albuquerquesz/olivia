package com.olivia.companion

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.olivia.companion.ai.FirebaseVisualAnalyzerFactory
import com.olivia.companion.session.BackendRuntimeProvider
import com.olivia.companion.session.DatGlassesGateway
import com.olivia.companion.session.DatMockDeviceBootstrap
import com.olivia.companion.session.LiveSessionRuntime
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.UnavailableSessionRuntime
import com.olivia.core.SystemOliviaClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object VariantRuntimeProvider : BackendRuntimeProvider {
    private var cameraPermissionLauncher: ActivityResultLauncher<Permission>? = null
    private val cameraPermissionMutex = Mutex()
    private var pendingCameraPermission: CompletableDeferred<PermissionStatus>? = null

    override fun create(context: Context): SessionRuntime {
        val clock = SystemOliviaClock()
        val analyzer = FirebaseVisualAnalyzerFactory.create(context, clock)
            .getOrElse { return UnavailableSessionRuntime() }
        return LiveSessionRuntime(
            context = context,
            gateway = DatGlassesGateway(
                context,
                clock,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ),
            analyzer = analyzer,
            clock = clock,
        )
    }

    override fun startGlassesRegistration(activity: Activity): Result<Unit> = runCatching {
        val initialization = Wearables.initialize(activity.applicationContext)
        if (initialization.isFailure && !initialization.exceptionOrNull().isAlreadyInitialized()) {
            throw IllegalStateException("DAT initialization unavailable", initialization.exceptionOrNull())
        }
        if (Wearables.registrationState.value == RegistrationState.REGISTERED) {
            val componentActivity = activity as? ComponentActivity
                ?: throw IllegalStateException("DAT camera permission requires ComponentActivity")
            componentActivity.lifecycleScope.launch {
                try {
                    ensureCameraPermission(componentActivity)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "DAT connection action failed type=${error.javaClass.simpleName}")
                }
            }
        } else {
            Wearables.startRegistration(activity)
        }
    }

    override fun registerGlassesPermissionLauncher(activity: ComponentActivity): Result<Unit> =
        runCatching {
            cameraPermissionLauncher?.unregister()
            cameraPermissionLauncher = activity.registerForActivityResult(
                Wearables.RequestPermissionContract(),
            ) { result ->
                val status = result.getOrNull()
                if (status != null) pendingCameraPermission?.complete(status)
                else pendingCameraPermission?.completeExceptionally(
                    IllegalStateException("DAT permission request failed: ${result.errorOrNull()}"),
                )
                when (status) {
                    PermissionStatus.Granted -> {
                        DatPermissionCache.setCameraConfirmed(activity, granted = true)
                        Log.i(TAG, "DAT camera permission result=GRANTED")
                    }
                    PermissionStatus.Denied -> {
                        DatPermissionCache.setCameraConfirmed(activity, granted = false)
                        Log.w(TAG, "DAT camera permission result=DENIED")
                    }
                    null -> Log.w(
                        TAG,
                        "DAT camera permission result=ERROR type=${result.errorOrNull()?.javaClass?.simpleName}",
                    )
                }
            }
        }

    /** One official request, awaited by the original start command across the Meta AI round-trip. */
    internal suspend fun ensureCameraPermission(context: Context) = cameraPermissionMutex.withLock {
        val connected = withTimeoutOrNull(15_000) {
            while (Wearables.devicesMetadata.values.none {
                it.value.linkState == LinkState.CONNECTED &&
                    it.value.compatibility == DeviceCompatibility.COMPATIBLE
            }) delay(100)
            true
        }
        check(connected == true) { "DAT data connection timed out before permission query" }
        val result = withTimeoutOrNull(5_000) { Wearables.checkPermissionStatus(Permission.CAMERA) }
        val status = result?.getOrNull()
        Log.i(TAG, "DAT camera query=${status?.javaClass?.simpleName ?: "UNAVAILABLE"}")
        when (status) {
            PermissionStatus.Granted -> DatPermissionCache.setCameraConfirmed(context, true)
            PermissionStatus.Denied -> {
                DatPermissionCache.setCameraConfirmed(context, false)
                val response = CompletableDeferred<PermissionStatus>()
                try {
                    withContext(Dispatchers.Main.immediate) {
                        check(pendingCameraPermission == null) { "DAT permission request already pending" }
                        val launcher = checkNotNull(cameraPermissionLauncher) { "DAT permission activity unavailable" }
                        pendingCameraPermission = response
                        launcher.launch(Permission.CAMERA)
                    }
                    val granted = withTimeoutOrNull(90_000) { response.await() }
                        ?: throw IllegalStateException("DAT camera permission request timed out")
                    check(granted == PermissionStatus.Granted) { "DAT camera permission denied" }
                    DatPermissionCache.setCameraConfirmed(context, true)
                } finally {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                        if (pendingCameraPermission === response) pendingCameraPermission = null
                    }
                    response.cancel()
                }
            }
            null -> {
                // A disconnected link or query error must never revoke an actual camera grant.
                Log.w(TAG, "DAT permission query unavailable error=${result?.errorOrNull()}")
                throw IllegalStateException("DAT camera permission query unavailable")
            }
        }
    }

    private fun Throwable?.isAlreadyInitialized(): Boolean {
        var current = this
        while (current != null) {
            if (current.message?.contains("already initialized", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    private const val TAG = "OliviaDatPermission"
}
