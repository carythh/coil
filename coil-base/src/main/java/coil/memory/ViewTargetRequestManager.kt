package coil.memory

import android.view.View
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.ViewTargetDisposable
import coil.util.getCompletedOrNull
import coil.util.isMainThread
import coil.util.requestManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Ensures that at most one executed [ImageRequest] can be attached to a given [View] at one time.
 *
 * @see requestManager
 */
internal class ViewTargetRequestManager(private val view: View) : View.OnAttachStateChangeListener {

    // The disposable for the current request attached to this view.
    @GuardedBy("this") private var currentDisposable: ViewTargetDisposable? = null

    // A pending operation that is posting to the main thread to clear the current request.
    @GuardedBy("this") private var pendingClear: Job? = null

    // Only accessed from the main thread.
    private var currentRequest: ViewTargetRequestDelegate? = null
    private var isRestart = false
    private var skipAttach = true

    /** Return 'true' if [disposable] is not attached to this view. */
    @Synchronized
    fun isDisposed(disposable: ViewTargetDisposable): Boolean {
        return disposable !== currentDisposable
    }

    /** Create and return a new disposable unless this is a restarted request. */
    @Synchronized
    fun getDisposable(job: Deferred<ImageResult>): ViewTargetDisposable {
        // If this is a restarted request, update the current disposable and return it.
        val disposable = currentDisposable
        if (disposable != null && isMainThread() && isRestart) {
            isRestart = false
            return disposable.apply { this.job = job }
        }

        // Else, create a new disposable as this is a new request.
        pendingClear?.cancel()
        pendingClear = null
        return ViewTargetDisposable(view, job).also { currentDisposable = it }
    }

    /** Cancel any in progress work and detach [currentRequest] from this view. */
    @Synchronized
    @OptIn(DelicateCoroutinesApi::class)
    fun dispose() {
        pendingClear?.cancel()
        pendingClear = GlobalScope.launch(Dispatchers.Main.immediate) { setRequest(null) }
        currentDisposable = null
    }

    /** Return the completed value of the latest job if it has completed. Else, return 'null'. */
    @Synchronized
    fun getResult(): ImageResult? {
        return currentDisposable?.job?.getCompletedOrNull()
    }

    /** Attach [request] to this view and cancel the old request. */
    @MainThread
    fun setRequest(request: ViewTargetRequestDelegate?) {
        currentRequest?.cancel()
        currentRequest = request
        skipAttach = true
    }

    @MainThread
    override fun onViewAttachedToWindow(v: View) {
        if (skipAttach) {
            skipAttach = false
            return
        }

        currentRequest?.let { request ->
            // As this is called from the main thread, isRestart will
            // be cleared synchronously as part of request.restart().
            isRestart = true
            request.restart()
        }
    }

    @MainThread
    override fun onViewDetachedFromWindow(v: View) {
        skipAttach = false
        currentRequest?.cancel()
    }
}
