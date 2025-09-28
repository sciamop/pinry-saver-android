package com.pinrysaver

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object PinryUploader {
    
    private const val TAG = "PinryUploader"
    
    interface UploadCallback {
        fun onSuccess()
        fun onFailure(error: String)
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    fun upload(
        imageBytes: ByteArray,
        boardId: String,
        token: String,
        pinryUrl: String,
        callback: UploadCallback
    ) {
        Log.d(TAG, "Starting upload - Board ID: $boardId, URL: $pinryUrl, Image size: ${imageBytes.size} bytes")
        
        try {
            // Create a temporary file for the image
            val tempFile = File.createTempFile("pinry_image", ".jpg")
            tempFile.writeBytes(imageBytes)
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
            
            // Step 1: Upload image to /api/v2/images/
            val imageRequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image", 
                    "image.jpg",
                    tempFile.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            
            val imageUrl = "$pinryUrl/api/v2/images/"
            Log.d(TAG, "Uploading image to: $imageUrl")
            
            val imageRequest = Request.Builder()
                .url(imageUrl)
                .addHeader("Authorization", "Token $token")
                .post(imageRequestBody)
                .build()
            
            client.newCall(imageRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Image upload network error", e)
                    callback.onFailure("Network error: ${e.message}")
                    tempFile.delete()
                }
                
                override fun onResponse(call: Call, response: Response) {
                    tempFile.delete()
                    Log.d(TAG, "Image upload response: ${response.code} ${response.message}")
                    
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            Log.d(TAG, "Image upload response body: $responseBody")
                            
                            val imageId = extractImageId(responseBody)
                            val imageUrl = extractImageUrl(responseBody)
                            Log.d(TAG, "Extracted image ID: $imageId, URL: $imageUrl")
                            
                            if (imageId != null) {
                                // Step 2: Create pin with image ID
                                createPin(imageId, boardId, token, pinryUrl, callback)
                            } else {
                                Log.e(TAG, "Failed to extract image ID from response: $responseBody")
                                callback.onFailure("Failed to get image ID from response")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing image response", e)
                            callback.onFailure("Error parsing image response: ${e.message}")
                        }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "Image upload failed: ${response.code} ${response.message}")
                        Log.e(TAG, "Error body: $errorBody")
                        callback.onFailure("Image upload failed: ${response.code} ${response.message} - $errorBody")
                    }
                    response.close()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing upload", e)
            callback.onFailure("Error preparing upload: ${e.message}")
        }
    }
    
    private fun extractImageIdFromUrl(imageUrl: String): String? {
        // Extract image ID from the URL path
        return try {
            val idMatch = Regex("/media/[^/]+/([^/]+)/").find(imageUrl)
            idMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractImageId(responseBody: String?): String? {
        // Simple JSON parsing to extract image ID
        return responseBody?.let { body ->
            val idMatch = Regex("\"id\"\\s*:\\s*(\\d+)").find(body)
            idMatch?.groupValues?.get(1)
        }
    }
    
    private fun extractImageUrl(responseBody: String?): String? {
        // Extract the image URL from the response
        return responseBody?.let { body ->
            val urlMatch = Regex("\"image\"\\s*:\\s*\"([^\"]+)\"").find(body)
            urlMatch?.groupValues?.get(1)
        }
    }
    
    private fun createPin(imageId: String, boardId: String, token: String, pinryUrl: String, callback: UploadCallback) {
        Log.d(TAG, "Creating pin with image ID: $imageId, board: $boardId")
        
        // Use JSON with the correct parameter name
        val pinData = """
            {
                "image_by_id": $imageId,
                "board": "${escapeJsonString(boardId)}",
                "url": "",
                "description": "",
                "private": false,
                "referer": ""
            }
        """.trimIndent()
        
        Log.d(TAG, "Creating pin with data: $pinData")
        
        val requestBody = pinData.toRequestBody("application/json".toMediaType())
        
        val pinUrl = "$pinryUrl/api/v2/pins/"
        Log.d(TAG, "Creating pin at: $pinUrl")
        
        val request = Request.Builder()
            .url(pinUrl)
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Pin creation network error", e)
                callback.onFailure("Network error creating pin: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Pin creation response: ${response.code} ${response.message}")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Pin created successfully!")
                    callback.onSuccess()
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Pin creation failed: ${response.code} ${response.message}")
                    Log.e(TAG, "Error body: $errorBody")
                    callback.onFailure("Pin creation failed: ${response.code} ${response.message} - $errorBody")
                }
                response.close()
            }
        })
    }
    
    fun createPinFromUrl(
        imageUrl: String,
        boardId: String,
        token: String,
        pinryUrl: String,
        callback: UploadCallback
    ) {
        Log.d(TAG, "Creating pin from URL - URL: $imageUrl, Board ID: $boardId")
        
        val domain = extractDomainFromUrl(imageUrl)
        Log.d(TAG, "Extracted domain: $domain")
        
        val pinData = """
            {
                "url": "${escapeJsonString(imageUrl)}",
                "board": "${escapeJsonString(boardId)}",
                "description": "${escapeJsonString(domain)}",
                "private": false,
                "referer": ""
            }
        """.trimIndent()
        
        Log.d(TAG, "Creating pin with data: $pinData")
        
        val requestBody = pinData.toRequestBody("application/json".toMediaType())
        
        val pinUrl = "$pinryUrl/api/v2/pins/"
        Log.d(TAG, "Creating pin at: $pinUrl")
        
        val request = Request.Builder()
            .url(pinUrl)
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "URL pin creation network error", e)
                callback.onFailure("Network error creating pin from URL: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "URL pin creation response: ${response.code} ${response.message}")
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Pin created from URL successfully!")
                    callback.onSuccess()
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "URL pin creation failed: ${response.code} ${response.message}")
                    Log.e(TAG, "Error body: $errorBody")
                    callback.onFailure("Pin creation from URL failed: ${response.code} ${response.message} - $errorBody")
                }
                response.close()
            }
        })
    }
    
    
    private fun extractDomainFromUrl(imageUrl: String): String {
        return try {
            val url = java.net.URL(imageUrl)
            val domain = url.host
            
            if (domain.isNotEmpty()) {
                domain
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting domain from URL: $imageUrl", e)
            ""
        }
    }
    
    private fun escapeJsonString(input: String): String {
        return input
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t")   // Escape tabs
            .replace("\b", "\\b")   // Escape backspace
            .replace("\u000C", "\\f") // Escape form feed
    }
}
