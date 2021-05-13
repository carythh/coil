package coil.memory

import coil.memory.MemoryCache.Key

internal class RealMemoryCache(
    val strongMemoryCache: StrongMemoryCache,
    val weakMemoryCache: WeakMemoryCache
) : MemoryCache {

    override val size get() = strongMemoryCache.size

    override val maxSize get() = strongMemoryCache.maxSize

    override fun get(key: Key): MemoryCache.Value? {
        return strongMemoryCache.get(key) ?: weakMemoryCache.get(key)
    }

    override fun set(key: Key, value: MemoryCache.Value) {
        strongMemoryCache.set(key, value.bitmap, value.isSampled)
        // weakMemoryCache.set() is called by strongMemoryCache when
        // a value is evicted from the strong reference cache.
    }

    override fun remove(key: Key): Boolean {
        // Do not short circuit. There is a regression test for this.
        val removedStrong = strongMemoryCache.remove(key)
        val removedWeak = weakMemoryCache.remove(key)
        return removedStrong || removedWeak
    }

    override fun clear() {
        strongMemoryCache.clearMemory()
        weakMemoryCache.clearMemory()
    }

    override fun trimMemory(level: Int) {
        strongMemoryCache.trimMemory(level)
        weakMemoryCache.trimMemory(level)
    }
}
