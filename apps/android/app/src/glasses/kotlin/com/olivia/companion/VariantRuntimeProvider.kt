package com.olivia.companion

import android.content.Context
import com.olivia.companion.ai.FirebaseVisualAnalyzerFactory
import com.olivia.companion.session.BackendRuntimeProvider
import com.olivia.companion.session.DatGlassesGateway
import com.olivia.companion.session.DatMockDeviceBootstrap
import com.olivia.companion.session.LiveSessionRuntime
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.UnavailableSessionRuntime
import com.olivia.core.SystemOliviaClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object VariantRuntimeProvider : BackendRuntimeProvider {
    override fun create(context: Context): SessionRuntime {
        val clock = SystemOliviaClock()
        val analyzer = FirebaseVisualAnalyzerFactory.create(context, clock)
            .getOrElse { return UnavailableSessionRuntime() }
        return LiveSessionRuntime(
            context = context,
            gateway = DatGlassesGateway(
                context,
                clock,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ),
            analyzer = analyzer,
            clock = clock,
        )
    }
}
