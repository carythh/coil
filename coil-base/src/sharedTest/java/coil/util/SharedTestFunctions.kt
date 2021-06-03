@file:JvmName("-coil-base-SharedTestFunctions")

package coil.util

import android.graphics.Bitmap
import coil.size.PixelSize

val Bitmap.size: PixelSize
    get() = PixelSize(width, height)
