@file:JvmName("OkHttpClients")
@file:Suppress("unused")

package coil.network

import android.content.Context
import coil.ImageLoader
import coil.util.CoilUtils
import coil.util.removeIfIndices
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File

/**
 * A convenience function to set the default image loader disk cache for this [OkHttpClient].
 */
fun OkHttpClient.Builder.imageLoaderDiskCache(context: Context) =
    imageLoaderDiskCache(CoilUtils.createDiskCache(context))

/**
 * Sets the disk cache for this [OkHttpClient] and adds extensions so an [ImageLoader] can read its disk cache.
 *
 * @param diskCache The disk cache to use with this [OkHttpClient].
 */
fun OkHttpClient.Builder.imageLoaderDiskCache(diskCache: Cache?) = apply {
    cache(diskCache)
    interceptors().removeIfIndices { it is DiskCacheInterceptor }
    if (diskCache != null) interceptors() += DiskCacheInterceptor(diskCache)
}

/**
 * Tags [Response]s with their associated disk cache file.
 * This must be the last non-network interceptor in the chain as it relies on
 * implementation details of the [Cache] class to determine the file name..
 */
private class DiskCacheInterceptor(private val diskCache: Cache) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        val request = response.request
        val cacheFile = CoilUtils.getDiskCacheFile(diskCache, request.url)
        if (cacheFile.exists()) {
            val newRequest = request.newBuilder()
                .tag(CacheFile::class.java, CacheFile(cacheFile))
                .build()
            response = response.newBuilder()
                .request(newRequest)
                .build()
        }
        return response
    }
}

/** Use a private class so its tag is guaranteed not to be overwritten. */
private class CacheFile(val file: File)

/** Get the cache file on disk for this response. */
internal val Response.cacheFile: File?
    get() = request.tag(CacheFile::class.java)?.file

/**
 * Fail loudly if it can be determined that this is an [OkHttpClient]
 * that was built without calling [imageLoaderDiskCache].
 */
internal fun Call.Factory.assertHasDiskCacheInterceptor() {
    if (this !is OkHttpClient) return
    check(interceptors.lastOrNull() is DiskCacheInterceptor) {
        "The ImageLoader is unable to read the disk cache of the OkHttpClient provided to it." +
            "Set `OkHttpClient.Builder.imageLoaderDiskCache` **after adding any interceptors** to fix this."
    }
}
