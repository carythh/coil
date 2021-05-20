@file:JvmName("OkHttpClients")

package coil.util

import android.content.Context
import coil.ImageLoader
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File

/**
 * Builds an [OkHttpClient] with extensions for use with an [ImageLoader].
 *
 * @param context A context.
 * @param diskCache The disk cache to use with this [OkHttpClient].
 */
@JvmOverloads
fun OkHttpClient.Builder.buildForImageLoader(
    context: Context,
    diskCache: Cache? = CoilUtils.createDiskCache(context)
): OkHttpClient {
    cache(diskCache)
    interceptors().removeIfIndices { it is DiskCacheInterceptor }
    if (diskCache != null) interceptors() += DiskCacheInterceptor(diskCache)
    return build()
}

/**
 * Tags [Response]s with their associated disk cache file.
 * This relies on implementation details of the [Cache] class to determine the file name.
 * Must be the last non-network interceptor in the chain.
 */
private class DiskCacheInterceptor(private val diskCache: Cache) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        val request = response.request
        val cacheFile = diskCache.directory.resolve("${Cache.key(request.url)}.$ENTRY_BODY")
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

    companion object {
        // Tracks 'okhttp3.Cache.ENTRY_BODY' which is private.
        private const val ENTRY_BODY = 1
    }
}

/** Use a private class so its tag is guaranteed not to be overwritten. */
private class CacheFile(val file: File)

internal val Response.cacheFile: File?
    get() = request.tag(CacheFile::class.java)?.file
