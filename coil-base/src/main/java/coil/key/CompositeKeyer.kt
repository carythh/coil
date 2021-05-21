package coil.key

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import coil.request.Options
import coil.util.nightMode
import okhttp3.HttpUrl
import java.io.File
import java.nio.ByteBuffer

internal class CompositeKeyer(private val addLastModifiedToFileCacheKey: Boolean) : Keyer<Any> {

    override fun key(data: Any, options: Options) = when (data) {
        is Uri -> newKey(data, options)
        is HttpUrl -> data.toString()
        is File -> newKey(data)
        is Bitmap,
        is Drawable,
        is ByteBuffer -> data.hashCode().toString()
        else -> null
    }

    private fun newKey(data: Uri, options: Options): String {
        // 'android.resource' uris can change if night mode is enabled/disabled.
        return if (data.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
            "$data-${options.context.resources.configuration.nightMode}"
        } else {
            data.toString()
        }
    }

    private fun newKey(data: File): String {
        return if (addLastModifiedToFileCacheKey) {
            "${data.path}:${data.lastModified()}"
        } else {
            data.path
        }
    }
}
