@file:Suppress("UNCHECKED_CAST")

package coil.fetch

import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.ImageSource
import coil.request.Options

/**
 * A [Fetcher] translates data into either an [ImageSource] or a [Drawable].
 *
 * To accomplish this, fetchers fit into one of two types:
 *
 * - Uses the data as a key to fetch bytes from a remote source (e.g. network or disk)
 *   and exposes it as an [ImageSource]. e.g. [HttpUrlFetcher]
 * - Reads the data directly and translates it into a [Drawable]. e.g. [BitmapFetcher]
 */
fun interface Fetcher {

    suspend fun fetch(): FetchResult?

    fun interface Factory<T : Any> {

        fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher?
    }
}
