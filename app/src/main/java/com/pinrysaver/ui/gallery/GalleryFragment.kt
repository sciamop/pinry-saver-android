package com.pinrysaver.ui.gallery

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pinrysaver.R
import com.pinrysaver.ui.settings.SettingsBottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class GalleryFragment : Fragment() {

    private val viewModel: GalleryViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PinAdapter
    private lateinit var loadingIndicator: ImageView
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var errorText: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var logoButton: ImageView

    private var settingsListener: SettingsBottomSheetDialogFragment.SettingsListener? = null
    private var isFastScrolling = false
    private var lastScrollEventTime = 0L
    private var promptedForEmptyState = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settingsListener = object : SettingsBottomSheetDialogFragment.SettingsListener {
            override fun onSettingsSaved(readOnly: Boolean) {
                viewModel.setReadOnlyMode(readOnly)
                viewModel.loadInitial(force = true)
                promptedForEmptyState = false
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        settingsListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_gallery, container, false)
        recyclerView = root.findViewById(R.id.pinRecyclerView)
        loadingIndicator = root.findViewById(R.id.loadingIndicator)
        errorText = root.findViewById(R.id.errorText)
        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        settingsButton = root.findViewById(R.id.settingsButton)
        logoButton = root.findViewById(R.id.logoButton)

        val orientation = resources.configuration.orientation
        val spanCount = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        adapter = PinAdapter(
            onPinClick = { position ->
                openFullscreen(position)
            },
            onPinLongClick = { position ->
                sharePin(position)
            },
            spanCount = spanCount
        )
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(false)

        val horizontalSpacing = resources.getDimensionPixelSize(R.dimen.pin_grid_spacing)
        val verticalSpacing = resources.getDimensionPixelSize(R.dimen.pin_grid_vertical_spacing)
        recyclerView.addItemDecoration(
            PinSpacingItemDecoration(spanCount, horizontalSpacing, verticalSpacing)
        )

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    val now = SystemClock.elapsedRealtime()
                    val dt = now - lastScrollEventTime
                    if (lastScrollEventTime != 0L && dt > 0) {
                        val velocity = kotlin.math.abs(dy).toFloat() / dt.toFloat()
                        isFastScrolling = velocity > FAST_SCROLL_THRESHOLD
                    }
                    lastScrollEventTime = now
                }
                if (dy >= 0) {
                    val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
                    val lastPositions = layoutManager.findLastVisibleItemPositions(null)
                    val maxPosition = lastPositions.maxOrNull() ?: return
                    viewModel.loadMoreIfNeeded(maxPosition, isFastScrolling)
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isFastScrolling = false
                    lastScrollEventTime = 0L
                }
            }
        })

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        settingsButton.setOnClickListener {
            showSettingsSheet()
        }

        logoButton.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }

        observeViewModel()

        return root
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.hasConfiguredServer()) {
            // No settings yet, prompt immediately
            view?.post { showSettingsSheet() }
        } else {
            viewModel.loadInitial()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadingIndicator.clearAnimation()
    }

    private fun observeViewModel() {
        viewModel.pins.observe(viewLifecycleOwner) { pins ->
            adapter.submitList(pins)
            if (pins.isEmpty() && !viewModel.loadingInitial.value.orFalse()) {
                errorText.isVisible = true
                errorText.text = getString(R.string.empty_gallery_message)
            } else if (pins.isNotEmpty()) {
                errorText.isGone = true
            }
        }

        viewModel.loadingInitial.observe(viewLifecycleOwner) { isLoading ->
            toggleLoadingIndicator(isLoading)
            swipeRefreshLayout.isEnabled = !isLoading
        }

        viewModel.refreshing.observe(viewLifecycleOwner) { refreshing ->
            swipeRefreshLayout.isRefreshing = refreshing
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                errorText.isVisible = adapter.itemCount == 0
                errorText.text = error
                view?.let { Snackbar.make(it, error, Snackbar.LENGTH_LONG).show() }
            } else if (adapter.itemCount > 0) {
                errorText.isGone = true
            }
        }
    }

    private fun openFullscreen(position: Int) {
        val fragmentManager = parentFragmentManager
        val existing = fragmentManager.findFragmentByTag(FullscreenPinDialogFragment.TAG)
        if (existing != null) return

        FullscreenPinDialogFragment.newInstance(position).show(fragmentManager, FullscreenPinDialogFragment.TAG)
    }

    private fun sharePin(position: Int) {
        val pins = viewModel.pins.value ?: return
        if (position < 0 || position >= pins.size) return

        val pin = pins[position]
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

    private fun showSettingsSheet() {
        if (parentFragmentManager.findFragmentByTag(SettingsBottomSheetDialogFragment.TAG) != null) {
            return
        }
        SettingsBottomSheetDialogFragment.newInstance().apply {
            listener = settingsListener
        }.show(parentFragmentManager, SettingsBottomSheetDialogFragment.TAG)
    }

    private fun toggleLoadingIndicator(show: Boolean) {
        if (show) {
            if (!loadingIndicator.isVisible) {
                loadingIndicator.visibility = View.VISIBLE
                loadingIndicator.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.pin_spin))
            }
        } else {
            loadingIndicator.clearAnimation()
            loadingIndicator.visibility = View.GONE
        }
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false

    private companion object {
        private const val FAST_SCROLL_THRESHOLD = 1.5f // pixels per millisecond (~1500 px/s)
    }
}

