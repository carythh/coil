package coil.decode

import coil.util.closeQuietly
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.Closeable
import java.io.File

sealed class ImageSource : Closeable {

    abstract val source: BufferedSource?
    abstract val file: File?

    abstract fun source(): BufferedSource
    abstract fun file(): File

    companion object {

        @JvmStatic
        @JvmOverloads
        @JvmName("create")
        operator fun invoke(
            file: File,
            source: BufferedSource? = null
        ): ImageSource = FileImageSource(file, source)

        @JvmStatic
        @JvmName("create")
        operator fun invoke(
            source: BufferedSource,
            cacheDirectory: File
        ): ImageSource = SourceImageSource(source, cacheDirectory)
    }
}

private class FileImageSource(
    override val file: File,
    source: BufferedSource?
) : ImageSource() {

    private val _source = source
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
        _source?.closeQuietly()
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
