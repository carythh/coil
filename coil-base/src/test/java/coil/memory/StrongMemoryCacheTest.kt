package coil.memory

import coil.memory.MemoryCache.Key
import coil.util.DEFAULT_BITMAP_SIZE
import coil.util.createBitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class StrongMemoryCacheTest {

    @Test
    fun `can retrieve cached value`() {
        val weakCache = EmptyWeakMemoryCache()
        val strongCache = RealStrongMemoryCache(2 * DEFAULT_BITMAP_SIZE, weakCache)

        val bitmap = createBitmap()
        strongCache.set(Key("1"), bitmap, false)

        assertEquals(bitmap, strongCache.get(Key("1"))?.bitmap)
    }

    @Test
    fun `least recently used value is evicted`() {
        val weakCache = RealWeakMemoryCache()
        val strongCache = RealStrongMemoryCache(2 * DEFAULT_BITMAP_SIZE, weakCache)

        val first = createBitmap()
        strongCache.set(Key("1"), first, false)

        val second = createBitmap()
        strongCache.set(Key("2"), second, false)

        val third = createBitmap()
        strongCache.set(Key("3"), third, false)

        assertNull(strongCache.get(Key("1")))
        assertNotNull(weakCache.get(Key("1")))
    }

    @Test
    fun `maxSize 0 disables memory cache`() {
        val weakCache = EmptyWeakMemoryCache()
        val strongCache = RealStrongMemoryCache(0, weakCache)

        val bitmap = createBitmap()
        strongCache.set(Key("1"), bitmap, false)

        assertNull(strongCache.get(Key("1")))
    }

    @Test
    fun `value can be removed`() {
        val weakCache = RealWeakMemoryCache()
        val strongCache = RealStrongMemoryCache(2 * DEFAULT_BITMAP_SIZE, weakCache)

        val bitmap = createBitmap()
        strongCache.set(Key("1"), bitmap, false)
        strongCache.remove(Key("1"))

        assertNull(strongCache.get(Key("1")))
        assertNotNull(weakCache.get(Key("1")))
    }
}
