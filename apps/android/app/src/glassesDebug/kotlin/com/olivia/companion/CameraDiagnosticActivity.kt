package com.olivia.companion

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.olivia.companion.session.DatGlassesGateway
import com.olivia.companion.ui.screens.Nv21PreviewView
import com.olivia.core.CaptureId
import com.olivia.core.SessionId
import com.olivia.core.SystemOliviaClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debug-only camera control: no session runtime, analyzer, microphone, or audio engine. */
class CameraDiagnosticActivity : ComponentActivity() {
    private lateinit var preview: Nv21PreviewView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VariantRuntimeProvider.registerGlassesPermissionLauncher(this).getOrThrow()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = Nv21PreviewView(this)
        status = TextView(this).apply { text = "Diagnóstico DAT · conectando câmera" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                coroutineScope {
                    val clock = SystemOliviaClock()
                    val gateway = DatGlassesGateway(
                        this@CameraDiagnosticActivity, clock, CoroutineScope(coroutineContext + Dispatchers.Default),
                        keepAliveInForeground = false,
                        diagnosticCompressedVideo = intent.getBooleanExtra("compressed", false),
                        diagnosticLowBandwidth = intent.getBooleanExtra("low_bandwidth", false),
                    )
                    Log.i(TAG, "camera_only start compressed=${intent.getBooleanExtra("compressed", false)} low_bandwidth=${intent.getBooleanExtra("low_bandwidth", false)}")
                    try {
                        launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                            var count = 0L
                            var windowStart = clock.now().monotonicMs
                            gateway.frames.collect { frame ->
                                preview.submit(frame)
                                count++
                                val elapsed = clock.now().monotonicMs - windowStart
                                if (elapsed >= 2_000) {
                                    val fps = count * 1_000.0 / elapsed
                                    Log.i(TAG, "camera_only fps=$fps backlog_ms=${frame.stamp.sourceBacklogMs}")
                                    withContext(Dispatchers.Main) {
                                        status.text = "DAT isolado · %.1f fps · %d×%d · backlog relativo %d ms".format(
                                            fps, frame.width, frame.height, frame.stamp.sourceBacklogMs,
                                        )
                                    }
                                    count = 0
                                    windowStart = clock.now().monotonicMs
                                }
                            }
                        }
                        withContext(Dispatchers.Default) {
                            gateway.startCapture(CaptureId(SessionId("camera-diagnostic"), 1))
                        }
                        awaitCancellation()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "camera_only failed type=${error.javaClass.simpleName}")
                        status.text = "Câmera indisponível · ${error.javaClass.simpleName} · reabra para tentar novamente"
                    } finally {
                        coroutineContext.cancelChildren()
                        gateway.close()
                        preview.clear()
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); preview.onResume() }
    override fun onPause() { preview.clear(); preview.onPause(); super.onPause() }

    private companion object { const val TAG = "OliviaCameraDiagnostic" }
}
