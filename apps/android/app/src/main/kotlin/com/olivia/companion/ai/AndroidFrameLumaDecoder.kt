package com.olivia.companion.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.olivia.core.Frame
import com.olivia.core.FrameFormat
import com.olivia.core.FrameLumaDecoder
import com.olivia.core.LumaFrame

/** Android-only decoder. It keeps an ephemeral grayscale copy in memory for local analysis. */
class AndroidFrameLumaDecoder : FrameLumaDecoder {
    override fun decode(frame: Frame): LumaFrame? {
        if (frame.format != FrameFormat.JPEG) return com.olivia.core.LumaFrameDecoder.decode(frame)
        val bitmap = BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val luma = ByteArray(pixels.size)
            for (index in pixels.indices) {
                val color = pixels[index]
                luma[index] = ((77 * ((color shr 16) and 0xff) + 150 * ((color shr 8) and 0xff) + 29 * (color and 0xff)) ushr 8).toByte()
            }
            LumaFrame(bitmap.width, bitmap.height, luma)
        } finally {
            bitmap.recycle()
        }
    }
}
