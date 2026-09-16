package com.olivia.companion.session

import android.content.Context
import android.net.Uri
import android.util.Log
import com.olivia.companion.BuildConfig
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.Stream
import com.olivia.core.CaptureHeartbeat
import com.olivia.core.CaptureId
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameOrigin
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
import kotlinx.coroutines.withTimeout

/**
 * DAT-only boundary. It never records native media and never keeps a frame after emitting it.
 * DAT's documented compressed HEVC path is decoded locally into NV21 before crossing the domain
 * boundary. Codec configuration packets and encoded buffers never leave this adapter.
 */
internal class DatGlassesGateway(
    private val context: Context,
    private val clock: OliviaClock,
    private val scope: CoroutineScope,
    private val keepAliveInForeground: Boolean = true,
    private val forceMockDeviceKit: Boolean = false,
    private val mockVideoUri: Uri? = null,
) : GlassesGateway, AutoCloseable {
    private val _connection = MutableStateFlow(GlassesConnection.UNAVAILABLE)
    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 1)
    private val _heartbeat = MutableStateFlow<CaptureHeartbeat?>(null)
    private var capture: CaptureId? = null
    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var stream: Stream? = null
    private var decoder: HevcFrameDecoder? = null
    private var streamJob: Job? = null
    private var sequence = 0L
    private var wearablesInitialized = false
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
        _heartbeat.value = null
        try {
            // MockDeviceKit replaces device transport, not the SDK singleton. Initialize the same
            // Wearables runtime in both paths so session state and AutoDeviceSelector can update.
            if (!wearablesInitialized) {
                Wearables.initialize(context).getOrElse { throw IllegalStateException("DAT initialization unavailable") }
                wearablesInitialized = true
            }
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
            val deviceSession = Wearables.createSession(AutoDeviceSelector())
                .getOrElse { throw IllegalStateException("DAT glasses unavailable") }
            session = deviceSession
            deviceSession.start()
            withTimeout(SESSION_STARTED_TIMEOUT_MS) {
                deviceSession.state.filter { it == DeviceSessionState.STARTED }.first()
            }
            if (keepAliveInForeground) DatStreamingService.start(context)
            val useRawMockFrames = forceMockDeviceKit || BuildConfig.MWDAT_USE_MOCK_DEVICE_KIT
            val attachedCamera = deviceSession.addCamera(
                StreamConfiguration(
                    videoQuality = VideoQuality.HIGH,
                    frameRate = DAT_FRAME_RATE,
                    // MockDeviceKit's raw I420 path avoids the device-specific HEVC/ImageReader
                    // crash seen on Moto G60. Physical DAT keeps the production HEVC path.
                    compressVideo = !useRawMockFrames,
                ),
            ).getOrElse { throw IllegalStateException("DAT camera unavailable") }
            camera = attachedCamera
            val activeStream = attachedCamera.stream
            stream = activeStream
            activeStream.start().getOrElse { throw IllegalStateException("DAT stream unavailable") }
            _connection.value = GlassesConnection.CONNECTED
            observeFrames(activeStream, capture)
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
        streamJob?.cancel()
        streamJob = null
        // DAT closes a Stream after stop() and does not support starting that same instance
        // again. Keep the capability alive while the app is paused; cancelling this collector
        // prevents heartbeats/frames from crossing the boundary, and resume simply reattaches it.
        _heartbeat.value = null
    }

    override suspend fun resumeCapture(capture: CaptureId) {
        val activeStream = stream ?: run { startCapture(capture); return }
        this.capture = capture
        sequence = 0
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
        streamJob = scope.launch {
            activeStream.videoStream.collect { videoFrame ->
                if (capture != activeCapture) return@collect
                if (!videoFrame.isCompressed) {
                    emitRawFrame(activeCapture, videoFrame.width, videoFrame.height, videoFrame.buffer)
                    return@collect
                }
                val activeDecoder = decoder ?: HevcFrameDecoder(
                    videoFrame.width,
                    videoFrame.height,
                    onFrame = { width, height, nv21 -> emitDecodedFrame(activeCapture, width, height, nv21) },
                    onUnavailable = { _connection.value = GlassesConnection.UNAVAILABLE },
                ).also { decoder = it }
                activeDecoder.enqueue(videoFrame.buffer, videoFrame.presentationTimeUs, videoFrame.isCodecConfig)
            }
        }
    }

    private fun stopResources() {
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

    private fun emitDecodedFrame(capture: CaptureId, width: Int, height: Int, nv21: ByteArray) {
        if (this.capture != capture) return
        val now = clock.now()
        _heartbeat.value = CaptureHeartbeat(capture, now.monotonicMs)
        _frames.tryEmit(
            Frame(
                stamp = FrameStamp(capture, sequence++, now),
                width = width,
                height = height,
                format = FrameFormat.NV21,
                origin = FrameOrigin.DAT,
                bytes = nv21,
            ),
        )
    }

    /** DAT raw Android frames are tightly packed I420 (Y, U, V); domain frames use NV21. */
    private fun emitRawFrame(capture: CaptureId, width: Int, height: Int, buffer: ByteBuffer) {
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
            emitDecodedFrame(capture, width, height, nv21)
        } finally {
            i420.fill(0)
        }
    }

    private companion object {
        const val TAG = "OliviaDatGateway"
        const val DAT_FRAME_RATE = 2
        const val MOCK_DEVICE_READY_TIMEOUT_MS = 3_000L
        const val SESSION_STARTED_TIMEOUT_MS = 8_000L
    }
}
