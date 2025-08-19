package com.example.draggablegridview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.draggablegridview.data.Item
import com.example.draggablegridview.data.items
import com.example.draggablegridview.ui.theme.DraggableGridViewTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val list = remember { mutableStateListOf(
                *(0..65).map {
                    Item(it, "Item #$it", if (it % 2 == 0) 70 else 100)
                }.toTypedArray()
            )  }
            DraggableGridViewTheme {
                Surface(color = Color.LightGray, modifier = Modifier.fillMaxSize()) {

                    SampleGrid(items = list)
                }
            }

        }
    }
}

