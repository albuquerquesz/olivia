package com.olivia.companion.session

import com.olivia.core.AudioFeedback
import kotlinx.coroutines.flow.StateFlow

enum class AudioFeedbackStatus { INITIALIZING, READY, ERROR }

/** Observable status shared by the UI and the runtime's single audio adapter. */
interface AudioFeedbackController : AudioFeedback {
    val status: StateFlow<AudioFeedbackStatus>
    val statusMessage: StateFlow<String?>
    fun close()
}
