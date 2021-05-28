package coil.request

import android.view.View
import coil.target.ViewTarget
import coil.util.requestManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.UUID

/**
 * Represents the work of an executed [ImageRequest].
 */
interface Disposable {

    /**
     * The most recent job for this work.
     */
    val currentJob: Deferred<ImageResult>

    /**
     * Returns 'true' if the request is complete or cancelling.
     */
    val isDisposed: Boolean

    /**
     * Cancels any in progress work and frees any resources associated with this request. This method is idempotent.
     */
    fun dispose()
}

/**
 * A disposable for one-shot image requests.
 */
internal class OneShotDisposable(
    override val currentJob: Deferred<ImageResult>
) : Disposable {

    override val isDisposed
        get() = !currentJob.isActive

    override fun dispose() {
        if (isDisposed) return
        currentJob.cancel()
    }
}

/**
 * A disposable for requests that are attached to a [View].
 *
 * [ViewTargetDisposable] is not disposed until its request is detached from the view.
 * This is because requests are automatically cancelled in [View.onDetachedFromWindow] and are
 * restarted in [View.onAttachedToWindow].
 */
internal class ViewTargetDisposable(
    private val requestId: UUID,
    private val target: ViewTarget<*>
) : Disposable {

    override val isDisposed
        get() = target.view.requestManager.currentRequestId != requestId

    override val currentJob: Deferred<ImageResult>
        get() = CompletableDeferred()

    override fun dispose() {
        if (isDisposed) return
        target.view.requestManager.clearCurrentRequest()
    }
}
