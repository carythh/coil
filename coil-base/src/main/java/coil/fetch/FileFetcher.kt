package coil.fetch

import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.Options
import okio.buffer
import okio.source
import java.io.File

internal class FileFetcher(
    private val data: File,
    private val addLastModifiedToFileCacheKey: Boolean
) : Fetcher {

    override val cacheKey: String
        get() = if (addLastModifiedToFileCacheKey) "${data.path}:${data.lastModified()}" else data.path

    override suspend fun fetch(): FetchResult {
        return SourceResult(
            source = data.source().buffer(),
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(data.extension),
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val addLastModifiedToFileCacheKey: Boolean) : Fetcher.Factory<File> {

        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher {
            return FileFetcher(data, addLastModifiedToFileCacheKey)
        }
    }
}
