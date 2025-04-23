package com.example.composeselect

//import androidx.compose.material.Text
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun DefaultHighlightedItem(text: String, query: String) {
    val highlighted = buildAnnotatedString {
        val startIndex = text.indexOf(query, ignoreCase = true)
        if (startIndex != -1) {
            append(text.substring(0, startIndex))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(text.substring(startIndex, startIndex + query.length))
            }
            append(text.substring(startIndex + query.length))
        } else {
            append(text)
        }
    }
    Text(text = highlighted)
}