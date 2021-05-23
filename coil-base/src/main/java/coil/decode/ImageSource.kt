@file:JvmName("ImageSources")

package coil.decode

import android.content.Context
import coil.util.closeQuietly
import coil.util.safeCacheDir
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.Closeable
import java.io.File

/**
 * Create a new [ImageSource] backed by a [File].
 *
 * @param file The file to read from.
 * @param closeable An optional closeable reference that will
 *  be closed when the image source is closed.
 */
@JvmOverloads
@JvmName("create")
fun ImageSource(
    file: File,
    closeable: Closeable? = null
): ImageSource = FileImageSource(file, closeable)

/**
 * Create a new [ImageSource] backed by a [BufferedSource].
 *
 * @param source The buffered source to read from.
 * @param context A context used to resolve a safe cache directory.
 */
@JvmName("create")
fun ImageSource(
    source: BufferedSource,
    context: Context
): ImageSource = SourceImageSource(source, context.safeCacheDir)

/**
 * Create a new [ImageSource] backed by a [BufferedSource].
 *
 * @param source The buffered source to read from.
 * @param cacheDirectory The directory to create temporary files in
 *  if [ImageSource.file] is called.
 */
@JvmName("create")
fun ImageSource(
    source: BufferedSource,
    cacheDirectory: File
): ImageSource = SourceImageSource(source, cacheDirectory)

sealed class ImageSource : Closeable {

    /**
     * Return the underlying [BufferedSource] for this [ImageSource] if it exists.
     * If this is null, [file] is guaranteed to be not null.
     */
    abstract val source: BufferedSource?

    /**
     * Return the underlying [File] for this [ImageSource] if it exists.
     * If this is null, [source] is guaranteed to be not null.
     */
    abstract val file: File?

    /**
     * Return a [BufferedSource] to read this [ImageSource].
     * The source returned by this method is reused for subsequent calls.
     */
    abstract fun source(): BufferedSource

    /**
     * Return a [File] containing this [ImageSource]'s data.
     * If this image source is backed by a [BufferedSource], a temporary file
     * on disk will be created.
     */
    abstract fun file(): File
}

private class FileImageSource(
    override val file: File,
    private val closeable: Closeable?
) : ImageSource() {

    private var isClosed = false
    private var tempSource: BufferedSource? = null

    override val source: BufferedSource? get() = null

    @Synchronized
    override fun source(): BufferedSource {
        assertNotClosed()
        tempSource?.let { return it }
        return file.source().buffer().also { tempSource = it }
    }

    @Synchronized
    override fun file(): File {
        assertNotClosed()
        return file
    }

    @Synchronized
    override fun close() {
        isClosed = true
        tempSource?.closeQuietly()
        closeable?.closeQuietly()
    }

    private fun assertNotClosed() {
        check(!isClosed) { "closed" }
    }
}

private class SourceImageSource(
    override val source: BufferedSource,
    private val cacheDirectory: File
) : ImageSource() {

    private var isClosed = false
    private var tempFile: File? = null

    override val file: File? get() = null

    @Synchronized
    override fun source(): BufferedSource {
        assertNotClosed()
        return source
    }

    @Synchronized
    override fun file(): File {
        assertNotClosed()
        tempFile?.let { return it }
        val file = File.createTempFile("tmp", null, cacheDirectory)
        source.peek().use { file.sink().use(it::readAll) }
        tempFile = file
        return file
    }

    @Synchronized
    override fun close() {
        isClosed = true
        tempFile?.delete()
        source.closeQuietly()
    }

    private fun assertNotClosed() {
        check(!isClosed) { "closed" }
    }
}
