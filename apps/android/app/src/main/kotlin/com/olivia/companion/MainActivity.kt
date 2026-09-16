package com.olivia.companion

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionRuntime = SessionRuntimeFactory.create(this, ActiveBackend.current)
        commandController = LocalCommandController(this, sessionRuntime, UnavailablePorcupineRecognizer())
        if (ActiveBackend.current.usesDatSdk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.CAMERA,
                ),
            )
        }
        setContent {
            OliviaTheme {
                OliviaApp(sessionRuntime)
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
