package com.example.draggablegridview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.draggablegridview.ui.theme.DraggableGridViewTheme

class MainActivity : ComponentActivity() {

    private val mViewModel : DragViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val listState by mViewModel.uistate.collectAsState()
            DraggableGridViewTheme {
                Surface(color = Color.LightGray) {
                    SimpleReorderableLazyVerticalGridScreen(listState,
                        onMove = {to, from->
                            mViewModel.onMoveItems(to, from)
                        },
                        onRemove = {item->
                            mViewModel.onRemove(item)
                        })
                }
            }

        }
    }
}

