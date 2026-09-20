package com.awnish.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.awnish.calculator.ui.AwnishApp
import com.awnish.calculator.ui.theme.AwnishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwnishTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AwnishApp(this)
                }
            }
        }
    }
}
