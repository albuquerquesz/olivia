package com.olivia.datdiagnostic

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

/** Standalone DAT probe: one session, one stream, one newest-frame GPU preview, aggregate logs only. */
class DiagnosticActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var preview: Nv21GlView
    private var session: DeviceSession? = null
    private var streamJob: Job? = null
    private var frameCount = 0L
    private var windowStart = 0L
    private var lastArrival = 0L
    private var lastPts: Long? = null

    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }
    private var datPermissionResult: ((PermissionStatus) -> Unit)? = null
    private val datPermission = registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        result.getOrNull()?.let { datPermissionResult?.invoke(it) }
        datPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = Nv21GlView(this)
        status = TextView(this).apply { textSize = 14f; text = "DAT Diagnostic\nparado" }
        val start = Button(this).apply { text = "Iniciar stream DAT"; setOnClickListener { start() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(start)
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    private fun start() {
        bluetoothPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.CAMERA))
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                Wearables.initialize(applicationContext)
                if (Wearables.registrationState.value != com.meta.wearable.dat.core.types.RegistrationState.REGISTERED) {
                    withContext(Dispatchers.Main) { Wearables.startRegistration(this@DiagnosticActivity) }
                    setStatus("Aguardando registro no Meta AI…")
                    return@launch
                }
                val cameraPermission = Wearables.checkPermissionStatus(Permission.CAMERA).getOrNull()
                if (cameraPermission != PermissionStatus.Granted) {
                    withContext(Dispatchers.Main) {
                        datPermissionResult = { result -> if (result == PermissionStatus.Granted) start() else setStatus("DAT câmera negada: $result") }
                        datPermission.launch(Permission.CAMERA)
                    }
                    return@launch
                }
                runStream()
            } catch (error: Exception) {
                Log.w(TAG, "diagnostic_failed type=${error.javaClass.simpleName}")
                setStatus("ERRO ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private suspend fun runStream() {
        streamJob?.cancel()
        session?.stop()
        val created = Wearables.createSession(AutoDeviceSelector()).getOrElse { error("createSession: $it") }
        session = created
        created.start()
        withTimeoutOrNull(15_000) { created.state.first { it == DeviceSessionState.STARTED } }
            ?: error("session não chegou a STARTED")
        val camera = created.addCamera(StreamConfiguration(VideoQuality.MEDIUM, 24, compressVideo = false))
            .getOrElse { error("addCamera: $it") }
        val stream = camera.stream
        windowStart = SystemClock.elapsedRealtime()
        frameCount = 0
        setStatus("Sessão STARTED · stream iniciando · MEDIUM/24 · raw SDK")
        streamJob = lifecycleScope.launch(Dispatchers.Default) {
            stream.videoStream.collect { video ->
                if (video.isCompressed) return@collect
                val frame = copyI420ToNv21(video.width, video.height, video.buffer) ?: return@collect
                preview.submit(DiagnosticFrame(video.width, video.height, frame))
                val now = SystemClock.elapsedRealtime()
                val gap = if (lastArrival == 0L) 0 else now - lastArrival
                val ptsGap = lastPts?.let { (video.presentationTimeUs - it) / 1_000 }
                lastArrival = now
                lastPts = video.presentationTimeUs
                frameCount++
                if (now - windowStart >= 2_000) {
                    val fps = frameCount * 1_000.0 / (now - windowStart)
                    Log.i(TAG, "frame fps=$fps gap_ms=$gap pts_gap_ms=$ptsGap size=${video.width}x${video.height}")
                    setStatus("STREAMING · %.1f fps · %dx%d · gap %d ms · pts %s".format(fps, video.width, video.height, gap, ptsGap ?: "-"))
                    frameCount = 0
                    windowStart = now
                }
            }
        }
        stream.start().onFailure { error("stream.start: $it") }
    }

    private fun setStatus(message: String) { runOnUiThread { status.text = message } }

    private fun copyI420ToNv21(width: Int, height: Int, input: ByteBuffer): ByteArray? {
        val y = width * height
        val c = y / 4
        val size = y + c * 2
        val source = input.duplicate()
        if (width <= 0 || height <= 0 || source.remaining() < size) return null
        val i420 = ByteArray(size)
        source.get(i420)
        val nv21 = ByteArray(size)
        i420.copyInto(nv21, endIndex = y)
        var out = y
        var v = y + c
        repeat(c) { nv21[out++] = i420[v++]; nv21[out++] = i420[y + it] }
        return nv21
    }

    override fun onDestroy() {
        streamJob?.cancel()
        session?.stop()
        preview.clear()
        super.onDestroy()
    }

    private companion object { const val TAG = "DatDiagnostic" }
}
