package com.pinrysaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pinrysaver.data.SettingsManager
import java.io.InputStream

class ShareReceiverActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        handleShareIntent()
    }
    
    private fun handleShareIntent() {
        val intent = intent
        Log.d("ShareReceiver", "Intent action: ${intent.action}")
        Log.d("ShareReceiver", "Intent type: ${intent.type}")
        Log.d("ShareReceiver", "Intent extras: ${intent.extras?.keySet()?.joinToString()}")
        
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val imageUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
            
            Log.d("ShareReceiver", "Image URI: $imageUri")
            Log.d("ShareReceiver", "Image URL: $imageUrl")
            
            if (imageUri != null) {
                val token = settingsManager.getApiToken()
                val boardId = settingsManager.getBoardId()
                val pinryUrl = settingsManager.getPinryUrl()
                
                if (token.isEmpty() || boardId.isEmpty() || pinryUrl.isEmpty()) {
                    Toast.makeText(this, "Please configure Pinry settings first", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                
                try {
                    if (imageUrl != null && imageUrl.isNotEmpty()) {
                        // We have both URL and image - try URL approach first
                        Log.d("ShareReceiver", "Using URL approach: $imageUrl")
                        createPinFromUrl(imageUrl, boardId, token, pinryUrl)
                    } else {
                        // Only have image bytes - use upload approach
                        Log.d("ShareReceiver", "Using upload approach")
                        val imageBytes = readImageBytes(imageUri)
                        uploadImage(imageBytes, boardId, token, pinryUrl, imageUrl)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error reading image: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "No image found in share", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }
    
    private fun readImageBytes(uri: Uri): ByteArray {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        return inputStream?.readBytes() ?: throw Exception("Could not read image")
    }
    
    private fun createPinFromUrl(imageUrl: String, boardId: String, token: String, pinryUrl: String) {
        PinryUploader.createPinFromUrl(
            imageUrl = imageUrl,
            boardId = boardId,
            token = token,
            pinryUrl = pinryUrl,
            callback = object : PinryUploader.UploadCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Toast.makeText(this@ShareReceiverActivity, "Pin saved successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }, 100)
                    }
                }
                
                override fun onFailure(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@ShareReceiverActivity, "Failed to save pin: $error", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        )
    }
    
    private fun uploadImage(imageBytes: ByteArray, boardId: String, token: String, pinryUrl: String, originalUrl: String?) {
        PinryUploader.upload(
            imageBytes = imageBytes,
            boardId = boardId,
            token = token,
            pinryUrl = pinryUrl,
            originalUrl = originalUrl,
            callback = object : PinryUploader.UploadCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Toast.makeText(this@ShareReceiverActivity, "Pin saved successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }, 100)
                    }
                }
                
                override fun onFailure(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@ShareReceiverActivity, "Failed to save pin: $error", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        )
    }
}
