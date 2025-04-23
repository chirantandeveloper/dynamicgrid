package com.example.composeselect

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> SearchableDropdown(
    items: List<T>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T, String) -> Unit = { item, query ->
        DefaultHighlightedItem(item.toString(), query)
    },
    placeholder: String = "Search..."
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldSize by remember { mutableStateOf(Size.Zero) }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding() // handles keyboard properly
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChanged(it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    textFieldSize = coordinates.size.toSize()
                },
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.clickable {
                            onQueryChanged("")
                            expanded = false
                        }
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        DropdownMenu(
            expanded = expanded && query.isNotEmpty() && items.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                .offset(y = 4.dp)
                .align(Alignment.BottomCenter)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { itemContent(item, query) },
                    onClick = {
                        onItemSelected(item)
                        onQueryChanged(item.toString())
                        expanded = false
                    }
                )
            }
        }
    }

}


@Composable
fun SearchableDropdownWithOverlay(
    items: List<String>,
    selectedItem: String?,
    onItemSelected: (String) -> Unit,
    placeholder: String
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textFieldSize = remember { mutableStateOf(IntSize.Zero) }

    val textFieldCoordinates = remember { mutableStateOf(Offset.Zero) }
    val textFieldHeight = remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    val view = LocalView.current
    val windowSize = remember {
        IntSize(view.width, view.height)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    expanded = true
                },
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        val size = coordinates.size
                        textFieldCoordinates.value = pos
                        textFieldHeight.value = size.height.toFloat()
                        textFieldSize.value = coordinates.size
                    },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.clickable {
                                query = ""
                                expanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                },
                singleLine = true
            )
        }
        val filteredItems = items.filter {
            it.contains(query, ignoreCase = true)
        }
        if (expanded && query.isNotEmpty() && filteredItems.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) {
                        IntOffset(
                            x = 0,
                            y = (textFieldHeight.value).toInt()
                        )
                    },
                properties = PopupProperties(focusable = false)
            ) {

                    Surface(
                        modifier = Modifier
                            .width(with(density) { textFieldSize.value.width.toDp() }) // match TextField width as needed
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(4.dp),
                            tonalElevation = 8.dp
                        ) {
                            LazyColumn {
                                items(filteredItems.size) { pos ->
                                    DropdownMenuItem(
                                        text = {
                                            val start = filteredItems[pos].indexOf(query, ignoreCase = true)
                                            if (start != -1) {
                                                val end = start + query.length
                                                val annotated = buildAnnotatedString {
                                                    append(filteredItems[pos].substring(0, start))
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Blue)) {
                                                        append(filteredItems[pos].substring(start, end))
                                                    }
                                                    append(filteredItems[pos].substring(end))
                                                }
                                                Text(annotated, modifier = Modifier.padding(8.dp))
                                            } else {
                                                Text(filteredItems[pos], modifier = Modifier.padding(8.dp))
                                            }

                                               },
                                        onClick = {
                                            onItemSelected(filteredItems[pos])
                                            query = filteredItems[pos]
                                            expanded = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                }
        }
    }
}
