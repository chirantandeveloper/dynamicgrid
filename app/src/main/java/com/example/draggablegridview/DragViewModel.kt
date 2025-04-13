package com.example.draggablegridview

import androidx.lifecycle.ViewModel
import com.example.draggablegridview.data.Item
import com.example.draggablegridview.data.items
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DragViewModel : ViewModel() {

    private var _uiState = MutableStateFlow<List<Item>>(emptyList())
     val uistate : StateFlow<List<Item>> = _uiState.asStateFlow()

    init {

        _uiState.value = items
    }

    fun onMoveItems(to : Int , from : Int){
        _uiState.update {
            it.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    }

    fun onRemove(item : Item){
        _uiState.update {
            it.toMutableList().apply {
                remove(item)
            }
        }
    }
}