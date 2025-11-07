package com.pinrysaver.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import com.pinrysaver.R
import com.pinrysaver.data.model.PinryPin
import coil.load
import kotlin.math.abs

class FullscreenPinDialogFragment : DialogFragment() {

    private val viewModel: GalleryViewModel by activityViewModels()

    private lateinit var container: View
    private lateinit var viewPager: ViewPager2
    private lateinit var closeButton: ImageButton
    private lateinit var tagButton: ImageButton
    private lateinit var tagsRecyclerView: RecyclerView
    private val tagsAdapter = TagsAdapter()
    private lateinit var pagerAdapter: FullscreenPinPagerAdapter
    private var initialIndex: Int = 0
    private var tagsVisible = false
    
    internal var dismissing = false
    internal var dismissStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setDimAmount(0f)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_fullscreen_pin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        container = view.findViewById(R.id.fullscreenContainer)
        viewPager = view.findViewById(R.id.fullscreenPager)
        closeButton = view.findViewById(R.id.closeButton)
        tagButton = view.findViewById(R.id.tagButton)
        tagsRecyclerView = view.findViewById(R.id.tagsRecyclerView)

        pagerAdapter = FullscreenPinPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1

        tagsRecyclerView.adapter = tagsAdapter
        tagsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        closeButton.setOnClickListener { dismissAllowingStateLoss() }
        tagButton.setOnClickListener { toggleTags() }

        initialIndex = arguments?.getInt(ARG_START_INDEX) ?: 0
        viewPager.setCurrentItem(initialIndex, false)

        viewModel.pins.observe(viewLifecycleOwner) { pins ->
            val wasEmpty = pagerAdapter.itemCount == 0
            pagerAdapter.submitList(pins) { adapter ->
                pagerAdapter.setZoomCallback { isZooming ->
                    viewPager.isUserInputEnabled = !isZooming
                }
            }
            if (wasEmpty && initialIndex < pins.size) {
                viewPager.setCurrentItem(initialIndex, false)
            }
            updateTagsForCurrentPin()
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.loadMoreIfNeeded(position, fastScroll = false)
                updateTagsForCurrentPin()
            }
        })
    }

    private fun updateTagsForCurrentPin() {
        val currentPosition = viewPager.currentItem
        val pins = viewModel.pins.value ?: emptyList()
        if (currentPosition < pins.size) {
            val pin = pins[currentPosition]
            val tags = pin.tags ?: emptyList()
            val hasTags = tags.isNotEmpty()

            if (hasTags) {
                tagButton.setImageResource(if (tagsVisible) R.drawable.ic_tag_filled else R.drawable.ic_tag_outline)
                tagButton.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.primary_color),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                tagsAdapter.submitList(tags)
                tagButton.isEnabled = true
                if (tagsVisible) {
                    tagsRecyclerView.visibility = View.VISIBLE
                }
            } else {
                tagButton.setImageResource(R.drawable.ic_tag_outline)
                tagButton.setColorFilter(
                    android.graphics.Color.WHITE,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                tagButton.isEnabled = false
                tagsRecyclerView.visibility = View.GONE
            }
        }
    }

    private fun toggleTags() {
        tagsVisible = !tagsVisible
        updateTagsVisibility()
    }

    private fun updateTagsVisibility() {
        val currentPosition = viewPager.currentItem
        val pins = viewModel.pins.value ?: emptyList()
        if (currentPosition < pins.size) {
            val pin = pins[currentPosition]
            val hasTags = pin.tags?.isNotEmpty() == true

            if (hasTags) {
                if (tagsVisible) {
                    tagButton.setImageResource(R.drawable.ic_tag_filled)
                    tagsRecyclerView.visibility = View.VISIBLE
                    tagsRecyclerView.alpha = 0f
                    tagsRecyclerView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                } else {
                    tagButton.setImageResource(R.drawable.ic_tag_outline)
                    tagsRecyclerView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            tagsRecyclerView.visibility = View.GONE
                        }
                        .start()
                }
            }
        }
    }

    internal fun handleDismissGesture(dy: Float) {
        val translation = dy.coerceAtLeast(0f)
        viewPager.translationY = translation
        val progress = (translation / DISMISS_THRESHOLD).coerceIn(0f, 1f)
        val alpha = 1f - progress
        container.background?.mutate()?.alpha = (alpha * 255).toInt()
        closeButton.alpha = alpha
        tagButton.alpha = alpha
        tagsRecyclerView.alpha = alpha
    }

    internal fun finalizeDismiss() {
        val translation = viewPager.translationY
        if (translation > DISMISS_THRESHOLD) {
            // Complete dismiss
            dismissAllowingStateLoss()
        } else {
            // Snap back
            viewPager.animate()
                .translationY(0f)
                .setDuration(200)
                .withEndAction {
                    container.background?.mutate()?.alpha = 255
                    closeButton.alpha = 1f
                    tagButton.alpha = 1f
                    tagsRecyclerView.alpha = 1f
                }
                .start()
        }
        dismissing = false
    }

    companion object {
        const val TAG = "FullscreenPinDialog"
        private const val ARG_START_INDEX = "arg_start_index"
        private const val DISMISS_THRESHOLD = 400f

        fun newInstance(startIndex: Int): FullscreenPinDialogFragment = FullscreenPinDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_START_INDEX, startIndex)
            }
        }
    }

    private class FullscreenPinPagerAdapter(
        private val fragment: FullscreenPinDialogFragment
    ) : RecyclerView.Adapter<FullscreenPinPagerAdapter.PinPageViewHolder>() {

        private val items = mutableListOf<PinryPin>()
        private var zoomCallback: ((Boolean) -> Unit)? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinPageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen_pin, parent, false)
            return PinPageViewHolder(view, fragment, zoomCallback)
        }

        override fun onBindViewHolder(holder: PinPageViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun submitList(newItems: List<PinryPin>, callback: ((FullscreenPinPagerAdapter) -> Unit)? = null) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            callback?.invoke(this)
        }

        fun setZoomCallback(callback: (Boolean) -> Unit) {
            zoomCallback = callback
        }

        class PinPageViewHolder(
            itemView: View,
            private val fragment: FullscreenPinDialogFragment,
            private val zoomCallback: ((Boolean) -> Unit)?
        ) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ZoomableImageView = itemView.findViewById(R.id.fullscreenImage)
            private val spinner: ImageView = itemView.findViewById(R.id.fullscreenProgress)
            private var spinnerRunnable: Runnable? = null

            fun bind(pin: PinryPin) {
                // Reset zoom when binding new pin
                imageView.resetZoom()

                // Setup zoom callback to disable ViewPager during zoom
                imageView.onZoomStateChanged = { isZooming ->
                    zoomCallback?.invoke(isZooming)
                }

                // Setup dismiss callback for middle 33% swipe down
                imageView.onDismissGesture = { startX, dy ->
                    val viewWidth = imageView.width.toFloat()
                    val leftBound = viewWidth * 0.33f
                    val rightBound = viewWidth * 0.67f
                    
                    // Only allow dismiss in middle 33%
                    if (startX in leftBound..rightBound) {
                        if (!fragment.dismissing) {
                            fragment.dismissing = true
                            fragment.dismissStartY = 0f
                        }
                        fragment.handleDismissGesture(dy)
                        true
                    } else {
                        false
                    }
                }
                
                imageView.onDismissEnd = {
                    if (fragment.dismissing) {
                        fragment.finalizeDismiss()
                    }
                }
                
                // Setup paging callback for left/right 33%
                imageView.shouldAllowPaging = { startX, dx ->
                    val viewWidth = imageView.width.toFloat()
                    val leftBound = viewWidth * 0.33f
                    val rightBound = viewWidth * 0.67f
                    
                    // Allow paging in left/right zones
                    startX < leftBound || startX > rightBound
                }

                val thumbnailUrl = pin.image.bestImageUrl
                val fullUrl = pin.image.fullSizeUrl

                if (thumbnailUrl.isNullOrBlank() && fullUrl.isNullOrBlank()) {
                    stopSpinner()
                    imageView.setImageResource(R.drawable.bg_pin_placeholder)
                    return
                }

                prepareSpinner()

                if (!thumbnailUrl.isNullOrBlank()) {
                    imageView.load(thumbnailUrl) {
                        crossfade(false)
                        listener(
                            onSuccess = { _, _ ->
                                if (fullUrl.isNullOrBlank()) {
                                    stopSpinner()
                                } else {
                                    loadFullImage(fullUrl, imageView.drawable)
                                }
                            },
                            onError = { _, _ ->
                                if (!fullUrl.isNullOrBlank()) {
                                    loadFullImage(fullUrl, imageView.drawable)
                                } else {
                                    stopSpinner()
                                }
                            }
                        )
                    }
                } else if (!fullUrl.isNullOrBlank()) {
                    loadFullImage(fullUrl, null)
                }
            }

            private fun loadFullImage(url: String, placeholderDrawable: android.graphics.drawable.Drawable?) {
                prepareSpinner()
                imageView.load(url) {
                    crossfade(true)
                    placeholder(placeholderDrawable)
                    listener(
                        onSuccess = { _, _ -> stopSpinner() },
                        onError = { _, _ -> stopSpinner() }
                    )
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
                spinner.postDelayed(runnable, PinAdapter.SPINNER_DELAY_MS)
            }

            private fun stopSpinner() {
                spinnerRunnable?.let { spinner.removeCallbacks(it) }
                spinnerRunnable = null
                spinner.clearAnimation()
                spinner.visibility = View.GONE
            }
        }
    }
}

