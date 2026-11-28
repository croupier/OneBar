package dev.onebar.app.ui

import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.onebar.app.data.IconShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconShapeTest {

    @Test
    fun `формы_иконок_соответствуют_выбору`() {
        listOf(IconShape.SQUARE, IconShape.CIRCLE, IconShape.SOFT_SQUARE).forEach { shape ->
            assertFalse(contains(appIconShape(shape), 1f, 1f))
            assertTrue(contains(appIconShape(shape), SIZE / 2f, SIZE / 2f))
        }
        assertTrue(contains(appIconShape(IconShape.SQUARE), 5f, 5f))
        assertFalse(contains(appIconShape(IconShape.SOFT_SQUARE), 5f, 5f))
    }

    @Test
    fun `системная_форма_использует_контур_или_надежный_квадрат`() {
        val outline = appIconShape(IconShape.SYSTEM).createOutline(
            size = Size(SIZE, SIZE),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        )

        assertEquals(isSystemIconShapeAvailable(), outline is Outline.Generic)
        assertTrue(contains(appIconShape(IconShape.SYSTEM), SIZE / 2f, SIZE / 2f))
        if (outline is Outline.Generic) {
            val bounds = outline.path.getBounds()
            assertTrue(bounds.width > 0f && bounds.height > 0f)
        }
    }

    private fun contains(shape: Shape, x: Float, y: Float): Boolean {
        val outline = shape.createOutline(
            size = Size(SIZE, SIZE),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        )
        val path = when (outline) {
            is Outline.Rectangle -> AndroidPath().apply {
                addRect(
                    outline.rect.left,
                    outline.rect.top,
                    outline.rect.right,
                    outline.rect.bottom,
                    AndroidPath.Direction.CW,
                )
            }

            is Outline.Rounded -> AndroidPath().apply {
                val rounded = outline.roundRect
                addRoundRect(
                    RectF(rounded.left, rounded.top, rounded.right, rounded.bottom),
                    floatArrayOf(
                        rounded.topLeftCornerRadius.x,
                        rounded.topLeftCornerRadius.y,
                        rounded.topRightCornerRadius.x,
                        rounded.topRightCornerRadius.y,
                        rounded.bottomRightCornerRadius.x,
                        rounded.bottomRightCornerRadius.y,
                        rounded.bottomLeftCornerRadius.x,
                        rounded.bottomLeftCornerRadius.y,
                    ),
                    AndroidPath.Direction.CW,
                )
            }

            is Outline.Generic -> outline.path.asAndroidPath()
        }
        val clip = Region(0, 0, SIZE.toInt(), SIZE.toInt())
        val region = Region()
        region.setPath(path, clip)
        return region.contains(x.toInt(), y.toInt())
    }

    private companion object {
        const val SIZE = 64f
    }
}
