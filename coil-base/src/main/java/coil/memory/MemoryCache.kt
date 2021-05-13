@file:Suppress("unused")

package coil.memory

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.annotation.FloatRange
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.util.Utils
import kotlinx.parcelize.Parcelize

/**
 * An in-memory cache of recently loaded images.
 */
interface MemoryCache {

    /** The current size of the cache in bytes. */
    val size: Int

    /** The maximum size of the cache in bytes. */
    val maxSize: Int

    /** Get the [Value] associated with [key]. */
    operator fun get(key: Key): Value?

    /** Set the [Value] associated with [key]. */
    operator fun set(key: Key, value: Value)

    /**
     * Remove the [Value] referenced by [key].
     *
     * @return 'true' if [key] was present in the cache. Else, return 'false'.
     */
    fun remove(key: Key): Boolean

    /** Remove all values from the memory cache. */
    fun clear()

    /** @see ComponentCallbacks2.onTrimMemory */
    fun trimMemory(level: Int)

    /** The cache key for an image in the memory cache. */
    sealed class Key : Parcelable {

        companion object {
            /** Create a simple memory cache key. */
            @JvmStatic
            @JvmName("create")
            operator fun invoke(value: String): Key = Simple(value)
        }

        /** A simple memory cache key that wraps a [String]. Create new instances using [invoke]. */
        @Parcelize
        internal data class Simple(val value: String) : Key()

        /**
         * A complex memory cache key. Instances can only be created by an [ImageLoader]'s
         * image pipeline and cannot be created directly.
         *
         * This class is an implementation detail and its fields may change in future releases.
         *
         * @see ImageRequest.Listener.onSuccess
         * @see SuccessResult
         */
        @Parcelize
        internal data class Complex(
            val base: String,
            val transformations: List<String>,
            val size: Size?,
            val parameters: Map<String, String>
        ) : Key()
    }

    /** The value for an image in the memory cache. */
    data class Value(
        val bitmap: Bitmap,
        val isSampled: Boolean
    )

    class Builder(private val context: Context) {

        private var maxSizePercent = Utils.getDefaultAvailableMemoryPercentage(context)
        private var maxSizeBytes = -1
        private var strongReferencesEnabled = true
        private var weakReferencesEnabled = true

        /**
         * Set the maximum size of the memory cache in bytes.
         */
        fun maxSizeBytes(size: Int) = apply {
            require(size >= 0) { "size must be >= 0." }
            this.maxSizePercent = Double.MIN_VALUE
            this.maxSizeBytes = size
        }

        /**
         * Set the maximum size of the memory cache as a percentage of this application's available memory.
         */
        fun maxSizePercent(@FloatRange(from = 0.0, to = 1.0) percent: Double) = apply {
            require(percent in 0.0..1.0) { "size must be in the range [0.0, 1.0]." }
            this.maxSizeBytes = Int.MIN_VALUE
            this.maxSizePercent = percent
        }

        /**
         * Enables/disables strong reference tracking of values added to this memory cache.
         */
        fun strongReferencesEnabled(enable: Boolean) = apply {
            this.strongReferencesEnabled = enable
        }

        /**
         * Enables/disables weak reference tracking of values added to this memory cache.
         * Weak references do not contribute to the current size of the memory cache.
         * This ensures that if an image is still in memory it will be returned from the memory cache.
         */
        fun weakReferencesEnabled(enable: Boolean) = apply {
            this.weakReferencesEnabled = enable
        }

        /**
         * Create a new [MemoryCache] instance.
         */
        fun build(): MemoryCache {
            val weakMemoryCache = if (weakReferencesEnabled) {
                RealWeakMemoryCache()
            } else {
                EmptyWeakMemoryCache()
            }
            val maxSize = if (maxSizePercent >= 0) {
                Utils.calculateAvailableMemorySize(context, maxSizePercent)
            } else {
                maxSizeBytes
            }
            val strongMemoryCache = if (maxSize > 0) {
                RealStrongMemoryCache(maxSize, weakMemoryCache)
            } else {
                EmptyStrongMemoryCache(weakMemoryCache)
            }
            return RealMemoryCache(strongMemoryCache, weakMemoryCache)
        }
    }

    companion object {
        /** Create a new [MemoryCache] without configuration. */
        @JvmStatic
        @JvmName("create")
        operator fun invoke(context: Context) = Builder(context).build()
    }
}
