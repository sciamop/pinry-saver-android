package com.pinrysaver.ui.gallery

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import com.google.android.material.snackbar.Snackbar
import com.pinrysaver.R
import com.pinrysaver.data.model.PinryPin
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class FullscreenPinDialogFragment : DialogFragment() {

    private val viewModel: GalleryViewModel by activityViewModels()

    private lateinit var container: View
    private lateinit var viewPager: ViewPager2
    private lateinit var closeButton: ImageButton
    private lateinit var tagButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var tagsRecyclerView: RecyclerView
    private val tagsAdapter = TagsAdapter()
    private lateinit var pagerAdapter: FullscreenPinPagerAdapter
    private var initialIndex: Int = 0
    private var tagsVisible = false
    
    internal var dismissing = false
    internal var dismissStartY = 0f
    
    private var hideUiJob: Job? = null
    private var uiVisible = true
    private val UI_HIDE_DELAY_MS = 3000L

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
        shareButton = view.findViewById(R.id.shareButton)
        tagsRecyclerView = view.findViewById(R.id.tagsRecyclerView)

        pagerAdapter = FullscreenPinPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1
        
        // Track ViewPager touch events for auto-hide
        viewPager.setOnTouchListener { _, _ ->
            resetHideUiTimer()
            false // Don't consume the event
        }

        tagsRecyclerView.adapter = tagsAdapter
        tagsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        closeButton.setOnClickListener { dismissAllowingStateLoss() }
        tagButton.setOnClickListener { toggleTags() }
        shareButton.setOnClickListener { shareCurrentPin() }

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
                resetHideUiTimer()
            }
        })
        
        // Setup touch activity tracking for auto-hide
        setupTouchActivityTracking()
        
        // Start the initial hide timer
        resetHideUiTimer()
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
        if (tagsVisible) {
            // Keep UI visible when tags are shown
            resetHideUiTimer()
        }
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
        // Show UI during dismiss gesture
        if (!uiVisible) {
            showUi()
            hideUiJob?.cancel()
        }
        val translation = dy.coerceAtLeast(0f)
        viewPager.translationY = translation
        val progress = (translation / DISMISS_THRESHOLD).coerceIn(0f, 1f)
        val alpha = 1f - progress
        container.background?.mutate()?.alpha = (alpha * 255).toInt()
        closeButton.alpha = alpha
        tagButton.alpha = alpha
        shareButton.alpha = alpha
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
                    shareButton.alpha = 1f
                    tagsRecyclerView.alpha = 1f
                    uiVisible = true
                    resetHideUiTimer()
                }
                .start()
        }
        dismissing = false
    }

    private fun setupTouchActivityTracking() {
        // Set up touch activity callback on the current image view
        pagerAdapter.setTouchActivityCallback {
            resetHideUiTimer()
        }
    }
    
    override fun onResume() {
        super.onResume()
        resetHideUiTimer()
    }
    
    override fun onPause() {
        super.onPause()
        hideUiJob?.cancel()
    }
    
    private fun resetHideUiTimer() {
        hideUiJob?.cancel()
        showUi()
        
        hideUiJob = lifecycleScope.launch {
            delay(UI_HIDE_DELAY_MS)
            hideUi()
        }
    }
    
    private fun showUi() {
        if (!uiVisible) {
            uiVisible = true
            closeButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            tagButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            shareButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            if (tagsVisible) {
                tagsRecyclerView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        }
    }
    
    private fun hideUi() {
        if (uiVisible && !tagsVisible) {
            uiVisible = false
            closeButton.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
            tagButton.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
            shareButton.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
        }
    }
    
    private fun shareCurrentPin() {
        resetHideUiTimer()
        val currentPosition = viewPager.currentItem
        val pins = viewModel.pins.value ?: emptyList()
        if (currentPosition < pins.size) {
            sharePin(pins[currentPosition])
        }
    }

    private fun sharePin(pin: PinryPin) {
        val imageUrl = pin.image.fullSizeUrl ?: pin.image.bestImageUrl

        if (imageUrl.isNullOrBlank()) {
            Snackbar.make(requireView(), "Unable to share pin", Snackbar.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val packageName = context.packageName
        val view = requireView()

        lifecycleScope.launch {
            try {
                val imageData = downloadAndShareImage(imageUrl, context, packageName)
                if (imageData != null) {
                    val shareIntent = ShareCompat.IntentBuilder.from(requireActivity())
                        .setType("image/*")
                        .setStream(imageData.uri)
                        .intent
                        .apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            // Use ClipData with thumbnail for better share sheet preview
                            clipData = ClipData.newUri(context.contentResolver, "Image", imageData.uri)
                        }
                    startActivity(Intent.createChooser(shareIntent, "Share image"))
                } else {
                    Snackbar.make(view, "Failed to download image", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(view, "Error sharing image: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private data class ImageShareData(val uri: Uri, val thumbnail: Bitmap?)

    private suspend fun downloadAndShareImage(imageUrl: String, context: Context, packageName: String): ImageShareData? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val imageBytes = body.bytes()

            // Determine file extension from URL or Content-Type
            val contentType = response.header("Content-Type", "image/jpeg")
            val extension = when {
                contentType?.contains("png") == true -> "png"
                contentType?.contains("gif") == true -> "gif"
                contentType?.contains("webp") == true -> "webp"
                imageUrl.contains(".png", ignoreCase = true) -> "png"
                imageUrl.contains(".gif", ignoreCase = true) -> "gif"
                imageUrl.contains(".webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }

            // Generate thumbnail
            val thumbnail = decodeThumbnail(imageBytes, 512)

            // Save to cache directory
            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, "shared_image_${System.currentTimeMillis()}.$extension")
            FileOutputStream(imageFile).use { it.write(imageBytes) }

            // Create FileProvider URI
            val uri = FileProvider.getUriForFile(
                context,
                "$packageName.fileprovider",
                imageFile
            )

            ImageShareData(uri, thumbnail)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeThumbnail(imageBytes: ByteArray, maxSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            var inSampleSize = 1
            val width = options.outWidth
            val height = options.outHeight

            if (height > maxSize || width > maxSize) {
                val halfWidth = width / 2
                val halfHeight = height / 2

                while ((halfWidth / inSampleSize) >= maxSize && (halfHeight / inSampleSize) >= maxSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
        } catch (e: Exception) {
            null
        }
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
        private var touchActivityCallback: (() -> Unit)? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinPageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen_pin, parent, false)
            return PinPageViewHolder(view, fragment, zoomCallback, touchActivityCallback)
        }

        override fun onBindViewHolder(holder: PinPageViewHolder, position: Int) {
            holder.bind(items[position], touchActivityCallback)
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
        
        fun setTouchActivityCallback(callback: () -> Unit) {
            touchActivityCallback = callback
            // Update existing view holders by rebinding them
            items.forEachIndexed { index, _ ->
                notifyItemChanged(index)
            }
        }

        class PinPageViewHolder(
            itemView: View,
            private val fragment: FullscreenPinDialogFragment,
            private val zoomCallback: ((Boolean) -> Unit)?,
            private var touchActivityCallback: (() -> Unit)?
        ) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ZoomableImageView = itemView.findViewById(R.id.fullscreenImage)

            fun bind(pin: PinryPin, callback: (() -> Unit)?) {
                // Reset zoom when binding new pin
                imageView.resetZoom()

                // Setup touch activity callback for auto-hide
                imageView.onTouchActivity = callback

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
                    imageView.setImageResource(R.drawable.bg_pin_placeholder)
                    return
                }

                if (!thumbnailUrl.isNullOrBlank()) {
                    imageView.load(thumbnailUrl) {
                        crossfade(false)
                        listener(
                            onSuccess = { _, result ->
                                if (!fullUrl.isNullOrBlank()) {
                                    // Capture the current drawable before loading full image
                                    val currentDrawable = result.drawable
                                    loadFullImage(fullUrl, currentDrawable)
                                }
                            },
                            onError = { _, _ ->
                                if (!fullUrl.isNullOrBlank()) {
                                    // Use current drawable as fallback
                                    val currentDrawable = imageView.drawable
                                    loadFullImage(fullUrl, currentDrawable)
                                }
                            }
                        )
                    }
                } else if (!fullUrl.isNullOrBlank()) {
                    loadFullImage(fullUrl, null)
                }
            }

            private fun loadFullImage(url: String, placeholderDrawable: android.graphics.drawable.Drawable?) {
                imageView.load(url) {
                    crossfade(200) // Short crossfade for smooth transition
                    placeholder(placeholderDrawable)
                }
            }
        }
    }
}

