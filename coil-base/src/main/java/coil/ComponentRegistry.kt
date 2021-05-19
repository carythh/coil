@file:Suppress("UNCHECKED_CAST", "unused")

package coil

import coil.decode.Decoder
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.intercept.Interceptor
import coil.map.Mapper
import coil.request.Options
import coil.util.asImmutable
import coil.util.firstNotNullOfOrNullIndices
import coil.util.forEachIndices

/**
 * Registry for all the components that an [ImageLoader] uses to fulfil image requests.
 *
 * Use this class to register support for custom [Interceptor]s, [Mapper]s, [Fetcher]s, and [Decoder]s.
 */
class ComponentRegistry private constructor(
    val interceptors: List<Interceptor>,
    val mappers: List<Pair<Mapper<out Any, *>, Class<out Any>>>,
    val fetchers: List<Pair<Fetcher.Factory<out Any>, Class<out Any>>>,
    val decoders: List<Decoder.Factory>
) {

    constructor() : this(emptyList(), emptyList(), emptyList(), emptyList())

    fun map(data: Any, options: Options): Any {
        var mappedData = data
        mappers.forEachIndices { (mapper, type) ->
            if (type.isAssignableFrom(mappedData::class.java)) {
                (mapper as Mapper<Any, *>).map(mappedData, options)?.let { mappedData = it }
            }
        }
        return mappedData
    }

    fun newFetcher(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
        fetchers.forEachIndices { (fetcher, type) ->
            if (type.isAssignableFrom(data::class.java)) {
                (fetcher as Fetcher.Factory<Any>).create(data, options, imageLoader)?.let { return it }
            }
        }
        return null
    }

    fun newDecoder(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
        return decoders.firstNotNullOfOrNullIndices { decoder ->
            decoder.create(result, options, imageLoader)
        }
    }

    fun newBuilder() = Builder(this)

    class Builder {

        private val interceptors: MutableList<Interceptor>
        private val mappers: MutableList<Pair<Mapper<out Any, *>, Class<out Any>>>
        private val fetchers: MutableList<Pair<Fetcher.Factory<out Any>, Class<out Any>>>
        private val decoders: MutableList<Decoder.Factory>

        constructor() {
            interceptors = mutableListOf()
            mappers = mutableListOf()
            fetchers = mutableListOf()
            decoders = mutableListOf()
        }

        constructor(registry: ComponentRegistry) {
            interceptors = registry.interceptors.toMutableList()
            mappers = registry.mappers.toMutableList()
            fetchers = registry.fetchers.toMutableList()
            decoders = registry.decoders.toMutableList()
        }

        /** Register an [Interceptor]. */
        fun add(interceptor: Interceptor) = apply {
            interceptors += interceptor
        }

        /** Register a [Mapper]. */
        inline fun <reified T : Any> add(mapper: Mapper<T, *>) = add(mapper, T::class.java)

        @PublishedApi
        internal fun <T : Any> add(mapper: Mapper<T, *>, type: Class<T>) = apply {
            mappers += mapper to type
        }

        /** Register a [Fetcher]. */
        inline fun <reified T : Any> add(fetcher: Fetcher.Factory<T>) = add(fetcher, T::class.java)

        @PublishedApi
        internal fun <T : Any> add(fetcher: Fetcher.Factory<T>, type: Class<T>) = apply {
            fetchers += fetcher to type
        }

        /** Register a [Decoder]. */
        fun add(decoder: Decoder.Factory) = apply {
            decoders += decoder
        }

        fun build(): ComponentRegistry {
            return ComponentRegistry(
                interceptors.toList().asImmutable(),
                mappers.toList().asImmutable(),
                fetchers.toList().asImmutable(),
                decoders.toList().asImmutable()
            )
        }
    }
}
