package com.example.draggablegridview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.example.draggablegridview.ui.theme.DraggableGridViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DraggableGridViewTheme {
                Surface(color = Color.LightGray) {
                    SimpleReorderableLazyVerticalGridScreen()
                }
            }

        }
    }
}

