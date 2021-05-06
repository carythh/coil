package coil.fetch

import coil.decode.DataSource
import coil.decode.Options
import okio.Buffer
import java.nio.ByteBuffer

internal class ByteBufferFetcher : Fetcher<ByteBuffer> {

    override fun cacheKey(data: ByteBuffer) = data.hashCode().toString()

    override suspend fun fetch(data: ByteBuffer, options: Options): FetchResult {
        return SourceResult(
            source = Buffer().apply { write(data) },
            mimeType = null,
            dataSource = DataSource.MEMORY
        )
    }
}
