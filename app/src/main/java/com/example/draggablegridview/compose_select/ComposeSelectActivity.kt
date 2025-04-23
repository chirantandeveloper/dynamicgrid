package com.example.draggablegridview.compose_select

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.draggablegridview.compose_select.composableScren.DropdownTestScreen
import com.example.draggablegridview.ui.theme.DraggableGridViewTheme

class ComposeSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DraggableGridViewTheme {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    DropdownTestScreen()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Hello there")
                }
            }
        }
    }
}

