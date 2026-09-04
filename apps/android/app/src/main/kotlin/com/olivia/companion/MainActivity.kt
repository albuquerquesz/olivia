package com.olivia.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.olivia.companion.ui.OliviaFoundationScreen
import com.olivia.companion.ui.OliviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OliviaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OliviaFoundationScreen(
                        backend = ActiveBackend.current,
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }
}
