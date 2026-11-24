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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.onebar.app.R
import dev.onebar.app.data.LaunchableApp
import java.io.InputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val ROW_HEIGHT = 64.dp
private const val ICON_PX = 96

val CONTACT_ROW_HEIGHT = 68.dp
val CONTACT_ROW_GAP = 2.dp
val CONTACT_AVATAR_SIZE = 36.dp
val SWIPE_ACTION_SIZE = 48.dp
val CONTACT_ACTION_ICON_SIZE = 28.dp
val CONTACT_CARD_RADIUS = 16.dp
val CONTACT_HEADER_HEIGHT = 36.dp
val CONTACT_LIST_BOTTOM = 12.dp
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

internal const val NAVIGATION_SWIPE_TAG = "navigation-swipe-screen"
private const val NAVIGATION_SWIPE_THRESHOLD = 0.3f
private const val NAVIGATION_SETTLE_MILLIS = 180

@Composable
internal fun Modifier.navigationSwipe(
    swipeDirection: Float,
    enabled: Boolean,
    canNavigate: Boolean,
    onNavigate: () -> Unit,
    onNavigationBlocked: () -> Unit,
): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(0f) }
    val currentCanNavigate by rememberUpdatedState(canNavigate)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnNavigationBlocked by rememberUpdatedState(onNavigationBlocked)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_RESUME) {
                scope.launch { offset.snapTo(0f) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return this
        .clipToBounds()
        .testTag(NAVIGATION_SWIPE_TAG)
        .onSizeChanged { width = it.width.toFloat() }
        .then(
            if (enabled) {
                Modifier.pointerInput(swipeDirection) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        var active = false
                        var completed = false
                        var totalDrag = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) return@awaitEachGesture
                            if (change.changedToUpIgnoreConsumed()) {
                                completed = true
                                break
                            }
                            if (!change.pressed) break
                            val dragAmount = change.positionChange().x
                            totalDrag += dragAmount
                            if (!active && abs(totalDrag) > viewConfiguration.touchSlop) {
                                if (totalDrag * swipeDirection <= 0f) return@awaitEachGesture
                                active = true
                                scope.launch { offset.stop() }
                            }
                            if (active) {
                                change.consume()
                                if (width > 0f) {
                                    scope.launch {
                                        offset.snapTo(
                                            (totalDrag * swipeDirection).coerceIn(0f, width),
                                        )
                                    }
                                }
                            }
                        }
                        if (active) {
                            val passedThreshold = width > 0f &&
                                totalDrag * swipeDirection >= width * NAVIGATION_SWIPE_THRESHOLD
                            scope.launch {
                                if (completed && passedThreshold && currentCanNavigate) {
                                    offset.animateTo(
                                        width * swipeDirection,
                                        tween(NAVIGATION_SETTLE_MILLIS),
                                    )
                                    currentOnNavigate()
                                } else {
                                    offset.animateTo(0f, tween(NAVIGATION_SETTLE_MILLIS))
                                    if (completed && passedThreshold) currentOnNavigationBlocked()
                                }
                            }
                        }
                    }
                }
            } else {
                Modifier
            },
        )
        .graphicsLayer { translationX = offset.value }
}

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
 * Адаптивная иконка рисуется системой под маску (на Pixel — круг), и toBitmap возвращает уже
 * вырезанный кружок. Здесь слои рисуются целиком, на весь холст 108×108: получается исходный
 * квадрат, который потом скругливается формой в UI.
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

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching { iconBitmap(packageManager.getApplicationIcon(packageName)) }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier = modifier.size(AppItemSize).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(IconRadius)))
    } else {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(AppItemSize).clip(RoundedCornerShape(IconRadius)))
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

@Composable
fun ActionIcon(
    handlerBitmap: ImageBitmap?,
    fallbackIcon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Labeled(caption = caption, tileSize = SWIPE_ACTION_SIZE) { tileWidth, tileHeight, shown ->
        val button: Modifier = modifier
            .size(width = tileWidth, height = tileHeight)
            .clip(RoundedCornerShape(IconRadius))
            .clickable(onClick = onClick)

        Box(modifier = button, contentAlignment = Alignment.Center) {
            if (handlerBitmap != null) {
                Image(
                    bitmap = handlerBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(lerp(CONTACT_ACTION_ICON_SIZE, CONTACT_ACTION_ICON_SIZE - 4.dp, shown))
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Icon(
                    painter = fallbackIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(lerp(20.dp, 16.dp, shown)),
                    tint = tint,
                )
            }
        }
    }
}

/**
 * Иконку действия берём у приложения, которое это действие выполняет (сообщения, телефон):
 * пользователь видит тот же значок, что в лаунчере, а не нарисованный глиф.
 */
@Composable
fun rememberHandlerBitmap(action: String, uri: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return remember(action, uri) { handlerIcon(context, action, uri)?.let(::iconBitmap) }
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
    Labeled(caption = caption, tileSize = SWIPE_ACTION_SIZE) { tileWidth, tileHeight, shown ->
        Box(
            modifier = modifier
                .size(width = tileWidth, height = tileHeight)
                .background(badgeBackground, RoundedCornerShape(IconRadius))
                .semantics { contentDescription = label }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                color = badgeContentColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - shown / 6f
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
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
    if (caption == null) {
        content(tileSize, tileSize, 0f)
        return
    }
    val rowWidth = LocalSwipeRowWidth.current
    val expanded = (reveal / CAPTION_FADE_END).coerceIn(0f, 1f)
    val shown = ((reveal - CAPTION_FADE_START) / (CAPTION_FADE_END - CAPTION_FADE_START)).coerceIn(0f, 1f)
    val tileWidth = if (rowWidth > 0.dp) lerp(tileSize, rowWidth * 0.25f, expanded) else tileSize
    val tileHeight = lerp(tileSize, tileSize - TileShrink, shown)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CaptionGap * shown),
    ) {
        content(tileWidth, tileHeight, shown)
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

val AppItemSize = 44.dp

/** Геометрия плитки приложения: нужна и ряду-строке, и сетке избранного, чтобы ряды совпадали. */
val AppItemWidth = 70.dp
val AppItemHeight = 78.dp

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
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box {
            AppIcon(app.packageName)
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
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}
