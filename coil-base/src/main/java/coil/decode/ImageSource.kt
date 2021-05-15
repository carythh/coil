package coil.decode

import okio.BufferedSource
import java.io.Closeable
import java.io.File

sealed class ImageSource : Closeable {

    abstract val source: BufferedSource

    abstract val file: File

    abstract fun sourceOrNull(): BufferedSource?

    abstract fun fileOrNull(): File?
}
