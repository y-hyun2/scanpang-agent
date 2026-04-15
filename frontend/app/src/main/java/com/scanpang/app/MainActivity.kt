package com.scanpang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.scanpang.app.navigation.ScanPangApp
import com.scanpang.app.ui.theme.ScanPangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanPangTheme {
                ScanPangApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
