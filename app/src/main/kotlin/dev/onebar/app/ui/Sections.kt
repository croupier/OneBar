package dev.onebar.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.onebar.app.R
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.ui.theme.googleBadgeBackground
import dev.onebar.app.ui.theme.googleBadgeContent
import dev.onebar.app.ui.theme.yandexBadgeBackground
import dev.onebar.app.ui.theme.yandexBadgeContent
import kotlinx.coroutines.launch

@Composable
fun ContactsSection(
    state: SearchState,
    modifier: Modifier,
    smsIcon: Drawable?,
    callIcon: Drawable?,
    onRequestPermission: () -> Unit,
    onSms: (ContactEntry) -> Unit,
    onCall: (ContactEntry) -> Unit,
    onOpen: (ContactEntry) -> Unit,
    onMenu: (ContactEntry) -> Unit,
) {
    if (!state.contactsGranted) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.contacts_denied), modifier = Modifier.weight(1f))
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow)) }
        }
        return
    }
    if (state.contacts.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.contacts_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CONTACT_ROW_GAP),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = CONTACT_LIST_BOTTOM),
        ) {
            items(state.contacts, key = { it.entry.contactId }) { row ->
                ContactRow(
                    row = row,
                    smsIcon = smsIcon,
                    callIcon = callIcon,
                    favorite = !state.favoritesMode && row.entry.lookupKey in state.favContactKeys,
                    onSms = { onSms(row.entry) },
                    onCall = { onCall(row.entry) },
                    onOpen = { onOpen(row.entry) },
                    onMenu = { onMenu(row.entry) },
                )
            }
        }
    }
}

/** Плиток в ряду избранного приложений: столько ширины экрана влезает с зазором. */
fun appsColumns(width: Dp): Int =
    ((width - 16.dp + 4.dp) / (AppItemWidth + 4.dp)).toInt().coerceAtLeast(1)

private const val MAX_FAV_APP_ROWS = 2

/**
 * Избранные приложения — сетка до двух рядов, а не одна горизонтальная строка: в избранном
 * клавиатуры нет, освободившуюся высоту отдаём спискам. Сверх двух рядов сетка листается сама.
 */
@Composable
fun FavoritesAppsSection(
    apps: List<LaunchableApp>,
    columns: Int,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp) -> Unit,
) {
    if (apps.isEmpty()) return
    val rows = ((apps.size + columns - 1) / columns).coerceAtMost(MAX_FAV_APP_ROWS)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxWidth().height(AppItemHeight * rows + 12.dp).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItem(
                app = app,
                favorite = false,
                onClick = { onOpen(app) },
                onLongClick = { onMenu(app) },
            )
        }
    }
}

@Composable
private fun ContactRow(
    row: ContactRowUi,
    smsIcon: Drawable?,
    callIcon: Drawable?,
    favorite: Boolean,
    onSms: () -> Unit,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val canAct = row.entry.number != null
    // Подпись строки журнала собирается здесь, а не во ViewModel: названия действий локализуемы.
    val detail = when {
        row.logKind != null -> {
            val kind = stringResource(
                if (row.logKind == CommunicationKind.CALL) R.string.caption_call else R.string.caption_sms
            )
            "$kind · ${row.logTime} · ${row.entry.number}"
        }
        else -> row.entry.number.orEmpty()
    }
    SwipeableRow(enabled = canAct, onTrigger = onCall) { labels ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CONTACT_ROW_HEIGHT)
                .clip(RoundedCornerShape(CONTACT_CARD_RADIUS))
                .background(contactCardColor()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .combinedClickable(onClick = onOpen, onLongClick = onMenu),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    ContactAvatar(contactId = row.entry.contactId, name = row.entry.name)
                    if (favorite) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(14.dp)
                                .background(contactCardColor(), CircleShape),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = row.entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (canAct) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ActionIcon(
                            handlerIcon = smsIcon,
                            fallbackIcon = painterResource(R.drawable.ic_sms),
                            contentDescription = stringResource(R.string.sms_action),
                            onClick = onSms,
                            caption = if (labels) stringResource(R.string.caption_sms) else null,
                        )
                        ActionIcon(
                            handlerIcon = callIcon,
                            fallbackIcon = painterResource(R.drawable.ic_call),
                            contentDescription = stringResource(R.string.call_action),
                            onClick = onCall,
                            caption = if (labels) stringResource(R.string.caption_call) else null,
                        )
                    }
                } else {
                    Spacer(Modifier.width(CONTACT_ACTION_SIZE * 2 + 2.dp))
                }
            }
        }
    }
}

/**
 * Свайп справа налево по строке: протяжка плавно, за пальцем, раскрывает кнопки — плитка
 * расширяется, под ней проступает подпись. Отпущено меньше чем на 30 dp — строка возвращается;
 * дальше кнопки остаются раскрытыми, а за 50 % ширины сразу выполняется правое действие.
 * При возвращении в приложение строка сбрасывается в исходное состояние.
 */
@Composable
private fun SwipeableRow(
    enabled: Boolean,
    onTrigger: () -> Unit,
    content: @Composable (labels: Boolean) -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    var labels by remember { mutableStateOf(false) }
    // 0f — покой, 1f — протяжка до порога действия; во время жеста значение ставится сразу.
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                labels = false
                scope.launch { reveal.snapTo(0f) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalSwipeReveal provides reveal.value) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .graphicsLayer { translationX = -width * ROW_SHIFT * reveal.value }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val minDrag = MIN_DRAG_DP.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, amount ->
                            val full = width * TRIGGER
                            if (full > 0f) {
                                val pulled = (reveal.value * full - amount).coerceIn(0f, full)
                                scope.launch { reveal.snapTo(pulled / full) }
                            }
                        },
                        onDragEnd = {
                            val full = width * TRIGGER
                            val pulled = reveal.value * full
                            when {
                                full > 0f && pulled >= full -> {
                                    labels = false
                                    scope.launch { reveal.animateTo(0f, settle()) }
                                    onTrigger()
                                }
                                pulled >= minDrag -> {
                                    labels = true
                                    scope.launch { reveal.animateTo(SETTLED, settle()) }
                                }
                                else -> scope.launch { reveal.animateTo(if (labels) SETTLED else 0f, settle()) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { reveal.animateTo(if (labels) SETTLED else 0f, settle()) }
                        },
                    )
                },
        ) {
            content(labels || reveal.value > 0f)
        }
    }
}

/** Доводка строки после отпускания: без пружинного перелёта, иначе кнопка «прыгает» за край. */
private fun settle() = tween<Float>(SETTLE_MS, easing = FastOutSlowInEasing)

private const val MIN_DRAG_DP = 30
private const val TRIGGER = 0.5f

/** Доля раскрытия, на которой строка остаётся подписанной: подписи к этому моменту уже видны. */
private const val SETTLED = 0.6f

/** Насколько ширина строки она уходит влево следом за пальцем. */
private const val ROW_SHIFT = 0.06f
private const val SETTLE_MS = 220

@Composable
fun SearchHistorySection(
    queries: List<String>,
    onPick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(queries, key = { it }) { query ->
            HistoryRow(text = query, onPick = { onPick(query) })
        }
    }
}

@Composable
private fun HistoryRow(
    text: String,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.padding(start = 14.dp).size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_submit),
            contentDescription = stringResource(R.string.history_restore),
            modifier = Modifier
                .padding(end = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .padding(6.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WebSuggestionsSection(
    state: SearchState,
    onPick: (String) -> Unit,
    onSearch: (WebEngine, String) -> Unit,
) {
    if (state.suggestions.isEmpty()) return

    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(state.suggestions, key = { it }) { suggestion ->
            WebRow(
                text = suggestion,
                onPick = { onPick(suggestion) },
                onSearch = { engine -> onSearch(engine, suggestion) },
            )
        }
    }
}

@Composable
private fun WebRow(
    text: String,
    onPick: () -> Unit,
    onSearch: (WebEngine) -> Unit,
) {
    SwipeableRow(enabled = true, onTrigger = { onSearch(WebEngine.GOOGLE) }) { labels ->
        Row(
            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPick)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BrandBadge(
                letter = "Я",
                badgeBackground = yandexBadgeBackground,
                badgeContentColor = yandexBadgeContent,
                label = stringResource(R.string.search_yandex),
                onClick = { onSearch(WebEngine.YANDEX) },
                caption = if (labels) "Яндекс" else null,
            )
            BrandBadge(
                letter = "G",
                badgeBackground = googleBadgeBackground,
                badgeContentColor = googleBadgeContent,
                label = stringResource(R.string.search_google),
                onClick = { onSearch(WebEngine.GOOGLE) },
                caption = if (labels) "Google" else null,
            )
        }
    }
}
