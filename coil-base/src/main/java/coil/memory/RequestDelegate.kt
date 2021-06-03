package coil.memory

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.ViewTarget
import coil.util.requestManager
import kotlinx.coroutines.Job

internal sealed class RequestDelegate : DefaultLifecycleObserver {

    /** Register all lifecycle observers. */
    @MainThread
    open fun start() {}

    /** Called when the image request completes for any reason. */
    @MainThread
    open fun complete() {}

    /** Cancel any in progress work and clear all lifecycle observers. */
    @MainThread
    open fun dispose() {}
}

/** A request delegate for a one-shot requests with no target or a non-[ViewTarget]. */
internal class BaseRequestDelegate(
    private val lifecycle: Lifecycle,
    private val job: Job
) : RequestDelegate() {

    override fun start() {
        lifecycle.addObserver(this)
    }

    override fun complete() {
        lifecycle.removeObserver(this)
    }

    override fun dispose() {
        job.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) = dispose()
}

/** A request delegate for restartable requests with a [ViewTarget]. */
internal class ViewTargetRequestDelegate(
    private val imageLoader: ImageLoader,
    private val initialRequest: ImageRequest,
    private val target: ViewTarget<*>,
    private val lifecycle: Lifecycle,
    private val job: Job
) : RequestDelegate() {

    /** Repeat this request with the same [ImageRequest]. */
    @MainThread
    fun restart() {
        imageLoader.enqueue(initialRequest)
    }

    override fun start() {
        lifecycle.addObserver(this)

        // Re-add the observer to ensure all its lifecycle callbacks are invoked.
        if (target is LifecycleObserver) {
            lifecycle.removeObserver(target)
            lifecycle.addObserver(target)
        }

        // Attach the request to the view's request manager.
        target.view.requestManager.setRequest(this)
    }

    override fun dispose() {
        job.cancel()
        if (target is LifecycleObserver) lifecycle.removeObserver(target)
        lifecycle.removeObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        target.view.requestManager.dispose()
    }
}
