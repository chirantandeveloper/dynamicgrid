# 📦 DynamicGrid
[![](https://jitpack.io/v/chirantandeveloper/dynamicgrid.svg)](https://jitpack.io/#chirantandeveloper/dynamicgrid)
A lightweight Jetpack Compose library for creating **reorderable grid layouts** with smooth drag & drop.  
Supports auto-scrolling when dragging near edges, long-press to drag, and stable item reordering.

---

## ✨ Features
- 🔄 Drag & drop grid items with smooth animations  
- ⏱ Long-press hold (500ms) to start dragging  
- 📏 Auto-scroll when dragging near edges  
- 🎨 Support for variable-sized items  
- 📦 Simple API, easy to integrate  

---

## 📸 Demo
<img src="https://github.com/chirantandeveloper/dynamicgrid/blob/229f659aaa6b47da49970cd652151f2ca887a433/1000165926%20(576%C3%971280).gif" width="30%">

---

## 📦 Installation
```gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}
```
### Then add the dependency (latest version 1.0.3):
```gradle
dependencies {
    implementation("com.github.chirantandeveloper:dynamicgrid:1.0.3")
}
```
---

## 🚀 Usage
#### 1. Create your data list (must be a SnapshotStateList):
```kotlin
data class Item(val id: Int, val text: String, val size: Int)

val items = remember {
    mutableStateListOf(
        *(0..34).map {
            Item(id = it, text = "Item #$it", size = if (it % 2 == 0) 70 else 100)
        }.toTypedArray()
    )
}
```
### 2.Use rememberGridReorderManager and wrap items in DragEnabledGridItem:
```kotlin
val gridState = rememberLazyGridState()

val manager = rememberGridReorderManager(
    gridState = gridState,
    onMove = { from, to ->
        val fromIdx = from.index
        val toIdx = to.index
        if (fromIdx in items.indices && toIdx in items.indices) {
            items.add(toIdx, items.removeAt(fromIdx))
        }
    }
)

LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    state = gridState,
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(
        count = items.size,
        key = { idx -> items[idx].id } // must be stable!
    ) { index ->
        DragEnabledGridItem(manager = manager, key = items[index].id) { isDragging ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        color = if (isDragging) Color.LightGray else Color.Red,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .dragHandle() // 👈 enables dragging
                    .clickable {
                        // your custom click logic
                    }
            ) {
                Text(
                    text = "Item ${items[index].id}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
```
## 📜 License
MIT License

Copyright (c) 2025 Chirantan Chaudhury
