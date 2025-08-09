package com.example.draggablegridview.compose_select.composableScren

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.example.composeselect.SearchableDropdownWithOverlay

//@Composable
//fun DropdownTestScreen() {
//    val fruits = listOf("Apple", "Banana", "Orange", "Grapes", "Mango", "Pineapple", "Avocado","Banana1","Banana2", "3Banana", "Banana4","Banana445")
//    var query by remember { mutableStateOf("") }
//    var selectedItem by remember { mutableStateOf<String?>(null) }
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text(text = "Select a Fruit", style = MaterialTheme.typography.titleMedium)
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        SearchableDropdown(
//            items = fruits.filter { it.contains(query, ignoreCase = true) },
//            query = query,
//            onQueryChanged = { query = it },
//            onItemSelected = { selectedItem = it },
//            placeholder = "Search fruits..."
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        selectedItem?.let {
//            Text("You selected: $it", style = MaterialTheme.typography.bodyLarge)
//        }
//    }
//}

@Composable
fun DropdownTestScreen() {
    val fruitList = listOf(
        "Apple", "Apricot", "Banana", "Blackberry", "Blueberry",
        "Cherry", "Date", "Fig", "Grape", "Guava", "Kiwi", "Mango", "Peach", "Pineapple"
    )
    var selectedItem by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        Column {
            SearchableDropdownWithOverlay(
                items = fruitList,
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it },
                placeholder = "Search fruits...",
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Selected: ${selectedItem ?: "None"}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
