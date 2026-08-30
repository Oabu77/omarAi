package com.darcloud.omarai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darcloud.omarai.ui.OmarAiRoot
import com.darcloud.omarai.ui.OmarTheme
import com.darcloud.omarai.ui.OmarViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmarTheme {
                val model: OmarViewModel = viewModel()
                OmarAiRoot(model)
            }
        }
    }
}
