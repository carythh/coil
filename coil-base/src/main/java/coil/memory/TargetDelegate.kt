package coil.memory

import android.graphics.drawable.Drawable
import androidx.annotation.MainThread
import coil.request.ErrorResult
import coil.request.SuccessResult
import coil.target.Target

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
    override fun start(placeholder: Drawable?) = target.onStart(placeholder)
    override fun success(result: SuccessResult) = target.onSuccess(result.drawable)
    override fun error(result: ErrorResult) = target.onError(result.drawable)
}
