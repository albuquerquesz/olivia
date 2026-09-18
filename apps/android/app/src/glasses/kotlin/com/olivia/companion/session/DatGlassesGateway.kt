package com.olivia.companion.session

import android.content.Context
import android.net.Uri
import android.util.Log
import com.olivia.companion.BuildConfig
import com.olivia.companion.VariantRuntimeProvider
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.camera.Stream
import com.olivia.core.CaptureHeartbeat
import com.olivia.core.CaptureId
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
import com.olivia.core.SourceBacklog
import kotlinx.coroutines.channels.BufferOverflow
import com.olivia.core.FrameStamp
import com.olivia.core.GlassesConnection
import com.olivia.core.GlassesGateway
import com.olivia.core.OliviaClock
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred

/**
 * DAT-only boundary. It never records native media and never keeps a frame after emitting it.
 * DAT decodes HEVC internally; raw I420 is converted to NV21 once for preview and analysis.
 * Compressed packets, if supplied unexpectedly, remain confined to the fallback decoder.
 */
internal class DatGlassesGateway(
    private val context: Context,
    private val clock: OliviaClock,
    private val scope: CoroutineScope,
    private val keepAliveInForeground: Boolean = true,
    private val forceMockDeviceKit: Boolean = false,
    private val mockVideoUri: Uri? = null,
    private val diagnosticCompressedVideo: Boolean = false,
    private val diagnosticLowBandwidth: Boolean = false,
) : GlassesGateway, AutoCloseable {
    private val _connection = MutableStateFlow(GlassesConnection.UNAVAILABLE)
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _heartbeat = MutableStateFlow<CaptureHeartbeat?>(null)
    @Volatile private var capture: CaptureId? = null
    private val sourceBacklog = SourceBacklog()
    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var stream: Stream? = null
    private var decoder: HevcFrameDecoder? = null
    private var streamJob: Job? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null
    private var streamStateJob: Job? = null
    private var streamErrorJob: Job? = null
    private var sequence = 0L
    private var firstFrameLogged = false
    private var lastFrameArrivalMs: Long? = null
    private var lastPresentationTimeUs: Long? = null
    // Relative media/phone clock drift from the first decoded frame. This cannot measure a fixed
    // upstream delay or prove where buffering occurred; packet/codec/preview timings isolate it.
    private var referencePresentationTimeUs: Long? = null
    private var referenceArrivalMs: Long? = null
    internal var lastStartError: Throwable? = null
        private set

    override val connection: Flow<GlassesConnection> = _connection.asStateFlow()
    override val frames: Flow<Frame> = _frames.asSharedFlow()
    override val heartbeat: StateFlow<CaptureHeartbeat?> = _heartbeat.asStateFlow()

    override suspend fun startCapture(capture: CaptureId) {
        stopResources()
        lastStartError = null
        this.capture = capture
        sequence = 0
        firstFrameLogged = false
        lastFrameArrivalMs = null
        lastPresentationTimeUs = null
        referencePresentationTimeUs = null
        referenceArrivalMs = null
        sourceBacklog.reset()
        _heartbeat.value = null
        try {
            // MockDeviceKit replaces device transport, not the SDK singleton. Initialize the same
            // Wearables runtime in both paths so session state and AutoDeviceSelector can update.
            ensureWearablesInitialized()
            if (forceMockDeviceKit || BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT) {
                DatMockDeviceBootstrap.enable(context, mockVideoUri)
            }
            if (DatMockDeviceBootstrap.isEnabled(context)) {
                // Pairing in MockDeviceKit is asynchronous. Do not ask AutoDeviceSelector for a
                // session before its simulated device is visible to Wearables.
                withTimeout(MOCK_DEVICE_READY_TIMEOUT_MS) {
                    Wearables.devices.filter { it.isNotEmpty() }.first()
                }
            }
            logDatAvailability()
            awaitDatDeviceReady()
            logDatAvailability()
            requireCameraPermission()
            awaitDatDeviceReady()
            val deviceSession = Wearables.createSession(AutoDeviceSelector())
                .onFailure { error, _ -> Log.w(TAG, "DAT create_error=${error.name}") }
                .getOrElse { throw IllegalStateException("DAT createSession failed", it) }
            session = deviceSession
            val ready = CompletableDeferred<Unit>()
            sessionStateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                deviceSession.state.collect { state ->
                    Log.i(TAG, "DAT session=$state")
                    if (state == DeviceSessionState.STARTED) ready.complete(Unit)
                    if (state == DeviceSessionState.STOPPED) {
                        ready.completeExceptionally(IllegalStateException("DAT session stopped before ready"))
                        _connection.value = GlassesConnection.UNAVAILABLE
                    }
                }
            }
            sessionErrorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                deviceSession.errors.collect { error ->
                    Log.w(TAG, "DAT session_error=${error.name}")
                    ready.completeExceptionally(IllegalStateException("DAT session: ${error.name}"))
                    _connection.value = GlassesConnection.UNAVAILABLE
                }
            }
            deviceSession.start()
            withTimeoutOrNull(SESSION_STARTED_TIMEOUT_MS) { ready.await() }
                ?: throw IllegalStateException("DAT session start timed out")
            if (keepAliveInForeground) DatStreamingService.start(context)
            val attachedCamera = deviceSession.addCamera(
                StreamConfiguration(
                    videoQuality = if (BuildConfig.DEBUG && diagnosticLowBandwidth) VideoQuality.LOW else VideoQuality.MEDIUM,
                    frameRate = if (BuildConfig.DEBUG && diagnosticLowBandwidth) 7 else DAT_FRAME_RATE,
                    // DAT performs one software decode; preview and ROI share its raw output.
                    compressVideo = BuildConfig.DEBUG && diagnosticCompressedVideo,
                ),
            ).onFailure { error, _ -> Log.w(TAG, "DAT camera_error=${error.name}") }
                .getOrElse { throw IllegalStateException("DAT camera failed", it) }
            camera = attachedCamera
            val activeStream = attachedCamera.stream
            stream = activeStream
            streamStateJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                activeStream.state.collect { Log.i(TAG, "DAT stream=$it") }
            }
            streamErrorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                activeStream.errorStream.collect {
                    Log.w(TAG, "DAT stream_error=${it.name}")
                    _connection.value = GlassesConnection.UNAVAILABLE
                }
            }
            observeFrames(activeStream, capture)
            activeStream.start().onFailure { error, _ -> Log.w(TAG, "DAT start_stream_error=${error.name}") }
                .getOrElse { throw IllegalStateException("DAT stream failed", it) }
            _connection.value = GlassesConnection.CONNECTED
            // STREAMING can precede the first HEVC access unit by several seconds on physical
            // glasses. Complete startup only when a decoded frame proves capture is ready.
            withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                _heartbeat.filter { it?.capture == capture }.first()
            } ?: throw IllegalStateException("DAT first decoded frame timed out")
        } catch (error: Throwable) {
            lastStartError = error
            Log.w(TAG, "DAT start failed: ${error::class.java.simpleName}: ${error.message}")
            _connection.value = GlassesConnection.UNAVAILABLE
            stopResources()
            this.capture = null
            throw error
        }
    }

    override suspend fun pauseCapture() {
        capture = null
        streamJob?.cancel()
        streamJob = null
        decoder?.close()
        decoder = null
        // DAT closes a Stream after stop() and does not support starting that same instance
        // again. Keep the capability alive while the app is paused; cancelling this collector
        // prevents heartbeats/frames from crossing the boundary, and resume simply reattaches it.
        _heartbeat.value = null
    }

    override suspend fun resumeCapture(capture: CaptureId) {
        val activeStream = stream ?: run { startCapture(capture); return }
        this.capture = capture
        sequence = 0
        firstFrameLogged = false
        lastFrameArrivalMs = null
        lastPresentationTimeUs = null
        referencePresentationTimeUs = null
        referenceArrivalMs = null
        sourceBacklog.reset()
        _heartbeat.value = null
        _connection.value = GlassesConnection.CONNECTED
        observeFrames(activeStream, capture)
    }

    override suspend fun stopCapture() {
        stopResources()
        capture = null
        _heartbeat.value = null
        _connection.value = GlassesConnection.DISCONNECTED
    }

    override fun close() { stopResources() }

    private fun observeFrames(activeStream: Stream, activeCapture: CaptureId) {
        streamJob?.cancel()
        val observeStartedMs = android.os.SystemClock.elapsedRealtime()
        var packetCount = 0L
        var previousPacketMs: Long? = null
        var previousPacketPts: Long? = null
        streamJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            activeStream.videoStream.collect { videoFrame ->
                if (capture != activeCapture) return@collect
                val arrivedMs = android.os.SystemClock.elapsedRealtime()
                if (BuildConfig.DEBUG) Log.i(TAG,
                    "packet_arrival packet=${packetCount++} since_observe_ms=${arrivedMs - observeStartedMs} " +
                        "gap_ms=${previousPacketMs?.let { arrivedMs - it }} " +
                        "pts_gap_ms=${previousPacketPts?.let { (videoFrame.presentationTimeUs - it) / 1_000 }} " +
                        "compressed=${videoFrame.isCompressed} config=${videoFrame.isCodecConfig} " +
                        "width=${videoFrame.width} height=${videoFrame.height} bytes=${videoFrame.buffer.remaining()}")
                previousPacketMs = arrivedMs
                previousPacketPts = videoFrame.presentationTimeUs
                if (!videoFrame.isCompressed) {
                    emitRawFrame(activeCapture, videoFrame.width, videoFrame.height, videoFrame.buffer, videoFrame.presentationTimeUs)
                    return@collect
                }
                val activeDecoder = decoder ?: HevcFrameDecoder(
                    videoFrame.width,
                    videoFrame.height,
                    onFrame = { width, height, nv21, presentationTimeUs ->
                        this@DatGlassesGateway.capture?.let {
                            if (it == activeCapture) emitDecodedFrame(it, width, height, nv21, presentationTimeUs)
                        }
                    },
                    onUnavailable = { _connection.value = GlassesConnection.UNAVAILABLE },
                ).also { decoder = it }
                activeDecoder.enqueue(videoFrame.buffer, videoFrame.presentationTimeUs, videoFrame.isCodecConfig)
            }
        }
    }

    private fun stopResources() {
        sessionStateJob?.cancel()
        sessionErrorJob?.cancel()
        streamStateJob?.cancel()
        streamErrorJob?.cancel()
        sessionStateJob = null
        sessionErrorJob = null
        streamStateJob = null
        streamErrorJob = null
        streamJob?.cancel()
        streamJob = null
        decoder?.close()
        decoder = null
        runCatching { stream?.stop() }
        runCatching { camera?.stop() }
        runCatching { session?.stop() }
        if (keepAliveInForeground) DatStreamingService.stop(context)
        stream = null
        camera = null
        session = null
    }

    private fun emitDecodedFrame(
        capture: CaptureId,
        width: Int,
        height: Int,
        nv21: ByteArray,
        presentationTimeUs: Long? = null,
    ) {
        if (this.capture != capture) return
        val now = clock.now()
        val backlogMs = if (forceMockDeviceKit || BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT) 0L
            else presentationTimeUs?.let { sourceBacklog.observe(it, now.monotonicMs) } ?: 0L
        logFirstFrame(width, height, FrameFormat.NV21)
        // Diagnostic-only: sequence, arrival gap and encoder timestamp gap, no pixels. Comparing
        // wall_gap_ms (phone-side arrival) to pts_gap_ms (glasses-side capture spacing) tells apart
        // an uneven capture/encode on the glasses from buffering in our own decode/transport path.
        val wallGapMs = lastFrameArrivalMs?.let { now.monotonicMs - it }
        lastFrameArrivalMs = now.monotonicMs
        val ptsGapMs = presentationTimeUs?.let { pts -> lastPresentationTimeUs?.let { (pts - it) / 1_000 } }
        if (presentationTimeUs != null) lastPresentationTimeUs = presentationTimeUs
        // capture_lag_ms: wall-clock elapsed since the take's first frame minus encoder-clock
        // elapsed since that same frame. Stays near a constant if the pipeline just has a fixed
        // startup offset; keeps growing if something upstream is queueing a backlog over time.
        val captureLagMs = presentationTimeUs?.let { pts ->
            if (referencePresentationTimeUs == null) {
                referencePresentationTimeUs = pts
                referenceArrivalMs = now.monotonicMs
                0L
            } else {
                val wallElapsedMs = now.monotonicMs - requireNotNull(referenceArrivalMs)
                val ptsElapsedMs = (pts - requireNotNull(referencePresentationTimeUs)) / 1_000
                wallElapsedMs - ptsElapsedMs
            }
        }
        Log.i(
            TAG,
            "frame_arrival sequence=$sequence wall_gap_ms=$wallGapMs pts_gap_ms=$ptsGapMs " +
                "capture_lag_ms=$captureLagMs backlog_ms=$backlogMs width=$width height=$height",
        )
        _heartbeat.value = CaptureHeartbeat(capture, now.monotonicMs)
        _frames.tryEmit(
            Frame(
                stamp = FrameStamp(capture, sequence++, now, sourceBacklogMs = backlogMs),
                width = width,
                height = height,
                format = FrameFormat.NV21,
                origin = FrameOrigin.DAT,
                bytes = nv21,
            ),
        )
    }

    /** Aggregate-only evidence for the camera checkpoint; never logs frame bytes or image data. */
    private fun logFirstFrame(width: Int, height: Int, format: FrameFormat) {
        if (firstFrameLogged) return
        firstFrameLogged = true
        Log.i(TAG, "first_frame origin=DAT format=$format width=$width height=$height")
    }

    /** DAT raw Android frames are tightly packed I420 (Y, U, V); domain frames use NV21. */
    private fun emitRawFrame(capture: CaptureId, width: Int, height: Int, buffer: ByteBuffer, presentationTimeUs: Long) {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) return
        val ySize = width * height
        val chromaSize = ySize / 4
        val expected = ySize + chromaSize * 2
        val i420 = buffer.duplicate().let { source ->
            if (source.remaining() < expected) return
            ByteArray(expected).also(source::get)
        }
        try {
            val nv21 = ByteArray(expected)
            i420.copyInto(nv21, endIndex = ySize)
            var destination = ySize
            var v = ySize + chromaSize
            repeat(chromaSize) {
                nv21[destination++] = i420[v++]
                nv21[destination++] = i420[ySize + it]
            }
            emitDecodedFrame(capture, width, height, nv21, presentationTimeUs)
        } finally {
            i420.fill(0)
        }
    }

    /**
     * DAT owns a process-wide SDK singleton. A new gateway can be created after a configuration
     * change, activity recreation, or a failed start while that singleton is still alive. Treat
     * the SDK's explicit "already initialized" result as success; only a different initialization
     * error should prevent a take from starting.
     */
    private suspend fun ensureWearablesInitialized() {
        wearablesInitializationMutex.withLock {
            if (processWearablesInitialized) return
            val result = Wearables.initialize(context)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                if (!error.isAlreadyInitialized()) {
                    throw IllegalStateException("DAT initialization unavailable", error)
                }
                Log.i(TAG, "DAT SDK already initialized; reusing process singleton")
            }
            processWearablesInitialized = true
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

    /** Aggregate-only DAT preflight; no device identifiers, credentials or media are logged. */
    private fun logDatAvailability() {
        val metadata = Wearables.devicesMetadata.values.mapNotNull { flow ->
            runCatching { flow.value }.getOrNull()
        }
        val states = metadata.joinToString(separator = ",") { device ->
            "${device.linkState}/${device.compatibility}"
        }.ifBlank { "none" }
        Log.i(
            TAG,
            "DAT preflight registration=${Wearables.registrationState.value} " +
                "dev_mode=${Wearables.isDevMode} devices=${Wearables.devices.value.size} " +
                "metadata=$states",
        )
    }

    /**
     * Registration exposes a device before its DAT link is usable. The first real command can
     * therefore observe CONNECTING while AutoDeviceSelector still has no eligible device.
     * Wait for the same connected/compatible state that the selector requires before creating
     * the session; this keeps the voice command and the camera handshake in one operation.
     */
    private suspend fun awaitDatDeviceReady() {
        if (forceMockDeviceKit || BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT) return
        val connected = withTimeoutOrNull(DAT_DEVICE_READY_TIMEOUT_MS) {
            while (!hasConnectedCompatibleDevice()) {
                delay(DAT_DEVICE_POLL_MS)
            }
            true
        }
        if (connected != true) throw IllegalStateException("DAT data connection timed out")
    }

    private fun hasConnectedCompatibleDevice(): Boolean =
        Wearables.devicesMetadata.values.any { metadataFlow ->
            val metadata = runCatching { metadataFlow.value }.getOrNull() ?: return@any false
            metadata.linkState == LinkState.CONNECTED &&
                metadata.compatibility == DeviceCompatibility.COMPATIBLE
        }

    private suspend fun requireCameraPermission() {
        if (forceMockDeviceKit || BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT) return
        VariantRuntimeProvider.ensureCameraPermission(context)
    }

    private companion object {
        const val TAG = "OliviaDatGateway"
        // MEDIUM/24 follows CameraAccess; the AI sampler is independent of camera cadence.
        const val DAT_FRAME_RATE = 24
        const val MOCK_DEVICE_READY_TIMEOUT_MS = 3_000L
        // Returning from Meta AI can briefly hand the BLE data link back through CONNECTING or
        // DISCONNECTED while the permission manifest finishes syncing to the glasses. Fail fast
        // enough for the hands-free flow to remain understandable when the glasses service is down.
        const val DAT_DEVICE_READY_TIMEOUT_MS = 15_000L
        const val DAT_DEVICE_POLL_MS = 100L
        const val SESSION_STARTED_TIMEOUT_MS = 8_000L
        const val FIRST_FRAME_TIMEOUT_MS = 25_000L
        private val wearablesInitializationMutex = Mutex()
        private var processWearablesInitialized = false
    }
}
