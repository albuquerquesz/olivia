package com.olivia.companion.ui.screens

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.olivia.companion.BuildConfig
import com.olivia.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** One pending decoded frame, two GPU textures. No JPEG, Bitmap, disk or per-frame work queue. */
internal class Nv21PreviewView(context: Context) : GLSurfaceView(context) {
    private val renderer = Nv21Renderer()

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Prévia ao vivo do stream da câmera dos óculos"
    }

    fun submit(frame: Frame) {
        renderer.pending.set(PendingFrame(frame, SystemClock.elapsedRealtime()))
        requestRender()
    }

    fun clear() {
        renderer.pending.set(null)
    }

    private data class PendingFrame(val frame: Frame, val submittedAtMs: Long)

    private class Nv21Renderer : Renderer {
        val pending = AtomicReference<PendingFrame?>(null)
        private val textures = IntArray(2)
        private var program = 0
        private var positionLocation = 0
        private var textureLocation = 0
        private var viewWidth = 1
        private var viewHeight = 1
        private var imageWidth = 0
        private var imageHeight = 0
        private var pixels: ByteBuffer = ByteBuffer.allocateDirect(0)
        private val vertices = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val coordinates = floats(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = GLES20.glCreateProgram()
            val vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX)
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { "Preview shader link failed" }
            positionLocation = GLES20.glGetAttribLocation(program, "position")
            textureLocation = GLES20.glGetAttribLocation(program, "textureCoordinate")
            GLES20.glGenTextures(2, textures, 0)
            textures.forEach {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, it)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }
            imageWidth = 0
            imageHeight = 0
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewWidth = width.coerceAtLeast(1)
            viewHeight = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val queued = pending.getAndSet(null)
            val frame = queued?.frame
            if (frame != null) {
                val size = frame.width * frame.height * 3 / 2
                if (frame.bytes.size < size) return
                if (pixels.capacity() < size) pixels = ByteBuffer.allocateDirect(size)
                pixels.clear()
                pixels.put(frame.bytes, 0, size)
                pixels.flip()
                val resized = imageWidth != frame.width || imageHeight != frame.height
                imageWidth = frame.width
                imageHeight = frame.height
                upload(0, imageWidth, imageHeight, GLES20.GL_LUMINANCE, resized)
                pixels.position(imageWidth * imageHeight)
                upload(1, imageWidth / 2, imageHeight / 2, GLES20.GL_LUMINANCE_ALPHA, resized)
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (imageWidth == 0) return
            val imageAspect = imageWidth.toFloat() / imageHeight
            val viewAspect = viewWidth.toFloat() / viewHeight
            val x = maxOf(1f, imageAspect / viewAspect)
            val y = maxOf(1f, viewAspect / imageAspect)
            vertices.clear()
            vertices.put(floatArrayOf(-x, -y, x, -y, -x, y, x, y)).position(0)
            GLES20.glUseProgram(program)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "luma"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "chroma"), 1)
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glEnableVertexAttribArray(textureLocation)
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, coordinates)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            if (BuildConfig.DEBUG && frame != null && frame.stamp.sequence % 24L == 0L) {
                Log.i("OliviaPreview", "draw sequence=${frame.stamp.sequence} " +
                    "submit_to_draw_ms=${SystemClock.elapsedRealtime() - requireNotNull(queued).submittedAtMs}")
            }
        }

        private fun upload(unit: Int, width: Int, height: Int, format: Int, resized: Boolean) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[unit])
            if (resized) GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, width, height,
                0, format, GLES20.GL_UNSIGNED_BYTE, pixels)
            else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height,
                format, GLES20.GL_UNSIGNED_BYTE, pixels)
        }

        private fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, compiled, 0)
            check(compiled[0] != 0) { "Preview shader compilation failed" }
            return id
        }
    }

    private companion object {
        fun floats(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
        const val VERTEX = """
            attribute vec4 position;
            attribute vec2 textureCoordinate;
            varying vec2 uv;
            void main() { gl_Position = position; uv = textureCoordinate; }
        """
        // DAT uses limited-range BT.709. NV21 stores V in red and U in alpha.
        const val FRAGMENT = """
            precision mediump float;
            varying vec2 uv;
            uniform sampler2D luma;
            uniform sampler2D chroma;
            void main() {
                float y = 1.164384 * (texture2D(luma, uv).r - 0.062745);
                vec4 vu = texture2D(chroma, uv);
                float u = vu.a - 0.501961;
                float v = vu.r - 0.501961;
                gl_FragColor = vec4(y + 1.792741*v, y - 0.213249*u - 0.532909*v,
                    y + 2.112402*u, 1.0);
            }
        """
    }
}
