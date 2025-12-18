package com.pinrysaver

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.pinrysaver.data.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var thumbnailView: ImageView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var tagsText: TextView
    private lateinit var progressIcon: ImageView
    private lateinit var closeButton: MaterialButton

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.6f)
                .build()
        )
    }

    private var finishingScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_progress)

        settingsManager = SettingsManager(this)
        bindViews()

        lifecycleScope.launch {
            processShareIntent()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressIcon.clearAnimation()
    }

    private fun bindViews() {
        thumbnailView = findViewById(R.id.shareThumbnail)
        statusText = findViewById(R.id.shareStatusText)
        detailText = findViewById(R.id.shareDetailText)
        tagsText = findViewById(R.id.shareTagsText)
        progressIcon = findViewById(R.id.shareProgressIcon)
        closeButton = findViewById(R.id.shareCloseButton)

        closeButton.setOnClickListener { finish() }
    }

    private suspend fun processShareIntent() {
        val intent = intent

        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) {
            finish()
            return
        }

        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (imageUri == null) {
            showError(getString(R.string.share_status_failure), getString(R.string.error_generic))
            return
        }

        val token = settingsManager.getApiToken()
        val boardId = settingsManager.getBoardId()
        val pinryUrl = settingsManager.getPinryUrl()

        if (token.isEmpty() || pinryUrl.isEmpty()) {
            showError(getString(R.string.share_status_failure), getString(R.string.share_detail_check_settings))
            return
        }

        updateStatus(getString(R.string.share_status_analyzing))

        try {
            val imageData = withContext(Dispatchers.IO) { readImage(imageUri) }
            val previewBitmap = imageData.preview
            val uploadBytes = imageData.bytes ?: throw IOException("Unable to read image data.")

            thumbnailView.setImageBitmap(previewBitmap)

            val tags = classifyImage(previewBitmap)
            updateTags(tags)

            startUpload(uploadBytes, boardId, token, pinryUrl, sharedUrl, tags)
        } catch (ce: CancellationException) {
            throw ce
        } catch (io: IOException) {
            showError(getString(R.string.share_status_failure), io.localizedMessage ?: getString(R.string.error_generic))
        } catch (ex: Exception) {
            showError(getString(R.string.share_status_failure), ex.localizedMessage ?: getString(R.string.error_generic))
        }
    }

    private fun startUpload(
        imageBytes: ByteArray,
        boardId: String,
        token: String,
        pinryUrl: String,
        originalUrl: String?,
        tags: List<String>
    ) {
        updateStatus(getString(R.string.share_status_uploading_image))

        val normalizedTags = tags.map { it.trim() }.filter { it.isNotEmpty() }

        PinryUploader.upload(
            imageBytes = imageBytes,
            boardId = boardId,
            token = token,
            pinryUrl = pinryUrl,
            originalUrl = originalUrl,
            tags = normalizedTags,
            callback = object : PinryUploader.UploadCallback {
                override fun onStatus(status: PinryUploader.UploadStatus) {
                    runOnUiThread {
                        when (status) {
                            PinryUploader.UploadStatus.PREPARING -> updateStatus(getString(R.string.share_status_preparing))
                            PinryUploader.UploadStatus.UPLOADING_IMAGE -> updateStatus(getString(R.string.share_status_uploading_image))
                            PinryUploader.UploadStatus.CREATING_PIN -> updateStatus(getString(R.string.share_status_creating_pin))
                        }
                    }
                }

                override fun onSuccess() {
                    runOnUiThread {
                        showSuccess()
                        scheduleFinish()
                    }
                }

                override fun onFailure(error: String) {
                    runOnUiThread {
                        showError(getString(R.string.share_status_failure), error)
                    }
                }
            }
        )
    }

    private suspend fun classifyImage(bitmap: Bitmap?): List<String> = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext emptyList<String>()
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            labels
                .sortedByDescending { it.confidence }
                .map { it.text.lowercase(Locale.getDefault()) }
                .distinct()
                .take(6)
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private data class ImageData(val bytes: ByteArray?, val preview: Bitmap?)

    private fun readImage(uri: Uri): ImageData {
        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open shared image.")

        val bytes = inputStream.use { it.readBytes() }
        val preview = decodeSampledBitmap(bytes, 720)
        return ImageData(bytes, preview)
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var inSampleSize = 1
        val width = options.outWidth
        val height = options.outHeight
        if (height > maxSize || width > maxSize) {
            var halfWidth = width / 2
            var halfHeight = height / 2

            while ((halfWidth / inSampleSize) >= maxSize && (halfHeight / inSampleSize) >= maxSize) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun updateStatus(message: String, detail: String? = null, showSpinner: Boolean = true, iconRes: Int? = null) {
        statusText.text = message

        if (detail.isNullOrBlank()) {
            detailText.isVisible = false
        } else {
            detailText.isVisible = true
            detailText.text = detail
        }

        if (showSpinner) {
            progressIcon.setImageResource(R.drawable.pinstle_bitmap_icon)
            progressIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_color))
            progressIcon.visibility = View.VISIBLE
            progressIcon.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pin_spin))
        } else {
            progressIcon.clearAnimation()
            if (iconRes != null) {
                progressIcon.setImageResource(iconRes)
                progressIcon.clearColorFilter()
                progressIcon.visibility = View.VISIBLE
            } else {
                progressIcon.visibility = View.GONE
            }
        }
    }

    private fun updateTags(tags: List<String>) {
        if (tags.isEmpty()) {
            tagsText.isVisible = true
            tagsText.text = getString(R.string.share_tags_none)
        } else {
            tagsText.isVisible = true
            val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) {
                tagsText.text = getString(R.string.share_tags_none)
            } else {
                tagsText.text = getString(R.string.share_tags_prefix, cleaned.joinToString(", "))
            }
        }
    }

    private fun showSuccess() {
        updateStatus(
            message = getString(R.string.share_status_success),
            detail = null,
            showSpinner = false,
            iconRes = R.drawable.ic_success
        )
        closeButton.visibility = View.GONE
    }

    private fun showError(title: String, detail: String?) {
        updateStatus(title, detail, showSpinner = false, iconRes = R.drawable.ic_error)
        closeButton.visibility = View.VISIBLE
    }

    private fun scheduleFinish() {
        if (finishingScheduled) return
        finishingScheduled = true
        lifecycleScope.launch {
            delay(1500)
            finish()
        }
    }

}
