package com.pinrysaver.ui.gallery

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class PinSpacingItemDecoration(
    private val spanCount: Int,
    private val horizontalSpacing: Int,
    private val verticalSpacing: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            return
        }

        val layoutParams = view.layoutParams as? StaggeredGridLayoutManager.LayoutParams
            ?: return

        val column = layoutParams.spanIndex

        val halfHorizontal = horizontalSpacing / 2
        val halfVertical = verticalSpacing / 2

        outRect.left = when (column) {
            0 -> horizontalSpacing
            else -> halfHorizontal
        }

        outRect.right = when (column) {
            spanCount - 1 -> horizontalSpacing
            else -> halfHorizontal
        }

        outRect.top = if (position < spanCount) verticalSpacing else halfVertical
        outRect.bottom = verticalSpacing
    }
}

