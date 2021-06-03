package coil.request

import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.memory.MemoryCache
import java.io.File

/**
 * Represents the result of an executed [ImageRequest].
 *
 * @see ImageLoader.execute
 */
sealed class ImageResult {
    abstract val drawable: Drawable?
    abstract val request: ImageRequest
}

/**
 * Indicates that the request completed successfully.
 *
 * @param drawable The success drawable.
 * @param request The request that was executed to create this result.
 * @param dataSource The data source that the image was loaded from.
 * @param memoryCacheKey The cache key for the image in the memory cache.
 *  It is 'null' if the image was not written to the memory cache.
 * @param file A direct reference to where this image was stored on disk when it was decoded.
 *  It is 'null' if the image is not stored on disk. NOTE: You should always check [File.exists] before using
 *  the file as it may have been deleted since the image was decoded.
 * @param isSampled 'true' if the image is sampled (i.e. loaded into memory at less than its original size).
 * @param isPlaceholderMemoryCacheKeyPresent 'true' if the request's [ImageRequest.placeholderMemoryCacheKey]
 *  was present in the memory cache and was set as the placeholder.
 */
data class SuccessResult(
    override val drawable: Drawable,
    override val request: ImageRequest,
    val dataSource: DataSource,
    val memoryCacheKey: MemoryCache.Key?,
    val file: File?,
    val isSampled: Boolean,
    val isPlaceholderMemoryCacheKeyPresent: Boolean
) : ImageResult()

/**
 * Indicates that an error occurred while executing the request.
 *
 * @param drawable The error drawable.
 * @param request The request that was executed to create this result.
 * @param throwable The error that failed the request.
 */
data class ErrorResult(
    override val drawable: Drawable?,
    override val request: ImageRequest,
    val throwable: Throwable
) : ImageResult()
