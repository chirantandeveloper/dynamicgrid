package com.example.draggablegridview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.example.draggablegridview.data.items
import com.example.draggablegridview.ui.theme.DraggableGridViewTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val list = items
            DraggableGridViewTheme {
                Surface(color = Color.LightGray) {
                    SimpleReorderableLazyVerticalGridScreen(list,
                        onMove = {to, from->

                        })
                }
            }

        }
    }
}

