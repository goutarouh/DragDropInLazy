package com.example.dragdropinlazy.lazycolumn

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun SortableLazyColumn(
    modifier: Modifier = Modifier
) {
    var items by remember { mutableStateOf(List(20) { "Item $it" }) }
    val lazyColumnDragDropState = rememberLazyColumnDragDropState(
        onMove = { from, to ->
            items = items.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFDDDDDD)),
        state = lazyColumnDragDropState.lazyListState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        itemsIndexed(items) { index, item ->
            val isCurrentDragging = index == lazyColumnDragDropState.draggedItemIndex
            DragDropItem(
                item = item,
                modifier = Modifier
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { _, offset -> lazyColumnDragDropState.onDrag(offset.y) },
                            onDragStart = { lazyColumnDragDropState.onDragStart(index) },
                            onDragEnd = { lazyColumnDragDropState.onDragInterrupted() },
                            onDragCancel = { lazyColumnDragDropState.onDragInterrupted() }
                        )
                    }
                    .graphicsLayer {
                        val draggedItemY =
                            lazyColumnDragDropState.draggedItemY.takeIf { isCurrentDragging }
                        translationY =  draggedItemY ?: 0f
                        scaleX = if (isCurrentDragging) 1.01f else 1f
                        scaleY = if (isCurrentDragging) 1.01f else 1f
                    }
                    .zIndex(if (isCurrentDragging) 1f else 0f),
            )

            val isPinned = lazyColumnDragDropState.initialDraggedItem?.index == index
            if (isPinned) {
                val pinnableContainer = LocalPinnableContainer.current
                DisposableEffect(key1 = pinnableContainer, effect = {
                    val pinnedHandle = pinnableContainer?.pin()
                    onDispose {
                        pinnedHandle?.release()
                    }
                })
            }
        }
    }
}