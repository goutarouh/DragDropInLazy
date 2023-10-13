package com.example.dragdropinlazy.lazycolumn

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun rememberLazyColumnDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    onMove: (Int, Int) -> Unit
): LazyColumnDragDropState {
    return remember {
        LazyColumnDragDropState(
            lazyListState = lazyListState,
            scope = scope,
            onMove = onMove
        )
    }
}

@Stable
class LazyColumnDragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    /**
     * ドラッグ中のアイテムのドラッグ距離 (ドラッグ中はリアルタイムで更新される)
     * 上方向なら負値、下方向なら正値
     * Compose側ではこの値を読み込んでRecomposeを行う必要があるため、Stateにする必要がある。
     * */
    private var draggedDistance by mutableStateOf(0f)

    /**
     * ドラッグ中のアイテムのIndex
     * ドラッグ中に並べ替えが行われた場合、更新される。
     *
     * どのアイテムがドラッグ中かをComposeで読み込むためにstateにする必要がある。
     * Compose側では
     * isCurrentDragging = index == lazyColumnDragDropState.draggedItemIndex
     * で読み込み、この値が変わるたびにRecomposeさせる必要があるため。
     * */
    var draggedItemIndex by mutableStateOf<Int?>(null)

    /**
     * ドラッグ中のアイテムのColumn内でのレイアウト情報
     * draggedItemIndexかinitialDraggedItemのどちらかがstateであればcompose側でRecomposeが発生するのでOK
     * 意味的にはdraggedItemIndexがドラッグ中にRecomposeされるので、stateである方が自然
     * */
    var initialDraggedItem: LazyListItemInfo? = null

    /**
     * ドラッグ中のアイテムの移動距離
     * draggedDistanceだけだと、並べ替え後のoffsetが反映されないので、それを加味する
     */
    val draggedItemY: Float
        get() {
            val draggedItemOffset = lazyListState.findVisibleItemInfoByIndex(draggedItemIndex)?.offset ?: 0
            return (initialDraggedItem?.offset ?: 0f).toFloat() + draggedDistance - draggedItemOffset
        }

    /**
     * ドラッグ中のスクロールを管理するJob
     */
    private var overscrollJob by mutableStateOf<Job?>(null)

    /**
     * ドラッグ開始時に計算に必要な以下の初期値をセットする
     */
    fun onDragStart(index: Int) {
        lazyListState.findVisibleItemInfoByIndex(index)?.also {
            draggedItemIndex = it.index
            initialDraggedItem = it
        }
    }

    /**
     * ドラッグが終了した時やキャンセルされた場合に各値を初期化する
     */
    fun onDragInterrupted() {
        draggedDistance = 0f
        draggedItemIndex = null
        initialDraggedItem = null
        overscrollJob?.cancel()
    }

    fun onDrag(scrollAmount: Float) {
        draggedDistance += scrollAmount
        switchItemIfNeed()
        scrollIfNeed()
    }

    private fun calculateScrollAmount(): Float {
        return initialDraggedItem?.let { initialDraggedItem ->
            val startOffset = initialDraggedItem.offset + draggedDistance
            val endOffset = initialDraggedItem.offsetEnd + draggedDistance
            return@let when {
                draggedDistance > 0 -> (endOffset - lazyListState.layoutInfo.viewportEndOffset).takeIf { diff -> diff > 0 }
                draggedDistance < 0 -> (startOffset - lazyListState.layoutInfo.viewportStartOffset).takeIf { diff -> diff < 0 }
                else -> null
            }
        } ?: 0f
    }

    private fun switchItemIfNeed() {
        initialDraggedItem?.let { initialDraggedItem ->
            val startOffset = initialDraggedItem.offset + draggedDistance
            val endOffset = initialDraggedItem.offsetEnd + draggedDistance
            val currentItem = lazyListState.findVisibleItemInfoByIndex(draggedItemIndex) ?: return@let
            findSwitchItem(startOffset, endOffset, currentItem)?.also { item ->
                draggedItemIndex?.let { current -> onMove.invoke(current, item.index) }
                draggedItemIndex = item.index
            }
        }
    }

    private fun scrollIfNeed() {
        if (overscrollJob?.isActive == true) {
            return
        }

        calculateScrollAmount()
            .takeIf { it != 0f }
            ?.let {
                overscrollJob = scope.launch {
                    lazyListState.scrollBy(it)
                }
            }
            ?: run { overscrollJob?.cancel() }
    }

    private fun findSwitchItem(
        draggedItemStartOffset: Float,
        draggedItemEndOffset: Float,
        currentItem: LazyListItemInfo,
    ): LazyListItemInfo? {
        return lazyListState.layoutInfo.visibleItemsInfo
            .filterNot { item ->
                item.offsetEnd < draggedItemStartOffset             // ドラッグ中のアイテムの上端より、上にあるアイテムを除く
                    || item.offset > draggedItemEndOffset       // ドラッグ中のアイテムの下端より、下にあるアイテムを除く
                    || draggedItemIndex == item.index   // 自分自身は除く
            }
            .firstOrNull { item ->
                // ドラッグにより、ドラッグ中アイテムと重なったアイテムがある場合、itemとして渡される。
                val delta = draggedItemStartOffset - currentItem.offset
                when {
                    // 下方向にドラッグしている場合
                    // ドラッグ中のアイテムの下端が、したのアイテムの下端を超えた場合true
                    delta > 0 -> (draggedItemEndOffset > item.offsetEnd)
                    // 上方向にドラッグしている場合
                    // ドラッグ中のアイテムの上端が、上のアイテムの上端を超えた場合true
                    else -> (draggedItemStartOffset < item.offset)
                }
            }
    }

    private fun LazyListState.findVisibleItemInfoByIndex(index: Int?): LazyListItemInfo? {
        return this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }

    private val LazyListItemInfo.offsetEnd: Int
        get() = this.offset + this.size

}
