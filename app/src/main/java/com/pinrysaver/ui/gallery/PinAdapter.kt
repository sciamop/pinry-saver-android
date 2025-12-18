package com.pinrysaver.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.pinrysaver.R
import com.pinrysaver.data.model.PinryPin

class PinAdapter(
    private val onPinClick: (position: Int) -> Unit,
    private var spanCount: Int = 2
) : ListAdapter<PinryPin, PinAdapter.PinViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pin, parent, false)
        return PinViewHolder(view, onPinClick)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        holder.bind(getItem(position), spanCount, position)
    }

    fun updateSpanCount(count: Int) {
        spanCount = count
        notifyDataSetChanged()
    }

    class PinViewHolder(
        itemView: View,
        private val onPinClick: (position: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.pinImage)
        private val placeholderIcon: ImageView = itemView.findViewById(R.id.placeholderIcon)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPinClick(position)
                }
            }
        }

        fun bind(item: PinryPin, spanCount: Int, position: Int) {
            val orientationToggle = if (spanCount > 0) {
                val column = position % spanCount
                val row = position / spanCount
                val base = if (column % 2 == 0) 45f else -45f
                if (row % 2 == 0) base else -base
            } else {
                45f
            }

            // Only show placeholder if image isn't already loaded
            val currentDrawable = imageView.drawable
            val hasImage = currentDrawable != null && currentDrawable.intrinsicWidth > 0
            
            if (!hasImage) {
                placeholderIcon.visibility = View.VISIBLE
                placeholderIcon.rotation = orientationToggle
                placeholderIcon.scaleX = 0.67f
                placeholderIcon.scaleY = 0.67f
            } else {
                // Image already loaded, don't show placeholder
                placeholderIcon.visibility = View.GONE
            }

            imageView.load(item.image.bestImageUrl) {
                crossfade(!hasImage) // Only crossfade if loading for first time
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                placeholder(R.drawable.bg_pin_placeholder)
                error(R.drawable.bg_pin_placeholder)
                listener(
                    onSuccess = { _, _ ->
                        placeholderIcon.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        placeholderIcon.visibility = View.VISIBLE
                    }
                )
            }

            // Adjust aspect ratio synchronously using ConstraintLayout
            val width = item.image.thumbnail?.width ?: item.image.width
            val height = item.image.thumbnail?.height ?: item.image.height

            val params = imageView.layoutParams as ConstraintLayout.LayoutParams
            if (width != null && height != null && width > 0 && height > 0) {
                // Set the aspect ratio H,w:h -> Height constrained by width
                // Actually simple "w:h" works when one dimension is 0dp
                params.dimensionRatio = "$width:$height"
            } else {
                params.dimensionRatio = "1:1"
            }
            imageView.layoutParams = params
        }

    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<PinryPin>() {
            override fun areItemsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem == newItem
        }
    }
}
