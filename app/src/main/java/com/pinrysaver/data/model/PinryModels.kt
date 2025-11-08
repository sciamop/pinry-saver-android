package com.pinrysaver.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class PinryPinsResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<PinryPin>? = emptyList()
) : Parcelable

@Parcelize
data class PinryPin(
    val id: Int,
    val url: String?,
    val description: String?,
    val image: PinryImage,
    val submitter: PinrySubmitter?,
    val tags: List<String>?
) : Parcelable

@Parcelize
data class PinryImage(
    val id: Int?,
    val image: String?,
    val width: Int?,
    val height: Int?,
    val thumbnail: PinryImageSize?,
    val square: PinryImageSize?,
    val standard: PinryImageSize?
) : Parcelable {
    val bestImageUrl: String?
        get() = thumbnail?.url ?: square?.url ?: standard?.url ?: image

    val fullSizeUrl: String?
        get() = image
}

@Parcelize
data class PinryImageSize(
    val image: String,
    val width: Int?,
    val height: Int?
) : Parcelable {
    val url: String
        get() = image
}

@Parcelize
data class PinrySubmitter(
    val username: String?,
    val email: String?,
    val token: String?,
    val gravatar: String?,
    @SerializedName("resource_link") val resourceLink: String?
) : Parcelable

