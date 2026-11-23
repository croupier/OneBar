package dev.onebar.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.onebar.app.R
import dev.onebar.app.data.LaunchableApp

val ROW_HEIGHT = 64.dp
private const val ICON_PX = 96

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier = modifier.size(AppItemSize).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    } else {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(AppItemSize))
    }
}

@Composable
fun ActionIcon(
    handlerIcon: Drawable?,
    fallbackIcon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val button: Modifier = modifier
        .size(AppItemSize)
        .clip(RoundedCornerShape(if (handlerIcon != null) 12.dp else 10.dp))
        .clickable(onClick = onClick)

    Labeled(caption = caption) {
        if (handlerIcon != null) {
            val bitmap = remember(handlerIcon) { handlerIcon.toBitmap(ICON_PX, ICON_PX).asImageBitmap() }
            Box(modifier = button, contentAlignment = Alignment.Center) {
                Image(bitmap = bitmap, contentDescription = contentDescription, modifier = Modifier.size(AppItemSize))
            }
        } else {
            Box(
                modifier = button.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = fallbackIcon, contentDescription = contentDescription, modifier = Modifier.size(22.dp), tint = tint)
            }
        }
    }
}

/**
 * Иконку действия берём у приложения, которое это действие выполняет (сообщения, телефон):
 * пользователь видит тот же значок, что в лаунчере, а не нарисованный глиф.
 */
@Composable
fun rememberHandlerIcon(action: String, uri: String): Drawable? {
    val context = LocalContext.current
    return remember(action, uri) { handlerIcon(context, action, uri) }
}

private fun handlerIcon(context: Context, action: String, uri: String): Drawable? {
    val pm = context.packageManager
    val resolved = runCatching { pm.resolveActivity(Intent(action, Uri.parse(uri)), 0) }.getOrNull()
    return runCatching { resolved?.loadIcon(pm) }.getOrNull()?.also {
        it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
    }
}

@Composable
fun BrandBadge(
    letter: String,
    badgeBackground: Color,
    badgeContentColor: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Labeled(caption = caption) {
        Box(
            modifier = modifier
                .size(AppItemSize)
                .background(badgeBackground, RoundedCornerShape(10.dp))
                .semantics { contentDescription = label }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = letter, color = badgeContentColor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Подпись под кнопкой: появляется, когда строку оттянули свайпом, но не до порога действия. */
@Composable
private fun Labeled(caption: String?, content: @Composable () -> Unit) {
    if (caption == null) {
        content()
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

val AppItemSize = 44.dp

@Composable
fun AppItem(
    app: LaunchableApp,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .size(width = 70.dp, height = 78.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box {
            AppIcon(app.packageName)
            if (favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(1.dp)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}
