@file:JvmName("-Extensions")
@file:Suppress("NOTHING_TO_INLINE", "unused")

package coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.request.Disposable
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.util.CoilUtils
import okhttp3.HttpUrl
import java.io.File

/**
 * Get the singleton [ImageLoader].
 */
inline val Context.imageLoader: ImageLoader
    @JvmName("imageLoader") get() = Coil.imageLoader(this)

/**
 * Load the image referenced by [data] and set it on this [ImageView].
 *
 * Example:
 * ```
 * imageView.load("https://www.example.com/image.jpg") {
 *     crossfade(true)
 *     transformations(CircleCropTransformation())
 * }
 * ```
 *
 * The default supported [data] types  are:
 *
 * - [String] (treated as a [Uri])
 * - [Uri] (`android.resource`, `content`, `file`, `http`, and `https` schemes only)
 * - [HttpUrl]
 * - [File]
 * - [DrawableRes] [Int]
 * - [Drawable]
 * - [Bitmap]
 *
 * @param data The data to load.
 * @param imageLoader The [ImageLoader] that will be used to enqueue the [ImageRequest].
 *  By default, the singleton [ImageLoader] will be used.
 * @param builder An optional lambda to configure the [ImageRequest].
 */
inline fun ImageView.load(
    data: Any?,
    imageLoader: ImageLoader = context.imageLoader,
    builder: ImageRequest.Builder.() -> Unit = {}
): Disposable {
    val request = ImageRequest.Builder(context)
        .data(data)
        .target(this)
        .apply(builder)
        .build()
    return imageLoader.enqueue(request)
}

/**
 * Cancel any in progress requests and clear all resources associated with this [ImageView].
 */
inline fun ImageView.clear() {
    CoilUtils.clear(this)
}

/**
 * Get the metadata of the successful request attached to this view.
 */
inline val ImageView.metadata: ImageResult.Metadata?
    @JvmName("metadata") get() = CoilUtils.metadata(this)
