package coil

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import coil.decode.BitmapFactoryDecoder
import coil.fetch.AssetUriFetcher
import coil.fetch.BitmapFetcher
import coil.fetch.ByteBufferFetcher
import coil.fetch.ContentUriFetcher
import coil.fetch.DrawableFetcher
import coil.fetch.FileFetcher
import coil.fetch.HttpUriFetcher
import coil.fetch.HttpUrlFetcher
import coil.fetch.ResourceUriFetcher
import coil.intercept.EngineInterceptor
import coil.intercept.RealInterceptorChain
import coil.map.FileUriMapper
import coil.map.ResourceIntMapper
import coil.map.ResourceUriMapper
import coil.map.StringMapper
import coil.memory.DelegateService
import coil.memory.MemoryCache
import coil.memory.RequestService
import coil.memory.TargetDelegate
import coil.request.DefaultRequestOptions
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.NullRequestData
import coil.request.NullRequestDataException
import coil.request.OneShotDisposable
import coil.request.SuccessResult
import coil.request.ViewTargetDisposable
import coil.target.ViewTarget
import coil.util.Emoji
import coil.util.ImageLoaderOptions
import coil.util.Logger
import coil.util.SystemCallbacks
import coil.util.Utils.REQUEST_TYPE_ENQUEUE
import coil.util.Utils.REQUEST_TYPE_EXECUTE
import coil.util.awaitStarted
import coil.util.emoji
import coil.util.get
import coil.util.job
import coil.util.log
import coil.util.requestManager
import coil.util.result
import coil.util.toDrawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

internal class RealImageLoader(
    val context: Context,
    override val defaults: DefaultRequestOptions,
    override val memoryCache: MemoryCache,
    val callFactory: Call.Factory,
    val eventListenerFactory: EventListener.Factory,
    val componentRegistry: ComponentRegistry,
    val options: ImageLoaderOptions,
    val logger: Logger?
) : ImageLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, throwable -> logger?.log(TAG, throwable) })
    private val systemCallbacks = SystemCallbacks(this, context)
    private val delegateService = DelegateService(this, logger)
    private val requestService = RequestService(systemCallbacks, logger)
    override val components = componentRegistry.newBuilder()
        // Mappers
        .add(StringMapper())
        .add(FileUriMapper())
        .add(ResourceUriMapper(context))
        .add(ResourceIntMapper(context))
        // Fetchers
        .add(HttpUriFetcher(callFactory))
        .add(HttpUrlFetcher(callFactory))
        .add(FileFetcher(options.addLastModifiedToFileCacheKey))
        .add(AssetUriFetcher(context))
        .add(ContentUriFetcher(context))
        .add(ResourceUriFetcher(context))
        .add(DrawableFetcher())
        .add(BitmapFetcher())
        .add(ByteBufferFetcher())
        // Decoders
        .add(BitmapFactoryDecoder(context))
        .build()
    private val interceptors = components.interceptors + EngineInterceptor(components, memoryCache, requestService, logger)
    private val isShutdown = AtomicBoolean(false)

    override fun enqueue(request: ImageRequest): Disposable {
        // Start executing the request on the main thread.
        val deferred = scope.async {
            executeMain(request, REQUEST_TYPE_ENQUEUE).also { result ->
                if (result is ErrorResult) logger?.log(TAG, result.throwable)
            }
        }

        // Update the current request attached to the view and return a new disposable.
        return if (request.target is ViewTarget<*>) {
            val requestId = request.target.view.requestManager.setCurrentRequestJob(deferred)
            ViewTargetDisposable(requestId, request.target)
        } else {
            OneShotDisposable(deferred)
        }
    }

    override suspend fun execute(request: ImageRequest): ImageResult {
        // Start executing the request on the main thread.
        val deferred = scope.async {
            executeMain(request, REQUEST_TYPE_EXECUTE).also { result ->
                if (result is ErrorResult) throw result.throwable
            }
        }

        // Update the current request attached to the view and await the result.
        if (request.target is ViewTarget<*>) {
            request.target.view.requestManager.setCurrentRequestJob(deferred)
        }
        return deferred.await()
    }

    @MainThread
    private suspend fun executeMain(initialRequest: ImageRequest, type: Int): ImageResult {
        // Ensure this image loader isn't shutdown.
        check(!isShutdown.get()) { "The image loader is shutdown." }

        // Apply this image loader's defaults to this request.
        val request = initialRequest.newBuilder().defaults(defaults).build()

        // Create a new event listener.
        val eventListener = eventListenerFactory.create(request)

        // Wrap the target to support bitmap pooling.
        val targetDelegate = delegateService.createTargetDelegate(request.target, eventListener)

        // Wrap the request to manage its lifecycle.
        val requestDelegate = delegateService.createRequestDelegate(request, targetDelegate, coroutineContext.job)

        try {
            // Fail before starting if data is null.
            if (request.data == NullRequestData) throw NullRequestDataException()

            // Enqueued requests suspend until the lifecycle is started.
            if (type == REQUEST_TYPE_ENQUEUE) request.lifecycle.awaitStarted()

            // Set the placeholder on the target.
            val cached = memoryCache[request.placeholderMemoryCacheKey]?.bitmap
            targetDelegate.result = null
            targetDelegate.start(cached?.toDrawable(request.context) ?: request.placeholder, cached)
            eventListener.onStart(request)
            request.listener?.onStart(request)

            // Resolve the size.
            eventListener.resolveSizeStart(request)
            val size = request.sizeResolver.size()
            eventListener.resolveSizeEnd(request, size)

            // Execute the interceptor chain.
            val result = withContext(request.interceptorDispatcher) {
                RealInterceptorChain(request, interceptors, 0, request, size, cached, eventListener).proceed(request)
            }

            // Set the result on the target.
            targetDelegate.result = result
            when (result) {
                is SuccessResult -> onSuccess(result, targetDelegate, eventListener)
                is ErrorResult -> onError(result, targetDelegate, eventListener)
            }
            return result
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                onCancel(request, eventListener)
                throw throwable
            } else {
                // Create the default error result if there's an uncaught exception.
                val result = requestService.errorResult(request, throwable)
                onError(result, targetDelegate, eventListener)
                return result
            }
        } finally {
            requestDelegate.complete()
        }
    }

    /** Called by [SystemCallbacks.onTrimMemory]. */
    internal fun onTrimMemory(level: Int) {
        memoryCache.trimMemory(level)
    }

    override fun shutdown() {
        if (isShutdown.getAndSet(true)) return

        // Order is important.
        scope.cancel()
        systemCallbacks.shutdown()
        memoryCache.clear()
    }

    override fun newBuilder() = ImageLoader.Builder(this)

    private suspend inline fun onSuccess(
        result: SuccessResult,
        targetDelegate: TargetDelegate,
        eventListener: EventListener
    ) {
        val request = result.request
        val dataSource = result.dataSource
        logger?.log(TAG, Log.INFO) { "${dataSource.emoji} Successful (${dataSource.name}) - ${request.data}" }
        targetDelegate.success(result)
        eventListener.onSuccess(request, result)
        request.listener?.onSuccess(request, result)
    }

    private suspend inline fun onError(
        result: ErrorResult,
        targetDelegate: TargetDelegate,
        eventListener: EventListener
    ) {
        val request = result.request
        logger?.log(TAG, Log.INFO) { "${Emoji.SIREN} Failed - ${request.data} - ${result.throwable}" }
        targetDelegate.error(result)
        eventListener.onError(request, result)
        request.listener?.onError(request, result)
    }

    private fun onCancel(request: ImageRequest, eventListener: EventListener) {
        logger?.log(TAG, Log.INFO) { "${Emoji.CONSTRUCTION} Cancelled - ${request.data}" }
        eventListener.onCancel(request)
        request.listener?.onCancel(request)
    }

    companion object {
        private const val TAG = "RealImageLoader"
    }
}
