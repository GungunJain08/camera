package com.example.cameraapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import com.example.cameraapp.ui.theme.CameraAppTheme
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraAppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues: PaddingValues ->
                    // Agar tu padding nahi use karna chahta
                    permission()
                }


            }
        }
    }
}




