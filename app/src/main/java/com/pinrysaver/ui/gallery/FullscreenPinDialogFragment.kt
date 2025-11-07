package com.pinrysaver.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
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
    private val pagerAdapter = FullscreenPinPagerAdapter()
    private var initialIndex: Int = 0

    private var initialTouchY = 0f
    private var initialTouchX = 0f
    private var draggingDown = false

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

        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1

        closeButton.setOnClickListener { dismissAllowingStateLoss() }

        initialIndex = arguments?.getInt(ARG_START_INDEX) ?: 0
        viewPager.setCurrentItem(initialIndex, false)

        viewModel.pins.observe(viewLifecycleOwner) { pins ->
            val wasEmpty = pagerAdapter.itemCount == 0
            pagerAdapter.submitList(pins)
            if (wasEmpty && initialIndex < pins.size) {
                viewPager.setCurrentItem(initialIndex, false)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.loadMoreIfNeeded(position)
            }
        })

        attachSwipeToDismiss()
    }

    private fun attachSwipeToDismiss() {
        viewPager.getChildAt(0)?.setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchY = event.rawY
                initialTouchX = event.rawX
                draggingDown = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - initialTouchY
                val dx = event.rawX - initialTouchX

                if (!draggingDown) {
                    if (dy > 20 && dy > abs(dx)) {
                        draggingDown = true
                        viewPager.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (draggingDown) {
                    val translation = dy.coerceAtLeast(0f)
                    viewPager.translationY = translation
                    val alpha = 1f - (translation / DISMISS_DISTANCE).coerceIn(0f, 1f)
                    container.background?.mutate()?.alpha = (alpha * 255).toInt()
                    closeButton.alpha = alpha
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (draggingDown) {
                    val totalDy = event.rawY - initialTouchY
                    if (totalDy > DISMISS_DISTANCE) {
                        dismissAllowingStateLoss()
                    } else {
                        viewPager.animate()
                            .translationY(0f)
                            .setDuration(200L)
                            .withEndAction {
                                container.background?.mutate()?.alpha = 255
                                closeButton.alpha = 1f
                            }
                            .start()
                    }
                    draggingDown = false
                    return true
                }
            }
        }
        return false
    }

    companion object {
        const val TAG = "FullscreenPinDialog"
        private const val ARG_START_INDEX = "arg_start_index"
        private const val DISMISS_DISTANCE = 400f

        fun newInstance(startIndex: Int): FullscreenPinDialogFragment = FullscreenPinDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_START_INDEX, startIndex)
            }
        }
    }

    private class FullscreenPinPagerAdapter : RecyclerView.Adapter<FullscreenPinPagerAdapter.PinPageViewHolder>() {

        private val items = mutableListOf<PinryPin>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinPageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen_pin, parent, false)
            return PinPageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PinPageViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun submitList(newItems: List<PinryPin>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        class PinPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: android.widget.ImageView = itemView.findViewById(R.id.fullscreenImage)
            private val progress: android.widget.ProgressBar = itemView.findViewById(R.id.fullscreenProgress)

            fun bind(pin: PinryPin) {
                val targetUrl = pin.image.fullSizeUrl ?: pin.image.bestImageUrl
                if (targetUrl.isNullOrBlank()) {
                    progress.isGone = true
                    imageView.setImageResource(R.drawable.bg_pin_placeholder)
                    return
                }

                progress.isVisible = true
                imageView.load(targetUrl) {
                    crossfade(true)
                    listener(
                        onSuccess = { _, _ -> progress.isInvisible = true },
                        onError = { _, _ -> progress.isInvisible = true }
                    )
                }
            }
        }
    }
}

