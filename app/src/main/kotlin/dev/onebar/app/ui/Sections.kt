package dev.onebar.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import kotlin.math.abs
import kotlinx.coroutines.launch

internal class SwipeRowController {
    var activeKey by mutableStateOf<String?>(null)
        private set

    fun activate(key: String) {
        activeKey = key
    }

    fun clear(key: String) {
        if (activeKey == key) activeKey = null
    }
}

@Composable
internal fun ContactsSection(
    state: SearchState,
    modifier: Modifier,
    swipeRows: SwipeRowController,
    smsIcon: ImageBitmap?,
    callIcon: ImageBitmap?,
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
            contentPadding = PaddingValues(bottom = CONTACT_LIST_BOTTOM),
        ) {
            items(state.contacts, key = { "contact:${it.entry.contactId}" }) { row ->
                ContactRow(
                    row = row,
                    rowKey = "contact:${row.entry.contactId}",
                    swipeRows = swipeRows,
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

/** Плиток в ряду: столько ширины экрана влезает с зазором. */
fun appsColumns(width: Dp): Int =
    ((width - 16.dp + 4.dp) / (AppItemWidth + 4.dp)).toInt().coerceAtLeast(1)

/**
 * Приложения сеткой, когда высоты хватает на два ряда: горизонтальная строка при большой
 * высоте оставила бы пустое поле. Сверх отданных рядов сетка листается сама.
 */
@Composable
fun AppsGridSection(
    apps: List<LaunchableApp>,
    columns: Int,
    rows: Int,
    favoritePackages: Set<String>,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp) -> Unit,
) {
    if (apps.isEmpty()) return
    val usedRows = ((apps.size + columns - 1) / columns).coerceAtMost(rows).coerceAtLeast(1)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxWidth()
            .height(AppsRowHeight * usedRows)
            .padding(vertical = 6.dp)
            .testTag("apps-grid"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItem(
                app = app,
                favorite = app.packageName in favoritePackages,
                onClick = { onOpen(app) },
                onLongClick = { onMenu(app) },
            )
        }
    }
}

@Composable
private fun ContactRow(
    row: ContactRowUi,
    rowKey: String,
    swipeRows: SwipeRowController,
    smsIcon: ImageBitmap?,
    callIcon: ImageBitmap?,
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
    SwipeableRow(rowKey = rowKey, swipeRows = swipeRows, enabled = canAct, onTrigger = onCall) { labels ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
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
                    ContactAvatar(
                        contactId = row.entry.contactId,
                        name = row.entry.name,
                        photoUri = row.entry.photoUri,
                    )
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
                            handlerBitmap = smsIcon,
                            fallbackIcon = painterResource(R.drawable.ic_sms),
                            contentDescription = stringResource(R.string.sms_action),
                            onClick = onSms,
                            modifier = Modifier.testTag("swipe-action-sms-$rowKey"),
                            caption = if (labels) stringResource(R.string.caption_sms) else null,
                        )
                        ActionIcon(
                            handlerBitmap = callIcon,
                            fallbackIcon = painterResource(R.drawable.ic_call),
                            contentDescription = stringResource(R.string.call_action),
                            onClick = onCall,
                            modifier = Modifier.testTag("swipe-action-call-$rowKey"),
                            caption = if (labels) stringResource(R.string.caption_call) else null,
                        )
                    }
                } else {
                    Spacer(Modifier.width(SWIPE_ACTION_SIZE * 2 + 2.dp))
                }
            }
        }
    }
}

/**
 * Свайп справа налево по строке: протяжка плавно, за пальцем, расширяет кнопки до 25 %
 * ширины строки каждая, под ними проступают подписи. Меньше 30 % — строка возвращается,
 * от 30 % — кнопки остаются раскрытыми, за 50 % ширины выполняется правое действие.
 * Обратный ход от 30 % закрывает строку; свайп по другой строке закрывает предыдущую.
 * Сама строка не сдвигается; при возвращении в приложение она сбрасывается в исходное состояние.
 */
@Composable
private fun SwipeableRow(
    rowKey: String,
    swipeRows: SwipeRowController,
    enabled: Boolean,
    onTrigger: () -> Unit,
    content: @Composable (labels: Boolean) -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    // 0f — покой, 1f — протяжка до порога действия; во время жеста значение ставит сразу.
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowWidth = with(density) { width.toDp() }
    val isActive = swipeRows.activeKey == rowKey
    val currentIsActive by rememberUpdatedState(isActive)

    LaunchedEffect(isActive) {
        if (!isActive && reveal.value != 0f) reveal.animateTo(0f, settle())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, rowKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                swipeRows.clear(rowKey)
                scope.launch { reveal.snapTo(0f) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            swipeRows.clear(rowKey)
        }
    }

    CompositionLocalProvider(
        LocalSwipeReveal provides reveal.value,
        LocalSwipeRowWidth provides rowWidth,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("swipe-row-$rowKey")
                .onSizeChanged { width = it.width }
                .pointerInput(rowKey, swipeRows, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Main,
                        )
                        var dragging = false
                        var reversing = false
                        var completed = false
                        var totalDrag = 0f
                        var verticalDrag = 0f
                        var reverseTravel = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                completed = true
                                if (dragging) change.consume()
                                break
                            }
                            if (!change.pressed || change.isConsumed) break
                            val dragAmount = change.positionChange()
                            totalDrag += dragAmount.x
                            verticalDrag += dragAmount.y
                            if (!dragging) {
                                if (abs(totalDrag) <= viewConfiguration.touchSlop &&
                                    abs(verticalDrag) <= viewConfiguration.touchSlop
                                ) {
                                    continue
                                }
                                if (abs(verticalDrag) > abs(totalDrag)) return@awaitEachGesture
                                reversing = totalDrag > 0f
                                if (reversing && !currentIsActive) return@awaitEachGesture
                                if (reversing) reverseTravel = totalDrag - dragAmount.x
                                dragging = true
                                swipeRows.activate(rowKey)
                            }
                            if (reversing) reverseTravel += dragAmount.x.coerceAtLeast(0f)
                            change.consume()
                            val full = width * TRIGGER
                            if (full > 0f) {
                                val pulled = (reveal.value * full - dragAmount.x).coerceIn(0f, full)
                                scope.launch { reveal.snapTo(pulled / full) }
                            }
                        }
                        if (!dragging) return@awaitEachGesture
                        if (!completed) {
                            if (currentIsActive) {
                                scope.launch { reveal.animateTo(SETTLED, settle()) }
                            } else {
                                swipeRows.clear(rowKey)
                            }
                            return@awaitEachGesture
                        }
                        val full = width * TRIGGER
                        val pulled = reveal.value * full
                        val minDrag = width * LABEL_TRIGGER
                        when {
                            reversing && reverseTravel >= minDrag -> swipeRows.clear(rowKey)
                            full > 0f && pulled >= full -> {
                                swipeRows.clear(rowKey)
                                onTrigger()
                            }
                            reversing -> {
                                swipeRows.activate(rowKey)
                                scope.launch { reveal.animateTo(SETTLED, settle()) }
                            }
                            pulled >= minDrag -> {
                                swipeRows.activate(rowKey)
                                scope.launch { reveal.animateTo(SETTLED, settle()) }
                            }
                            else -> swipeRows.clear(rowKey)
                        }
                    }
                },
        ) {
            content(isActive || reveal.value > 0f)
        }
    }
}

/** Доводка строки после отпускания: без пружинного перелёта, иначе кнопка «прыгает» за край. */
private fun settle() = tween<Float>(SETTLE_MS, easing = FastOutSlowInEasing)

private const val LABEL_TRIGGER = 0.3f
private const val TRIGGER = 0.5f

/** Доля порога действия, на которой кнопки остаются подписанными: 30 % / 50 % = 0,6. */
private const val SETTLED = LABEL_TRIGGER / TRIGGER
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
internal fun WebSuggestionsSection(
    state: SearchState,
    swipeRows: SwipeRowController,
    onPick: (String) -> Unit,
    onSearch: (WebEngine, String) -> Unit,
) {
    if (state.suggestions.isEmpty()) return

    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(state.suggestions, key = { "web:$it" }) { suggestion ->
            WebRow(
                text = suggestion,
                rowKey = "web:$suggestion",
                swipeRows = swipeRows,
                onPick = { onPick(suggestion) },
                onSearch = { engine -> onSearch(engine, suggestion) },
            )
        }
    }
}

@Composable
private fun WebRow(
    text: String,
    rowKey: String,
    swipeRows: SwipeRowController,
    onPick: () -> Unit,
    onSearch: (WebEngine) -> Unit,
) {
    SwipeableRow(rowKey = rowKey, swipeRows = swipeRows, enabled = true, onTrigger = { onSearch(WebEngine.GOOGLE) }) { labels ->
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
                modifier = Modifier.testTag("swipe-action-yandex-$rowKey"),
                caption = if (labels) "Яндекс" else null,
            )
            BrandBadge(
                letter = "G",
                badgeBackground = googleBadgeBackground,
                badgeContentColor = googleBadgeContent,
                label = stringResource(R.string.search_google),
                onClick = { onSearch(WebEngine.GOOGLE) },
                modifier = Modifier.testTag("swipe-action-google-$rowKey"),
                caption = if (labels) "Google" else null,
            )
        }
    }
}
