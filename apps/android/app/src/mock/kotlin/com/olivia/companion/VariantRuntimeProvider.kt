package com.olivia.companion

import android.content.Context
import com.olivia.companion.session.BackendRuntimeProvider
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.createMockSessionRuntime

object VariantRuntimeProvider : BackendRuntimeProvider {
    override fun create(context: Context): SessionRuntime = createMockSessionRuntime(context)
}
