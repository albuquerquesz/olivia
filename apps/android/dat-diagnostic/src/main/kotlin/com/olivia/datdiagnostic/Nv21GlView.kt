package com.olivia.datdiagnostic

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal data class DiagnosticFrame(val width: Int, val height: Int, val bytes: ByteArray)

/** Minimal YUV preview for the diagnostic APK. It keeps one newest frame and does no bitmap/JPEG work. */
internal class Nv21GlView(context: Context) : GLSurfaceView(context) {
    private val renderer = RendererImpl()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun submit(frame: DiagnosticFrame) {
        renderer.pending.set(frame)
        requestRender()
    }

    fun clear() { renderer.pending.set(null) }

    private class RendererImpl : Renderer {
        val pending = AtomicReference<DiagnosticFrame?>(null)
        private val textures = IntArray(2)
        private var program = 0
        private var width = 0
        private var height = 0
        private var viewWidth = 1
        private var viewHeight = 1
        private var buffer = ByteBuffer.allocateDirect(0)
        private val vertices = direct(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val coordinates = direct(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = GLES20.glCreateProgram()
            val vertex = shader(GLES20.GL_VERTEX_SHADER, VERTEX)
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            GLES20.glGenTextures(2, textures, 0)
            textures.forEach { texture ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            viewWidth = w.coerceAtLeast(1)
            viewHeight = h.coerceAtLeast(1)
            GLES20.glViewport(0, 0, w, h)
        }

        override fun onDrawFrame(gl: GL10?) {
            pending.getAndSet(null)?.let { frame ->
                val size = frame.width * frame.height * 3 / 2
                if (frame.bytes.size < size) return@let
                if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size)
                buffer.clear()
                buffer.put(frame.bytes, 0, size)
                buffer.flip()
                val resized = width != frame.width || height != frame.height
                width = frame.width
                height = frame.height
                upload(0, width, height, GLES20.GL_LUMINANCE, resized)
                buffer.position(width * height)
                upload(1, width / 2, height / 2, GLES20.GL_LUMINANCE_ALPHA, resized)
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (width == 0) return
            val imageAspect = width.toFloat() / height
            val viewAspect = viewWidth.toFloat() / viewHeight
            val x = maxOf(1f, imageAspect / viewAspect)
            val y = maxOf(1f, viewAspect / imageAspect)
            vertices.clear()
            vertices.put(floatArrayOf(-x, -y, x, -y, -x, y, x, y))
            vertices.position(0)
            GLES20.glUseProgram(program)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "luma"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "chroma"), 1)
            val position = GLES20.glGetAttribLocation(program, "position")
            val coordinate = GLES20.glGetAttribLocation(program, "textureCoordinate")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glEnableVertexAttribArray(coordinate)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
            GLES20.glVertexAttribPointer(coordinate, 2, GLES20.GL_FLOAT, false, 0, coordinates)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun upload(unit: Int, w: Int, h: Int, format: Int, resized: Boolean) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[unit])
            if (resized) GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, w, h, 0, format, GLES20.GL_UNSIGNED_BYTE, buffer)
            else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h, format, GLES20.GL_UNSIGNED_BYTE, buffer)
        }

        private fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            return id
        }

        private companion object {
            fun direct(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values).position(0) }
            const val VERTEX = "attribute vec4 position; attribute vec2 textureCoordinate; varying vec2 uv; void main(){gl_Position=position;uv=textureCoordinate;}"
            const val FRAGMENT = "precision mediump float; uniform sampler2D luma; uniform sampler2D chroma; varying vec2 uv; void main(){float y=texture2D(luma,uv).r; vec2 c=texture2D(chroma,uv).ra-vec2(.5,.5); gl_FragColor=vec4(y+1.402*c.x,y-.344*c.y- .714*c.x,y+1.772*c.y,1.0);}"
        }
    }
}
