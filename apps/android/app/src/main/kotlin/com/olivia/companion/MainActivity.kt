package com.olivia.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.olivia.companion.commands.LocalCommandController
import com.olivia.companion.commands.UnavailablePorcupineRecognizer
import com.olivia.companion.session.SessionRuntime
import com.olivia.companion.session.SessionRuntimeFactory
import com.olivia.companion.ui.OliviaApp
import com.olivia.companion.ui.OliviaTheme

class MainActivity : ComponentActivity() {
    private lateinit var sessionRuntime: SessionRuntime
    private lateinit var commandController: LocalCommandController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionRuntime = SessionRuntimeFactory.create(this, ActiveBackend.current)
        commandController = LocalCommandController(this, sessionRuntime, UnavailablePorcupineRecognizer())
        setContent {
            OliviaTheme {
                OliviaApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        commandController.start()
    }

    override fun onStop() {
        commandController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        commandController.close()
        sessionRuntime.close()
        super.onDestroy()
    }
}
