package coil.fetch

import coil.ImageLoader
import coil.decode.DataSource
import coil.request.Options
import okio.Buffer
import java.nio.ByteBuffer

internal class ByteBufferFetcher(private val data: ByteBuffer) : Fetcher {

    override val cacheKey get() = data.hashCode().toString()

    override suspend fun fetch(): FetchResult {
        return SourceResult(
            source = Buffer().apply { write(data) },
            mimeType = null,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory: Fetcher.Factory<ByteBuffer> {

        override fun create(data: ByteBuffer, options: Options, imageLoader: ImageLoader): Fetcher {
            return ByteBufferFetcher(data)
        }
    }
}
