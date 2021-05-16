@file:Suppress("unused")

package coil.decode

import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.fetch.SourceResult
import okio.sink
import java.io.File

/**
 * A [Decoder] that uses [MediaMetadataRetriever] to fetch and decode a frame from a video.
 *
 * NOTE: [VideoFrameDecoder] creates a temporary copy of the video on the file system. This may cause the decode
 * process to fail if the video being decoded is very large and/or the device is very low on disk space.
 */
class VideoFrameDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    private val delegate = VideoFrameDecoderDelegate(options.context)

    override suspend fun decode(): DecodeResult {
        val tempFile = File.createTempFile("tmp", null, options.context.cacheDir.apply { mkdirs() })
        try {
            // Read the source into a temporary file.
            source.source.use { tempFile.sink().use(it::readAll) }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempFile.path)
                return delegate.decode(retriever, options)
            } finally {
                retriever.release()
            }
        } finally {
            tempFile.delete()
        }
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (!isApplicable(result.mimeType)) return null
            return VideoFrameDecoder(result.source, options)
        }

        private fun isApplicable(mimeType: String?): Boolean {
            return mimeType != null && mimeType.startsWith("video/")
        }
    }

    companion object {
        const val VIDEO_FRAME_MICROS_KEY = "coil#video_frame_micros"
        const val VIDEO_FRAME_OPTION_KEY = "coil#video_frame_option"
    }
}
