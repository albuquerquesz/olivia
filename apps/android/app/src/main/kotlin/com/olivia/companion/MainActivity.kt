package com.olivia.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.SessionRuntimeFactory
import com.olivia.companion.ui.OliviaApp
import com.olivia.companion.ui.OliviaTheme

class MainActivity : ComponentActivity() {
    private lateinit var sessionRuntime: SessionRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionRuntime = SessionRuntimeFactory.create(this, ActiveBackend.current)
        setContent {
            OliviaTheme {
                OliviaApp()
            }
        }
    }

    override fun onDestroy() {
        sessionRuntime.close()
        super.onDestroy()
    }
}
