package coil.memory

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.ViewTarget
import kotlinx.coroutines.Job

internal sealed class RequestDelegate : DefaultLifecycleObserver {

    /** Called when the image request completes for any reason. */
    @MainThread
    open fun complete() {}

    /** Cancel any in progress work and free all resources. */
    @MainThread
    open fun dispose() {}

    /** Automatically dispose this delegate when the containing lifecycle is destroyed. */
    @MainThread
    override fun onDestroy(owner: LifecycleOwner) = dispose()
}

/** A request delegate for a one-shot requests with no target or a non-[ViewTarget]. */
internal class BaseRequestDelegate(
    private val lifecycle: Lifecycle,
    private val job: Job
) : RequestDelegate() {

    override fun complete() {
        lifecycle.removeObserver(this)
    }

    override fun dispose() {
        job.cancel()
    }
}

/** A request delegate for restartable requests with a [ViewTarget]. */
internal class ViewTargetRequestDelegate(
    private val imageLoader: ImageLoader,
    private val request: ImageRequest,
    private val target: ViewTarget<*>,
    private val lifecycle: Lifecycle,
    private val job: Job
) : RequestDelegate() {

    /** Repeat this request with the same [ImageRequest]. */
    @MainThread
    fun restart() {
        imageLoader.enqueue(request)
    }

    override fun dispose() {
        job.cancel()
        (target as? LifecycleObserver)?.let(lifecycle::removeObserver)
        lifecycle.removeObserver(this)
    }
}
