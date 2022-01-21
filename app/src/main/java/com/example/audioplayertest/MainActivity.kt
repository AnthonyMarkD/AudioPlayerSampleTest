package com.example.audioplayertest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.audioplayertest.ui.theme.AudioPlayerTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudioPlayerTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
   Surface(Modifier.fillMaxSize()) {
        val context = LocalContext.current
        Box(Modifier.fillMaxSize()) {
            Button(onClick = {
                Intent(context, MediaPlaybackService::class.java).also { intent ->
                    context.startForegroundService(intent)
                }
            }, Modifier.align(Alignment.Center)) {
                Text("Start Audio Player")
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AudioPlayerTestTheme {

    }
}

