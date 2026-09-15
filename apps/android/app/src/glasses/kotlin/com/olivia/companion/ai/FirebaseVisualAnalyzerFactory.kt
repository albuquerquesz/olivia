package com.olivia.companion.ai

import android.content.Context
import com.google.firebase.FirebaseApp
import com.olivia.core.FrameAnalyzer
import com.olivia.core.GeminiFrameAnalyzer
import com.olivia.core.OliviaClock

/**
 * Creates the real visual-analysis boundary only in the glasses flavor.
 *
 * Firebase configuration is deliberately discovered from the Android environment rather than
 * copied into source code. This factory is not a DAT adapter: until INT-00 supplies one, the
 * runtime must keep reporting the physical-glasses path as unavailable.
 */
object FirebaseVisualAnalyzerFactory {
    fun create(context: Context, clock: OliviaClock): Result<FrameAnalyzer> = try {
        val app = FirebaseApp.initializeApp(context.applicationContext)
            ?: throw IllegalStateException("Firebase app configuration unavailable")
        Result.success(GeminiFrameAnalyzer(clock, FirebaseGeminiVisualTransport.forFirebaseApp(app)))
    } catch (_: Throwable) {
        // Do not surface configuration/provider detail in UI, timeline, or logs.
        Result.failure(IllegalStateException("Firebase visual analysis unavailable"))
    }
}
