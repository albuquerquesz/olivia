package com.olivia.companion

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import com.olivia.companion.session.SessionRuntimeFactory
import com.olivia.companion.ui.OliviaApp
import com.olivia.companion.ui.OliviaTheme

class MainActivity : ComponentActivity() {
    private val glassesPermissionLauncherRegistration =
        if (ActiveBackend.current.usesDatSdk) {
            SessionRuntimeFactory.registerGlassesPermissionLauncher(this)
        } else {
            Result.success(Unit)
        }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glassesPermissionLauncherRegistration.exceptionOrNull()?.printStackTrace()
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
                OliviaApp()
            }
        }
    }

}
