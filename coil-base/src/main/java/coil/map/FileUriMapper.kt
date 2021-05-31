package coil.map

import android.content.ContentResolver
import android.net.Uri
import coil.fetch.AssetUriFetcher
import coil.request.Options
import coil.util.firstPathSegment
import java.io.File

internal class FileUriMapper : Mapper<Uri, File> {

    override fun map(data: Uri, options: Options): File? {
        if (!isApplicable(data)) return null
        return File(checkNotNull(data.path))
    }

    private fun isApplicable(data: Uri): Boolean {
        return (data.scheme == null || data.scheme == ContentResolver.SCHEME_FILE) &&
            data.firstPathSegment.let { it != null && it != AssetUriFetcher.ASSET_FILE_PATH_ROOT }
    }
}
