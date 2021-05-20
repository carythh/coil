@file:Suppress("unused")

package coil.decode

import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.fetch.SourceResult
import coil.request.Options

/**
 * A [Decoder] that uses [MediaMetadataRetriever] to fetch and decode a frame from a video.
 */
class VideoFrameDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    private val delegate = VideoFrameDecoderDelegate(options.context)

    override suspend fun decode(): DecodeResult {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source.file().path)
            delegate.decode(retriever, options)
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
