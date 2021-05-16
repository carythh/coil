package coil.decode

import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.fetch.SourceResult
import okio.BufferedSource

/**
 * Converts an [ImageSource] into a [Drawable].
 *
 * Use this interface to add support for custom file formats (e.g. GIF, SVG, TIFF, etc.).
 */
fun interface Decoder {

    /**
     * Decode [source] as a [Drawable].
     *
     * @param source The [ImageSource] to read from.
     * @param options A set of configuration options for this request.
     */
    suspend fun decode(): DecodeResult

    fun interface Factory {

        /**
         * Return a [Decoder] that can decode [source] or 'null' if this factory
         * cannot create a decoder for the source.
         *
         * Implementations **must not** consume the source, as this can cause calls to subsequent decoders to fail.
         *
         * Prefer using [BufferedSource.peek], [BufferedSource.rangeEquals], or other non-destructive methods to check
         * for the presence of header bytes or other markers. Implementations can also rely on [mimeType],
         * however it is not guaranteed to be accurate (e.g. a file that ends with .png, but is encoded as a .jpg).
         *
         * @param source The [ImageSource] to read from.
         * @param options A set of configuration options for this request.
         * @param mimeType An optional MIME type for the [source].
         * @param imageLoader The [ImageLoader] that's creating this [Decoder].
         */
        fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder?
    }
}
