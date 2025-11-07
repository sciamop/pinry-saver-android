package com.pinrysaver.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
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
        private val spinner: ImageView = itemView.findViewById(R.id.itemProgress)
        private val placeholderIcon: ImageView = itemView.findViewById(R.id.placeholderIcon)
        private var spinnerRunnable: Runnable? = null

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

            // Only show placeholder/spinner if image isn't already loaded
            val currentDrawable = imageView.drawable
            val hasImage = currentDrawable != null && currentDrawable.intrinsicWidth > 0
            
            if (!hasImage) {
                placeholderIcon.visibility = View.VISIBLE
                placeholderIcon.rotation = orientationToggle
                placeholderIcon.scaleX = 0.67f
                placeholderIcon.scaleY = 0.67f
                prepareSpinner()
            } else {
                // Image already loaded, don't show spinner/placeholder
                stopSpinner()
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
                        stopSpinner()
                        placeholderIcon.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        stopSpinner()
                        placeholderIcon.visibility = View.VISIBLE
                    }
                )
            }

            // Adjust approximate aspect ratio to help staggered grid look natural
            val width = item.image.thumbnail?.width ?: item.image.width
            val height = item.image.thumbnail?.height ?: item.image.height
            if (width != null && height != null && width > 0 && height > 0) {
                imageView.post {
                    val params = imageView.layoutParams
                    val measuredWidth = imageView.width.takeIf { it > 0 }
                        ?: imageView.measuredWidth.takeIf { it > 0 }
                        ?: itemView.width.takeIf { it > 0 }

                    if (measuredWidth != null && measuredWidth > 0) {
                        val calculatedHeight = (measuredWidth * height / width.toFloat())
                            .toInt()
                            .coerceAtLeast(200)
                        if (params.height != calculatedHeight) {
                            params.height = calculatedHeight
                            imageView.layoutParams = params
                        }
                    }
                }
            }
        }

        private fun prepareSpinner() {
            stopSpinner()
            spinner.visibility = View.GONE
            val runnable = Runnable {
                spinner.visibility = View.VISIBLE
                spinner.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.pin_spin))
            }
            spinnerRunnable = runnable
            spinner.postDelayed(runnable, SPINNER_DELAY_MS)
        }

        private fun stopSpinner() {
            spinnerRunnable?.let { spinner.removeCallbacks(it) }
            spinnerRunnable = null
            spinner.clearAnimation()
            spinner.visibility = View.GONE
        }
    }

    companion object {
        internal const val SPINNER_DELAY_MS = 120L

        val DiffCallback = object : DiffUtil.ItemCallback<PinryPin>() {
            override fun areItemsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem == newItem
        }
    }
}

