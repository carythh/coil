@file:Suppress("unused")

package coil.decode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import coil.fetch.Fetcher
import coil.request.CachePolicy
import coil.request.Parameters
import coil.size.OriginalSize
import coil.size.Scale
import coil.size.Size
import coil.util.EMPTY_HEADERS
import coil.util.NULL_COLOR_SPACE
import okhttp3.Headers

/**
 * A set of configuration options for fetching and decoding an image. [Fetcher]s and [Decoder]s
 * should respect these options as best as possible.
 *
 * @param context The [Context] used to execute this request.
 * @param config The requested config for any [Bitmap]s.
 * @param colorSpace The preferred color space for any [Bitmap]s.
 *  If 'null', components should typically default to [ColorSpace.Rgb].
 * @param size The requested output size for the image request.
 * @param scale The scaling algorithm for how to fit the source image's dimensions into the target's dimensions.
 * @param allowInexactSize 'true' if the output image does not need to fit/fill the target's dimensions exactly. For
 *  instance, if 'true' [BitmapFactoryDecoder] will not decode an image at a larger size than its source dimensions as
 *  an optimization.
 * @param allowRgb565 'true' if a component is allowed to use [Bitmap.Config.RGB_565] as an optimization. As RGB_565
 *  does not have an alpha channel, components should only use RGB_565 if the image is guaranteed to not use alpha.
 * @param premultipliedAlpha 'true' if the color (RGB) channels of the decoded image should be pre-multiplied by the
 *  alpha channel. The default behavior is to enable pre-multiplication but in some environments it can be necessary
 *  to disable this feature to leave the source pixels unmodified.
 * @param headers The header fields to use for any network requests.
 * @param parameters A map of custom parameters. These are used to pass custom data to a component.
 * @param memoryCachePolicy Determines if this request is allowed to read/write from/to memory.
 * @param diskCachePolicy Determines if this request is allowed to read/write from/to disk.
 * @param networkCachePolicy Determines if this request is allowed to read from the network.
 */
data class Options(
    val context: Context,
    val config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    val colorSpace: ColorSpace? = NULL_COLOR_SPACE,
    val size: Size = OriginalSize,
    val scale: Scale = Scale.FIT,
    val allowInexactSize: Boolean = false,
    val allowRgb565: Boolean = false,
    val premultipliedAlpha: Boolean = true,
    val headers: Headers = EMPTY_HEADERS,
    val parameters: Parameters = Parameters.EMPTY,
    val memoryCachePolicy: CachePolicy = CachePolicy.ENABLED,
    val diskCachePolicy: CachePolicy = CachePolicy.ENABLED,
    val networkCachePolicy: CachePolicy = CachePolicy.ENABLED
)
