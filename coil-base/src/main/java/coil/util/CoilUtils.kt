package coil.util

import android.content.Context
import android.view.View
import coil.request.Disposable
import coil.request.ImageResult
import okhttp3.Cache

/** Public utility methods for Coil. */
object CoilUtils {

    /**
     * Create an OkHttp disk cache with a reasonable default size and location.
     */
    @JvmStatic
    fun createDefaultCache(context: Context): Cache {
        val cacheDirectory = Utils.getDefaultDiskCacheDirectory(context)
        val cacheSize = Utils.calculateDiskCacheSize(cacheDirectory)
        return Cache(cacheDirectory, cacheSize)
    }

    /**
     * Cancel any in progress requests attached to [view] and clear any associated resources.
     *
     * NOTE: Typically you should use [Disposable.dispose] to cancel requests and clear resources,
     * however this method is provided for convenience.
     */
    @JvmStatic
    fun clear(view: View) {
        view.requestManager.clearCurrentRequest()
    }

    /**
     * Get the metadata of the successful request attached to this view.
     */
    @JvmStatic
    fun result(view: View): ImageResult? {
        return view.requestManager.result
    }
}
