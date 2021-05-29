package coil.memory

import android.graphics.drawable.Drawable
import androidx.annotation.MainThread
import coil.request.ErrorResult
import coil.request.SuccessResult
import coil.target.Target
import coil.util.result

internal sealed class TargetDelegate {

    open val target: Target? get() = null

    @MainThread
    open fun start(placeholder: Drawable?) {}

    @MainThread
    open fun success(result: SuccessResult) {}

    @MainThread
    open fun error(result: ErrorResult) {}
}

internal object EmptyTargetDelegate : TargetDelegate()

internal class RealTargetDelegate(override val target: Target) : TargetDelegate() {

    override fun start(placeholder: Drawable?) {
        target.result = null
        target.onStart(placeholder)
    }

    override fun success(result: SuccessResult) {
        target.result = result
        target.onSuccess(result.drawable)
    }

    override fun error(result: ErrorResult) {
        target.result = result
        target.onError(result.drawable)
    }
}
