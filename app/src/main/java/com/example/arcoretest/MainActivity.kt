package com.example.arcoretest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.arcoretest.ui.ARSceneComposable
import com.example.arcoretest.ui.theme.ARCoreTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge()

        setContent {
            ARCoreTestTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ARSceneComposable(
                        onPermissionDenied = { }
                    )
                }
            }
        }
    }
}