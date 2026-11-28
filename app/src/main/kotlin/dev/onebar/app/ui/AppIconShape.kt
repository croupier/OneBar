package dev.onebar.app.ui

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.onebar.app.data.IconShape

internal val LocalIconShape = staticCompositionLocalOf { IconShape.SQUARE }

private const val ICON_MASK_SIZE = 100

internal fun isSystemIconShapeAvailable(): Boolean = systemIconMask() != null

private fun systemIconMask(): AndroidPath? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return runCatching {
        AdaptiveIconDrawable(
            ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(Color.TRANSPARENT),
        ).apply {
            setBounds(0, 0, ICON_MASK_SIZE, ICON_MASK_SIZE)
        }.iconMask.takeIf { !it.isEmpty }
    }.getOrNull()
}

internal fun appIconShape(option: IconShape): Shape = when (option) {
    IconShape.SYSTEM -> systemIconMask()?.let(::SystemIconShape) ?: RectangleShape
    IconShape.SQUARE -> RoundedCornerShape(IconRadius)
    IconShape.CIRCLE -> CircleShape
    IconShape.SOFT_SQUARE -> RoundedCornerShape(percent = 30)
}

private class SystemIconShape(private val mask: AndroidPath) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = AndroidPath(mask)
        path.transform(
            Matrix().apply {
                setScale(size.width / ICON_MASK_SIZE, size.height / ICON_MASK_SIZE)
            },
        )
        return Outline.Generic(path.asComposePath())
    }
}
