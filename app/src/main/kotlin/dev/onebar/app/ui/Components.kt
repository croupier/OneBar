package dev.onebar.app.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.graphics.drawable.toBitmap
import dev.onebar.app.R
import dev.onebar.app.data.LaunchableApp
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ROW_HEIGHT = 64.dp
private const val ICON_PX = 96

val CONTACT_ROW_HEIGHT = 68.dp
val CONTACT_ROW_GAP = 2.dp
val CONTACT_AVATAR_SIZE = 36.dp
val SWIPE_ACTION_SIZE = 48.dp
val CONTACT_CARD_RADIUS = 16.dp
val CONTACT_HEADER_HEIGHT = 36.dp
val CONTACT_LIST_BOTTOM = 12.dp
val ContactCardPadding = 12.dp
val ContactContentStart = 12.dp
val ContactContentEnd = 2.dp
val ContactActionEnd = ContactCardPadding + ContactContentEnd
val ContactAvatarStart = ContactActionEnd
val CONTACT_SECTION_HEIGHT =
    CONTACT_HEADER_HEIGHT + CONTACT_ROW_HEIGHT * 3 + CONTACT_ROW_GAP * 2 + CONTACT_LIST_BOTTOM

/** Стиль иконок: квадрат со скруглением, а не круглая маска лаунчера. */
val IconRadius = 12.dp

/**
 * Доля протяжки свайпа в строке: 0 — покой, 1 — достигнут порог действия. Значение ставит
 * `SwipeableRow` из Sections.kt; наружу строки оно не выходит, поэтому дефолт — нулевой.
 */
internal val LocalSwipeReveal = compositionLocalOf { 0f }

/** Ширина строки в dp: к 30 % протяжки кнопки доходят до четверти ширины каждая. */
internal val LocalSwipeRowWidth = compositionLocalOf { 0.dp }
internal val LocalSwipeRowHeight = compositionLocalOf { 0.dp }

/** null — обычная строка; 0..1 — финальное раскрытие выбранного действия на всю строку. */
internal val LocalSwipeActionProgress = compositionLocalOf<Float?> { null }

/** На столько плитка становится ниже, когда под ней появляется подпись. */
private val TileShrink = 8.dp
private val CaptionLine = 18.dp
private val CaptionGap = 4.dp

/** Подпись начинает проступать не с первого пикселя протяжки и доходит до готовности к 0,6. */
private const val CAPTION_FADE_START = 0.25f
private const val CAPTION_FADE_END = 0.6f

/**
 * Во сколько раз увеличить передний план. В круге система показывает только центр площадки
 * 72/108, поэтому без увеличения глиф в квадрате кажется мельче, чем в лаунчере.
 */
private const val FOREGROUND_SCALE = 1.3f

/**
 * Адаптивная иконка рисуется под маску лаунчера. Здесь слои рисуются целиком, на весь холст
 * 108×108, поэтому получаем исходный квадрат, который потом скругливается формой в UI.
 */
internal fun iconBitmap(source: Drawable): ImageBitmap {
    if (source !is AdaptiveIconDrawable) return source.toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    source.background?.let { layer ->
        layer.setBounds(0, 0, ICON_PX, ICON_PX)
        layer.draw(canvas)
    }
    canvas.scale(FOREGROUND_SCALE, FOREGROUND_SCALE, ICON_PX / 2f, ICON_PX / 2f)
    source.foreground?.let { layer ->
        layer.setBounds(0, 0, ICON_PX, ICON_PX)
        layer.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/**
 * Иконка приложения-обработчика, разложенная на слои: подложка растягивается вместе с тайлом,
 * глиф остаётся квадратным. Без такого разделения плитке СМС/звонка нечего расширять — иконка
 * и есть плитка, — поэтому при протяжке она отставала от бейджей Я/G, у которых растёт плашка.
 */
class HandlerIcon(
    val plateBitmap: ImageBitmap?,
    val plateColor: Color?,
    val glyph: ImageBitmap?,
)

/** Один слой адаптивной иконки на холсте 108×108; передний план рисуется с увеличением. */
private fun layerBitmap(layer: Drawable?, scale: Float): ImageBitmap? {
    if (layer == null) return null
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale, ICON_PX / 2f, ICON_PX / 2f)
    layer.setBounds(0, 0, ICON_PX, ICON_PX)
    layer.draw(canvas)
    return bitmap.asImageBitmap()
}

/** Цвет угла неадаптивной иконки: у таких иконок подложка — ровно он. */
private fun edgeColor(bitmap: Bitmap): Color {
    val corners = listOf(
        3 to 3,
        bitmap.width - 4 to 3,
        3 to bitmap.height - 4,
        bitmap.width - 4 to bitmap.height - 4,
    ).map { bitmap.getPixel(it.first, it.second) }
    val painted = corners.map { Color(it) }.filter { it.alpha > 0.5f }
    if (painted.isEmpty()) return Color.Transparent
    val size = painted.size
    val red = painted.sumOf { it.red.toDouble() } / size
    val green = painted.sumOf { it.green.toDouble() } / size
    val blue = painted.sumOf { it.blue.toDouble() } / size
    return Color(red.toFloat(), green.toFloat(), blue.toFloat())
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = AppItemSize) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching { iconBitmap(packageManager.getApplicationIcon(packageName)) }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(IconRadius)))
    } else {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size).clip(RoundedCornerShape(IconRadius)))
    }
}

@Composable
internal fun contactCardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() > 0.5f) {
        scheme.surfaceContainerLowest
    } else {
        scheme.surfaceContainerLow
    }
}

@Composable
fun ContactAvatar(contactId: Long, name: String, photoUri: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val photoSize = with(density) { CONTACT_AVATAR_SIZE.roundToPx() }
    val photo = produceState<ImageBitmap?>(initialValue = null, photoUri, context, photoSize) {
        value = photoUri?.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) {
                runCatching { decodeContactPhoto(context, contactId, photoSize) }
                    .getOrNull()?.asImageBitmap()
            }
        }
    }.value
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.surfaceVariant to scheme.onSurfaceVariant,
    )
    val index = (contactId.hashCode() and Int.MAX_VALUE) % palette.size
    val (background, content) = palette[index]
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(CONTACT_AVATAR_SIZE)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (photoUri.isNullOrBlank()) {
            Text(text = initial, color = content, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun decodeContactPhoto(context: Context, contactId: Long, targetPx: Int): Bitmap? {
    val target = targetPx.coerceAtLeast(1)
    val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = openContactPhoto(context, contactUri) ?: return null
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= target && bounds.outHeight / (sampleSize * 2) >= target) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return openContactPhoto(context, contactUri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun openContactPhoto(context: Context, contactUri: Uri): InputStream? =
    ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, contactUri, false)
        ?: ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, contactUri, true)

/**
 * Плитка действия: подложка заполняет весь тайл и тянется по ширине вместе с протяжкой, а
 * содержимое ([content]) держит сторону тайла и чуть уменьшается к раскрытию подписи. Один и тот
 * же код рисует и брендовый бейдж, и иконку обработчика — поэтому СМС/звонок и Я/G меняются
 * одинаково.
 */
@Composable
private fun ActionTile(
    plateBitmap: ImageBitmap?,
    plateColor: Color?,
    label: String?,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plateModifier: Modifier = Modifier,
    content: @Composable (side: Dp, scale: Float) -> Unit,
) {
    Labeled(caption = caption, tileSize = SWIPE_ACTION_SIZE) { tileWidth, tileHeight, shown ->
        Box(
            modifier = modifier
                .size(width = tileWidth, height = tileHeight)
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                plateBitmap != null -> Image(
                    bitmap = plateBitmap,
                    contentDescription = null,
                    modifier = plateModifier.fillMaxSize().clip(RoundedCornerShape(IconRadius)),
                    contentScale = ContentScale.FillBounds,
                )

                plateColor != null -> Box(
                    modifier = plateModifier.fillMaxSize().background(plateColor, RoundedCornerShape(IconRadius)),
                )
            }
            content(minOf(tileWidth, tileHeight), 1f - shown / 6f)
        }
    }
}

@Composable
fun ActionIcon(
    handler: HandlerIcon?,
    fallbackIcon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plateModifier: Modifier = Modifier,
    caption: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    ActionTile(
        plateBitmap = handler?.plateBitmap,
        plateColor = handler?.plateColor,
        label = null,
        caption = caption,
        onClick = onClick,
        modifier = modifier,
        plateModifier = plateModifier,
    ) { side, scale ->
        val glyph = handler?.glyph
        val layer = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        if (glyph != null) {
            Image(
                bitmap = glyph,
                contentDescription = contentDescription,
                modifier = Modifier.size(side).then(layer),
            )
        } else {
            Icon(
                painter = fallbackIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(side).then(layer),
                tint = tint,
            )
        }
    }
}

/**
 * Иконку действия берём у приложения, которое это действие выполняет (сообщения, телефон):
 * пользователь видит тот же значок, что в лаунчере, а не нарисованный глиф.
 */
@Composable
fun rememberHandlerIcon(action: String, uri: String): HandlerIcon? {
    val context = LocalContext.current.applicationContext
    return remember(action, uri) { handlerIcon(context, action, uri) }
}

private fun handlerIcon(context: Context, action: String, uri: String): HandlerIcon? {
    val pm = context.packageManager
    val resolved = runCatching { pm.resolveActivity(Intent(action, Uri.parse(uri)), 0) }.getOrNull()
    val drawable = runCatching { resolved?.loadIcon(pm) }.getOrNull() ?: return null
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    return if (drawable is AdaptiveIconDrawable) {
        HandlerIcon(
            plateBitmap = layerBitmap(drawable.background, 1f),
            plateColor = null,
            glyph = layerBitmap(drawable.foreground, FOREGROUND_SCALE),
        )
    } else {
        val bitmap = runCatching { drawable.toBitmap(ICON_PX, ICON_PX) }.getOrNull() ?: return null
        HandlerIcon(plateBitmap = null, plateColor = edgeColor(bitmap), glyph = bitmap.asImageBitmap())
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
    ActionTile(
        plateBitmap = null,
        plateColor = badgeBackground,
        label = label,
        caption = caption,
        onClick = onClick,
        modifier = modifier,
    ) { _, scale ->
        Text(
            text = letter,
            color = badgeContentColor,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

/**
 * Кнопка с подписью снизу. Пока строку тянут свайпом, ширина плитки и подпись следуют за
 * протяжкой ([LocalSwipeReveal]): к 30 % ширины плитка доходит до четверти строки, подпись
 * выезжает из-под неё и проступает, а высота плитки уменьшается, чтобы подпись не обрезалась.
 */
@Composable
private fun Labeled(
    caption: String?,
    tileSize: Dp = AppItemSize,
    content: @Composable (tileWidth: Dp, tileHeight: Dp, shown: Float) -> Unit,
) {
    val reveal = LocalSwipeReveal.current
    val actionProgress = LocalSwipeActionProgress.current
    if (caption == null && actionProgress == null) {
        content(tileSize, tileSize, 0f)
        return
    }
    val rowWidth = LocalSwipeRowWidth.current
    val rowHeight = LocalSwipeRowHeight.current
    val expanded = (reveal / CAPTION_FADE_END).coerceIn(0f, 1f)
    val shown = ((reveal - CAPTION_FADE_START) / (CAPTION_FADE_END - CAPTION_FADE_START)).coerceIn(0f, 1f)
    val tileWidth = when {
        actionProgress != null && rowWidth > 0.dp -> lerp(rowWidth * 0.25f, rowWidth, actionProgress)
        rowWidth > 0.dp -> lerp(tileSize, rowWidth * 0.25f, expanded)
        else -> tileSize
    }
    val tileHeight = if (actionProgress != null && rowHeight > 0.dp) {
        lerp(tileSize - TileShrink, rowHeight, actionProgress)
    } else {
        lerp(tileSize, tileSize - TileShrink, shown)
    }
    val iconShown = if (actionProgress != null) 1f - actionProgress else shown
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CaptionGap * shown),
    ) {
        content(tileWidth, tileHeight, iconShown)
        if (caption != null) {
            Box(
                modifier = Modifier.height(CaptionLine * shown).clipToBounds(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = caption,
                    modifier = Modifier.height(CaptionLine).alpha(shown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

val AppItemSize = 44.dp
val AppIconSize = 72.dp

/** Геометрия плитки приложения: нужна и ряду-строке, и сетке избранного, чтобы ряды совпадали. */
val AppItemWidth = 72.dp
val AppItemHeight = 104.dp
val AppIconInset = (AppItemWidth - AppIconSize) / 2
val AppRowStart = ContactAvatarStart - AppIconInset
val AppRowEndInset = ContactActionEnd - AppIconInset

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
            .size(width = AppItemWidth, height = AppItemHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box {
            AppIcon(
                app.packageName,
                size = AppIconSize,
                modifier = Modifier.testTag("app-icon-${app.packageName}"),
            )
            if (favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(1.dp)
                        .size(14.dp)
                        .testTag("app-favorite-${app.packageName}")
                        .background(MaterialTheme.colorScheme.background, CircleShape),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = app.label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(AppIconSize)
                .testTag("app-label-${app.packageName}"),
        )
    }
}
