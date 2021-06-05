@file:JvmName("-SingletonExtensions")
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
import java.nio.ByteBuffer

/**
 * Get the singleton [ImageLoader].
 */
inline val Context.imageLoader: ImageLoader
    get() = Coil.imageLoader(this)

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
 * - [Uri] (`android.resource`, `content`, `file`, `http`, and `https` schemes)
 * - [HttpUrl]
 * - [File]
 * - [DrawableRes] [Int]
 * - [Drawable]
 * - [Bitmap]
 * - [ByteBuffer]
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
 * Cancel any in progress work associated with this [ImageView].
 */
inline fun ImageView.dispose() {
    CoilUtils.dispose(this)
}

/**
 * Get the [ImageResult] of the latest executed request attached to this view.
 */
inline val ImageView.result: ImageResult?
    get() = CoilUtils.result(this)
