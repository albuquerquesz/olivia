package com.olivia.companion

import com.olivia.core.AnalysisMode

/** Process-local gate shared by settings and the physical Firebase adapter. */
object AnalysisModeController {
    @Volatile
    var current: AnalysisMode = AnalysisMode.AUTOMATIC
}
