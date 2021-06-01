package coil.memory

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.arraySetOf
import androidx.test.core.app.ApplicationProvider
import coil.memory.MemoryCache.Key
import coil.util.allocationByteCountCompat
import coil.util.createBitmap
import coil.util.forEachIndices
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 28])
class WeakMemoryCacheTest {

    private lateinit var context: Context
    private lateinit var weakMemoryCache: RealWeakMemoryCache
    private lateinit var references: MutableSet<Any>

    @Before
    fun before() {
        context = ApplicationProvider.getApplicationContext()
        weakMemoryCache = RealWeakMemoryCache()
        references = arraySetOf()
    }

    @Test
    fun `can retrieve cached value`() {
        val key = Key("key")
        val bitmap = reference(createBitmap())
        val isSampled = false
        val size = bitmap.allocationByteCountCompat

        weakMemoryCache.set(key, bitmap, isSampled, size)
        val value = weakMemoryCache.get(key)

        assertNotNull(value)
        assertEquals(bitmap, value.bitmap)
        assertEquals(isSampled, value.isSampled)
    }

    @Test
    fun `can hold multiple values`() {
        val bitmap1 = reference(createBitmap())
        weakMemoryCache.set(Key("key1"), bitmap1, false, 100)

        val bitmap2 = reference(createBitmap())
        weakMemoryCache.set(Key("key2"), bitmap2, false, 100)

        val bitmap3 = reference(createBitmap())
        weakMemoryCache.set(Key("key3"), bitmap3, false, 100)

        assertEquals(bitmap1, weakMemoryCache.get(Key("key1"))?.bitmap)
        assertEquals(bitmap2, weakMemoryCache.get(Key("key2"))?.bitmap)
        assertEquals(bitmap3, weakMemoryCache.get(Key("key3"))?.bitmap)

        weakMemoryCache.clear(bitmap2)

        assertEquals(bitmap1, weakMemoryCache.get(Key("key1"))?.bitmap)
        assertNull(weakMemoryCache.get(Key("key2")))
        assertEquals(bitmap3, weakMemoryCache.get(Key("key3"))?.bitmap)
    }

    @Test
    fun `empty references are removed from cache`() {
        val key = Key("key")
        val bitmap = reference(createBitmap())

        weakMemoryCache.set(key, bitmap, false, 100)
        weakMemoryCache.clear(bitmap)

        assertNull(weakMemoryCache.get(key))
    }

    @Test
    fun `bitmaps with same key are retrieved by size descending`() {
        val bitmap1 = reference(createBitmap())
        val bitmap2 = reference(createBitmap())
        val bitmap3 = reference(createBitmap())
        val bitmap4 = reference(createBitmap())
        val bitmap5 = reference(createBitmap())
        val bitmap6 = reference(createBitmap())
        val bitmap7 = reference(createBitmap())
        val bitmap8 = reference(createBitmap())

        weakMemoryCache.set(Key("key"), bitmap1, false, 1)
        weakMemoryCache.set(Key("key"), bitmap3, false, 3)
        weakMemoryCache.set(Key("key"), bitmap5, false, 5)
        weakMemoryCache.set(Key("key"), bitmap7, false, 7)
        weakMemoryCache.set(Key("key"), bitmap8, false, 8)
        weakMemoryCache.set(Key("key"), bitmap4, false, 4)
        weakMemoryCache.set(Key("key"), bitmap6, false, 6)
        weakMemoryCache.set(Key("key"), bitmap2, false, 2)

        assertEquals(bitmap8, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap8)

        assertEquals(bitmap7, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap7)

        assertEquals(bitmap6, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap6)

        assertEquals(bitmap5, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap5)

        assertEquals(bitmap4, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap4)

        assertEquals(bitmap3, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap3)

        assertEquals(bitmap2, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap2)

        assertEquals(bitmap1, weakMemoryCache.get(Key("key"))?.bitmap)
        weakMemoryCache.clear(bitmap1)

        // All the values are invalidated.
        assertNull(weakMemoryCache.get(Key("key")))
    }

    @Test
    fun `cleanUp clears all collected values`() {
        val bitmap1 = reference(createBitmap())
        weakMemoryCache.set(Key("key1"), bitmap1, false, 100)

        val bitmap2 = reference(createBitmap())
        weakMemoryCache.set(Key("key2"), bitmap2, false, 100)

        val bitmap3 = reference(createBitmap())
        weakMemoryCache.set(Key("key3"), bitmap3, false, 100)

        weakMemoryCache.clear(bitmap1)
        assertNull(weakMemoryCache.get(Key("key1")))

        weakMemoryCache.clear(bitmap3)
        assertNull(weakMemoryCache.get(Key("key3")))

        assertEquals(3, weakMemoryCache.keys.size)

        weakMemoryCache.cleanUp()

        assertEquals(1, weakMemoryCache.keys.size)

        assertNull(weakMemoryCache.get(Key("key1")))
        assertEquals(bitmap2, weakMemoryCache.get(Key("key2"))?.bitmap)
        assertNull(weakMemoryCache.get(Key("key3")))
    }

    @Test
    fun `value is removed after invalidate is called`() {
        val key = Key("1")
        val bitmap = createBitmap()
        weakMemoryCache.set(key, bitmap, false, bitmap.allocationByteCountCompat)
        weakMemoryCache.remove(key)

        assertNull(weakMemoryCache.get(key))
    }

    /**
     * Clears [bitmap]'s weak reference without removing its entry from
     * [RealWeakMemoryCache.cache]. This simulates garbage collection.
     */
    private fun RealWeakMemoryCache.clear(bitmap: Bitmap) {
        cache.values.forEach { values ->
            values.forEachIndices { value ->
                if (value.bitmap.get() === bitmap) {
                    value.bitmap.clear()
                    return
                }
            }
        }
    }

    /**
     * Hold a strong reference to the value for the duration of the test
     * to prevent it from being garbage collected.
     */
    private fun <T : Any> reference(value: T): T {
        references += value
        return value
    }
}
