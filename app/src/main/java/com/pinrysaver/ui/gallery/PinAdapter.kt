package com.pinrysaver.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.pinrysaver.R
import com.pinrysaver.data.model.PinryPin

class PinAdapter(
    private val onPinClick: (position: Int) -> Unit
) : ListAdapter<PinryPin, PinAdapter.PinViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pin, parent, false)
        return PinViewHolder(view, onPinClick)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PinViewHolder(
        itemView: View,
        private val onPinClick: (position: Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.pinImage)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.itemProgress)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPinClick(position)
                }
            }
        }

        fun bind(item: PinryPin) {
            progressBar.visibility = View.VISIBLE
            imageView.load(item.image.bestImageUrl) {
                crossfade(true)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                placeholder(R.drawable.bg_pin_placeholder)
                error(R.drawable.bg_pin_placeholder)
                listener(
                    onSuccess = { _, _ ->
                        progressBar.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        progressBar.visibility = View.GONE
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
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PinryPin>() {
        override fun areItemsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PinryPin, newItem: PinryPin): Boolean = oldItem == newItem
    }
}

