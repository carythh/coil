package coil.intercept

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.collection.arrayMapOf
import coil.EventListener
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.DecodeResult
import coil.decode.DecodeUtils
import coil.fetch.DrawableResult
import coil.fetch.DrawableUtils
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.memory.MemoryCache
import coil.memory.RequestService
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.Options
import coil.request.SuccessResult
import coil.size.OriginalSize
import coil.size.PixelSize
import coil.size.Size
import coil.transform.Transformation
import coil.util.Logger
import coil.util.Utils
import coil.util.allowInexactSize
import coil.util.closeQuietly
import coil.util.foldIndices
import coil.util.forEachIndices
import coil.util.get
import coil.util.log
import coil.util.safeConfig
import coil.util.toDrawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** The last interceptor in the chain which executes the [ImageRequest]. */
internal class EngineInterceptor(
    private val imageLoader: ImageLoader,
    private val requestService: RequestService,
    private val logger: Logger?
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        try {
            // This interceptor uses some internal APIs.
            check(chain is RealInterceptorChain)

            val request = chain.request
            val context = request.context
            val data = request.data
            val size = chain.size
            val eventListener = chain.eventListener
            val options = requestService.options(request, size)

            // Perform any data mapping.
            eventListener.mapStart(request, data)
            val mappedData = imageLoader.components.map(data, options)
            eventListener.mapEnd(request, mappedData)

            // Check the memory cache.
            val memoryCacheKey = request.memoryCacheKey
                ?: newMemoryCacheKey(mappedData, options, request, size, eventListener)
            val value = if (request.memoryCachePolicy.readEnabled) imageLoader.memoryCache[memoryCacheKey] else null

            // Short circuit if the cached bitmap is valid.
            if (value != null && isCachedValueValid(memoryCacheKey, value, request, size)) {
                return SuccessResult(
                    drawable = value.bitmap.toDrawable(context),
                    request = request,
                    memoryCacheKey = memoryCacheKey,
                    diskCacheFile = null,
                    isSampled = value.isSampled,
                    dataSource = DataSource.MEMORY_CACHE,
                    isPlaceholderMemoryCacheKeyPresent = chain.cached != null
                )
            }

            // Fetch, decode, transform, and cache the image.
            return withContext(Dispatchers.Unconfined) {
                // Fetch and decode the image.
                val (drawable, isSampled, dataSource) = execute(mappedData, request, options, eventListener)

                // Cache the result in the memory cache.
                val isMemoryCached = writeToMemoryCache(memoryCacheKey, request, drawable, isSampled)

                // Return the result.
                SuccessResult(
                    drawable = drawable,
                    request = request,
                    memoryCacheKey = memoryCacheKey.takeIf { isMemoryCached },
                    diskCacheFile = File(""), // TODO
                    isSampled = isSampled,
                    dataSource = dataSource,
                    isPlaceholderMemoryCacheKeyPresent = chain.cached != null
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            } else {
                return requestService.errorResult(chain.request, throwable)
            }
        }
    }

    /** Create the memory cache key for this request. */
    @VisibleForTesting
    internal fun newMemoryCacheKey(
        mappedData: Any,
        options: Options,
        request: ImageRequest,
        size: Size,
        eventListener: EventListener
    ): MemoryCache.Key? {
        eventListener.keyStart(request, mappedData)
        val base = imageLoader.components.key(mappedData, options)
        eventListener.keyEnd(request, base)
        if (base == null) return null

        val extras = arrayMapOf<String, String>()
        extras.putAll(request.parameters.cacheKeys())
        if (request.transformations.isNotEmpty()) {
            val transformations = StringBuilder()
            request.transformations.forEachIndices {
                transformations.append(it.cacheKey).append('~')
            }
            extras[MEMORY_CACHE_KEY_TRANSFORMATIONS] = transformations.toString()

            if (size is PixelSize) {
                extras[MEMORY_CACHE_KEY_WIDTH] = size.width.toString()
                extras[MEMORY_CACHE_KEY_HEIGHT] = size.height.toString()
            }
        }
        return MemoryCache.Key(base, extras)
    }

    /** Return 'true' if [cacheValue] satisfies the [request]. */
    @VisibleForTesting
    internal fun isCachedValueValid(
        cacheKey: MemoryCache.Key?,
        cacheValue: MemoryCache.Value,
        request: ImageRequest,
        size: Size
    ): Boolean {
        // Ensure the size of the cached bitmap is valid for the request.
        if (!isSizeValid(cacheKey, cacheValue, request, size)) {
            return false
        }

        // Ensure we don't return a hardware bitmap if the request doesn't allow it.
        if (!requestService.isConfigValidForHardware(request, cacheValue.bitmap.safeConfig)) {
            logger?.log(TAG, Log.DEBUG) {
                "${request.data}: Cached bitmap is hardware-backed, which is incompatible with the request."
            }
            return false
        }

        // Else, the cached drawable is valid and we can short circuit the request.
        return true
    }

    /** Return 'true' if [cacheValue]'s size satisfies the [request]. */
    private fun isSizeValid(
        cacheKey: MemoryCache.Key?,
        cacheValue: MemoryCache.Value,
        request: ImageRequest,
        size: Size
    ): Boolean {
        when (size) {
            is OriginalSize -> {
                if (cacheValue.isSampled) {
                    logger?.log(TAG, Log.DEBUG) {
                        "${request.data}: Requested original size, but cached image is sampled."
                    }
                    return false
                }
            }
            is PixelSize -> {
                var cachedWidth = cacheKey?.extras?.get(MEMORY_CACHE_KEY_WIDTH)?.toInt()
                var cachedHeight = cacheKey?.extras?.get(MEMORY_CACHE_KEY_HEIGHT)?.toInt()
                if (cachedWidth == null || cachedHeight == null) {
                    val bitmap = cacheValue.bitmap
                    cachedWidth = bitmap.width
                    cachedHeight = bitmap.height
                }

                // Short circuit the size check if the size is at most 1 pixel off in either dimension.
                // This accounts for the fact that downsampling can often produce images with one dimension
                // at most one pixel off due to rounding.
                if (abs(cachedWidth - size.width) <= 1 && abs(cachedHeight - size.height) <= 1) {
                    return true
                }

                val multiple = DecodeUtils.computeSizeMultiplier(
                    srcWidth = cachedWidth,
                    srcHeight = cachedHeight,
                    dstWidth = size.width,
                    dstHeight = size.height,
                    scale = request.scale
                )
                if (multiple != 1.0 && !request.allowInexactSize) {
                    logger?.log(TAG, Log.DEBUG) {
                        "${request.data}: Cached image's request size ($cachedWidth, $cachedHeight) does not " +
                            "exactly match the requested size (${size.width}, ${size.height}, ${request.scale})."
                    }
                    return false
                }
                if (multiple > 1.0 && cacheValue.isSampled) {
                    logger?.log(TAG, Log.DEBUG) {
                        "${request.data}: Cached image's request size ($cachedWidth, $cachedHeight) is smaller " +
                            "than the requested size (${size.width}, ${size.height}, ${request.scale})."
                    }
                    return false
                }
            }
        }

        return true
    }

    /** Execute the [Fetcher], decode any data into a [Drawable], and apply any [Transformation]s. */
    private suspend inline fun execute(
        mappedData: Any,
        request: ImageRequest,
        options: Options,
        eventListener: EventListener
    ): DrawableResult {
        var newOptions = options
        var fetchResult: FetchResult? = null
        val drawableResult = try {
            // Fetch the data.
            fetchResult = withContext(request.fetcherDispatcher) {
                if (!requestService.allowHardwareWorkerThread(options)) {
                    newOptions = options.copy(config = Bitmap.Config.ARGB_8888)
                }
                fetch(request, mappedData, newOptions, eventListener)
            }

            // Decode the data.
            when (fetchResult) {
                is SourceResult -> withContext(request.decoderDispatcher) {
                    decode(fetchResult, request, mappedData, newOptions, eventListener)
                }
                is DrawableResult -> fetchResult
            }
        } finally {
            // Ensure the fetch result's source is always closed.
            (fetchResult as? SourceResult)?.source?.closeQuietly()
        }

        // Apply any transformations and prepare to draw.
        val finalResult = applyTransformations(drawableResult, request, newOptions, eventListener)
        (finalResult.drawable as? BitmapDrawable)?.bitmap?.prepareToDraw()
        return finalResult
    }

    private suspend inline fun fetch(
        request: ImageRequest,
        mappedData: Any,
        options: Options,
        eventListener: EventListener
    ): FetchResult {
        val fetchResult: FetchResult
        var searchIndex = 0
        while (true) {
            val pair = imageLoader.components.newFetcher(mappedData, options, imageLoader, searchIndex)
            checkNotNull(pair) { "Unable to create a fetcher that supports: $mappedData" }
            val fetcher = pair.first
            searchIndex = pair.second + 1

            eventListener.fetchStart(request, fetcher, options)
            val result = fetcher.fetch()
            try {
                eventListener.fetchEnd(request, fetcher, options, result)
            } catch (throwable: Throwable) {
                // Ensure the source is closed if an exception occurs before returning the result.
                (result as? SourceResult)?.source?.closeQuietly()
                throw throwable
            }

            if (result != null) {
                fetchResult = result
                break
            }
        }
        return fetchResult
    }

    private suspend inline fun decode(
        fetchResult: SourceResult,
        request: ImageRequest,
        mappedData: Any,
        options: Options,
        eventListener: EventListener
    ): DrawableResult {
        val decodeResult: DecodeResult
        var searchIndex = 0
        while (true) {
            val pair = imageLoader.components.newDecoder(fetchResult, options, imageLoader, searchIndex)
            checkNotNull(pair) { "Unable to create a decoder that supports: $mappedData" }
            val decoder = pair.first
            searchIndex = pair.second + 1

            eventListener.decodeStart(request, decoder, options)
            val result = decoder.decode()
            eventListener.decodeEnd(request, decoder, options, result)

            if (result != null) {
                decodeResult = result
                break
            }
        }

        // Combine the fetch and decode operations' results.
        return DrawableResult(
            drawable = decodeResult.drawable,
            isSampled = decodeResult.isSampled,
            dataSource = fetchResult.dataSource
        )
    }

    /** Apply any [Transformation]s and return an updated [DrawableResult]. */
    @VisibleForTesting
    internal suspend inline fun applyTransformations(
        result: DrawableResult,
        request: ImageRequest,
        options: Options,
        eventListener: EventListener
    ): DrawableResult {
        val transformations = request.transformations
        if (transformations.isEmpty()) return result

        // Apply the transformations.
        return withContext(request.transformationDispatcher) {
            val input = convertDrawableToBitmap(result.drawable, options, transformations)
            eventListener.transformStart(request, input)
            val output = transformations.foldIndices(input) { bitmap, transformation ->
                transformation.transform(bitmap, options.size).also { ensureActive() }
            }
            eventListener.transformEnd(request, output)
            result.copy(drawable = output.toDrawable(request.context))
        }
    }

    /** Write [drawable] to the memory cache. Return 'true' if it was added to the cache. */
    private fun writeToMemoryCache(
        key: MemoryCache.Key?,
        request: ImageRequest,
        drawable: Drawable,
        isSampled: Boolean
    ): Boolean {
        if (!request.memoryCachePolicy.writeEnabled) {
            return false
        }

        if (key != null) {
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                imageLoader.memoryCache[key] = MemoryCache.Value(bitmap, isSampled)
                return true
            }
        }
        return false
    }

    /** Convert [drawable] to a [Bitmap]. */
    private fun convertDrawableToBitmap(
        drawable: Drawable,
        options: Options,
        transformations: List<Transformation>
    ): Bitmap {
        if (drawable is BitmapDrawable) {
            var bitmap = drawable.bitmap
            val config = bitmap.safeConfig
            if (config !in Utils.VALID_TRANSFORMATION_CONFIGS) {
                logger?.log(TAG, Log.INFO) {
                    "Converting bitmap with config $config to apply transformations: $transformations"
                }
                bitmap = DrawableUtils.convertToBitmap(drawable, options.config, options.size,
                    options.scale, options.allowInexactSize)
            }
            return bitmap
        }

        logger?.log(TAG, Log.INFO) {
            val type = drawable::class.java.canonicalName
            "Converting drawable of type $type to apply transformations: $transformations"
        }
        return DrawableUtils.convertToBitmap(drawable, options.config, options.size,
            options.scale, options.allowInexactSize)
    }

    companion object {
        private const val TAG = "EngineInterceptor"
        private const val MEMORY_CACHE_KEY_WIDTH = "coil#width"
        private const val MEMORY_CACHE_KEY_HEIGHT = "coil#height"
        private const val MEMORY_CACHE_KEY_TRANSFORMATIONS = "coil#transformations"
    }
}
